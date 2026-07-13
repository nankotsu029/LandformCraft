package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.ActorIdentity;
import com.github.nankotsu029.landformcraft.model.PlacementJournal;
import com.github.nankotsu029.landformcraft.model.PlacementState;
import com.github.nankotsu029.landformcraft.model.SnapshotCleanupEntry;
import com.github.nankotsu029.landformcraft.model.SnapshotCleanupFile;
import com.github.nankotsu029.landformcraft.model.SnapshotCleanupPlan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Explicit plan/execute retention cleanup. Startup never runs this scan or deletion path. */
public final class SnapshotCleanupService {
    private static final Duration PLAN_TTL = Duration.ofMinutes(10);

    private final Path snapshotsRoot;
    private final Path plansRoot;
    private final PlacementJournalRepository journals;
    private final GenerationExecutors executors;
    private final Clock clock;
    private final int retentionDays;
    private final LandformDataCodec codec = new LandformDataCodec();

    public SnapshotCleanupService(
            Path snapshotsRoot,
            Path plansRoot,
            PlacementJournalRepository journals,
            GenerationExecutors executors,
            Clock clock,
            int retentionDays
    ) {
        this.snapshotsRoot = snapshotsRoot.toAbsolutePath().normalize();
        this.plansRoot = plansRoot.toAbsolutePath().normalize();
        this.journals = Objects.requireNonNull(journals, "journals");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (retentionDays < 1 || retentionDays > 36_500) {
            throw new IllegalArgumentException("retentionDays must be between 1 and 36500");
        }
        this.retentionDays = retentionDays;
    }

    public CompletableFuture<PreparedCleanup> plan(ActorIdentity actor) {
        Objects.requireNonNull(actor, "actor");
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofDays(retentionDays));
        return journals.findAll().thenCompose(values -> executors.supplyIo(() -> {
            try {
                List<SnapshotCleanupEntry> entries = new ArrayList<>();
                for (PlacementJournal journal : values) {
                    if (!eligible(journal.state()) || !journal.updatedAt().isBefore(cutoff)) {
                        continue;
                    }
                    List<SnapshotCleanupFile> files = inspectJournalSnapshots(journal);
                    if (!files.isEmpty()) {
                        entries.add(new SnapshotCleanupEntry(
                                journal.plan().placementId(), journal.state(), journal.updatedAt(), files));
                    }
                }
                entries.sort(Comparator.comparing(value -> value.placementId().toString()));
                UUID planId = UUID.randomUUID();
                String token = UUID.randomUUID().toString();
                Instant expiresAt = now.plus(PLAN_TTL);
                String hash = confirmationHash(planId, actor, now, expiresAt, entries, token);
                SnapshotCleanupPlan plan = new SnapshotCleanupPlan(
                        1, planId, actor, now, expiresAt, retentionDays, entries, hash, false);
                writeStrict(path(planId), plan);
                return new PreparedCleanup(plan, token);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }));
    }

    public CompletableFuture<SnapshotCleanupPlan> execute(
            UUID planId, String confirmationToken, ActorIdentity actor
    ) {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(confirmationToken, "confirmationToken");
        Objects.requireNonNull(actor, "actor");
        CompletableFuture<SnapshotCleanupPlan> loaded = executors.supplyIo(() -> read(planId));
        return loaded.thenCompose(plan -> {
            validateConfirmation(plan, confirmationToken, actor);
            return journals.findAll().thenCompose(values -> executors.supplyIo(() -> {
                try {
                    Map<UUID, PlacementJournal> byId = values.stream().collect(Collectors.toUnmodifiableMap(
                            value -> value.plan().placementId(), Function.identity()));
                    for (SnapshotCleanupEntry entry : plan.entries()) {
                        PlacementJournal journal = byId.get(entry.placementId());
                        if (journal == null || journal.state() != entry.placementState()
                                || !journal.updatedAt().equals(entry.journalUpdatedAt())
                                || !eligible(journal.state())) {
                            throw new LandformException(
                                    LandformErrorCode.RECOVERY_REQUIRED,
                                    "A placement changed after the cleanup plan was created.", "cleanup-execute",
                                    entry.placementId().toString(), "cleanup-recheck",
                                    "Discard this cleanup plan and create a new dry-run plan."
                            );
                        }
                        for (SnapshotCleanupFile file : entry.files()) {
                            Path target = resolveSnapshot(file.relativePath());
                            verifyIdentity(target, file);
                        }
                    }
                    for (SnapshotCleanupEntry entry : plan.entries()) {
                        for (SnapshotCleanupFile file : entry.files()) {
                            Files.delete(resolveSnapshot(file.relativePath()));
                        }
                        Path placementDirectory = snapshotsRoot.resolve(entry.placementId().toString()).normalize();
                        if (placementDirectory.startsWith(snapshotsRoot) && Files.isDirectory(placementDirectory)
                                && !Files.isSymbolicLink(placementDirectory)) {
                            try (var remaining = Files.list(placementDirectory)) {
                                if (remaining.findAny().isEmpty()) {
                                    Files.delete(placementDirectory);
                                }
                            }
                        }
                    }
                    SnapshotCleanupPlan executed = new SnapshotCleanupPlan(
                            plan.schemaVersion(), plan.planId(), plan.actor(), plan.createdAt(), plan.expiresAt(),
                            plan.retentionDays(), plan.entries(), plan.confirmationHash(), true);
                    writeStrict(path(planId), executed);
                    appendAudit(executed);
                    return executed;
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }));
        });
    }

    public CompletableFuture<List<SnapshotCleanupPlan>> status() {
        return executors.supplyIo(() -> {
            try {
                if (!Files.isDirectory(plansRoot)) {
                    return List.of();
                }
                try (var paths = Files.list(plansRoot)) {
                List<SnapshotCleanupPlan> plans = new ArrayList<>();
                for (Path file : paths.filter(value -> value.getFileName().toString().endsWith(".json"))
                        .sorted().toList()) {
                    plans.add(codec.readSnapshotCleanupPlan(file));
                }
                return List.copyOf(plans);
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private List<SnapshotCleanupFile> inspectJournalSnapshots(PlacementJournal journal) throws IOException {
        List<SnapshotCleanupFile> files = new ArrayList<>();
        for (var checkpoint : journal.tiles()) {
            if (checkpoint.snapshotFile().isEmpty()) {
                continue;
            }
            Path path = resolveSnapshot(checkpoint.snapshotFile());
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
                throw unsafe(journal.plan().placementId(), "Snapshot is missing or is a symbolic link.");
            }
            String checksum = Sha256.file(path);
            if (!checksum.equals(checkpoint.snapshotChecksum())) {
                throw unsafe(journal.plan().placementId(), "Snapshot checksum does not match its journal.");
            }
            files.add(new SnapshotCleanupFile(
                    snapshotsRoot.relativize(path).toString().replace('\\', '/'), checksum, Files.size(path)));
        }
        return List.copyOf(files);
    }

    private void verifyIdentity(Path path, SnapshotCleanupFile expected) throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)
                || Files.size(path) != expected.bytes() || !Sha256.file(path).equals(expected.checksum())) {
            throw new LandformException(
                    LandformErrorCode.RECOVERY_REQUIRED,
                    "A snapshot changed after cleanup planning.", "cleanup-execute", "", "identity-recheck",
                    "Do not delete it; create a new cleanup plan after investigation."
            );
        }
    }

    private Path resolveSnapshot(String relative) {
        Path value = Path.of(relative);
        if (value.isAbsolute() || relative.contains("\\")) {
            throw new LandformException(LandformErrorCode.PATH_UNSAFE, "Unsafe snapshot path.",
                    "cleanup", "", "path-validation", "Inspect the placement journal.");
        }
        Path resolved = snapshotsRoot.resolve(value).normalize();
        if (!resolved.startsWith(snapshotsRoot)) {
            throw new LandformException(LandformErrorCode.PATH_UNSAFE, "Snapshot path escapes its root.",
                    "cleanup", "", "path-validation", "Inspect the placement journal.");
        }
        return resolved;
    }

    private void validateConfirmation(SnapshotCleanupPlan plan, String token, ActorIdentity actor) {
        if (plan.executed() || !plan.actor().equals(actor)) {
            throw new LandformException(LandformErrorCode.CONFIRM_ACTOR_MISMATCH,
                    "Cleanup confirmation belongs to another operator or was already used.",
                    "cleanup-execute", plan.planId().toString(), "confirmation",
                    "Create a new cleanup plan with this operator.");
        }
        String actual = confirmationHash(
                plan.planId(), actor, plan.createdAt(), plan.expiresAt(), plan.entries(), token);
        if (!MessageDigest.isEqual(plan.confirmationHash().getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)) || !clock.instant().isBefore(plan.expiresAt())) {
            throw new LandformException(LandformErrorCode.CONFIRM_INVALID,
                    "Cleanup confirmation is invalid or expired.", "cleanup-execute",
                    plan.planId().toString(), "confirmation", "Create a new cleanup plan.");
        }
    }

    private SnapshotCleanupPlan read(UUID planId) {
        try {
            Path file = path(planId);
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new LandformException(LandformErrorCode.NOT_FOUND, "Cleanup plan was not found.",
                        "cleanup-execute", planId.toString(), "load-plan", "Run cleanup plan first.");
            }
            return codec.readSnapshotCleanupPlan(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Path path(UUID id) {
        return plansRoot.resolve(id + ".json").normalize();
    }

    private void writeStrict(Path target, SnapshotCleanupPlan plan) {
        try {
            Files.createDirectories(plansRoot);
            if (Files.isSymbolicLink(plansRoot)) {
                throw new IOException("cleanup plan root must not be a symbolic link");
            }
            byte[] bytes = codec.writeJsonString(plan).getBytes(StandardCharsets.UTF_8);
            Path temporary = Files.createTempFile(plansRoot, ".cleanup-", ".tmp");
            try {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    throw new IOException("atomic move is required for cleanup plans", exception);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            codec.readSnapshotCleanupPlan(target);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void appendAudit(SnapshotCleanupPlan plan) throws IOException {
        Path audit = plansRoot.resolve("cleanup-audit.jsonl");
        if (Files.isSymbolicLink(audit)) {
            throw new IOException("cleanup audit must not be a symbolic link");
        }
        String line = "{\"timestamp\":\"" + clock.instant() + "\",\"planId\":\"" + plan.planId()
                + "\",\"actor\":\"" + plan.actor().canonical() + "\",\"placements\":"
                + plan.entries().size() + ",\"bytes\":" + plan.totalBytes() + "}\n";
        try (FileChannel channel = FileChannel.open(audit, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode(line);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static boolean eligible(PlacementState state) {
        return state == PlacementState.APPLIED || state == PlacementState.UNDONE
                || state == PlacementState.ROLLED_BACK;
    }

    private static LandformException unsafe(UUID placementId, String message) {
        return new LandformException(LandformErrorCode.RECOVERY_REQUIRED, message, "cleanup-plan",
                placementId.toString(), "snapshot-validation",
                "Preserve the snapshot and run recovery diagnosis; it will not be deleted.");
    }

    private static String confirmationHash(
            UUID planId, ActorIdentity actor, Instant createdAt, Instant expiresAt,
            List<SnapshotCleanupEntry> entries, String nonce
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String header = planId + "\n" + actor.canonical() + "\n" + createdAt + "\n" + expiresAt + "\n";
            digest.update(header.getBytes(StandardCharsets.UTF_8));
            for (SnapshotCleanupEntry entry : entries) {
                digest.update((entry.placementId() + ":" + entry.placementState() + ":"
                        + entry.journalUpdatedAt() + "\n").getBytes(StandardCharsets.UTF_8));
                for (SnapshotCleanupFile file : entry.files()) {
                    digest.update((file.relativePath() + ":" + file.checksum() + ":" + file.bytes() + "\n")
                            .getBytes(StandardCharsets.UTF_8));
                }
            }
            digest.update(nonce.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
