package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.ReleaseVerification;
import com.github.nankotsu029.landformcraft.format.ReleaseVerifier;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.ConfirmationAction;
import com.github.nankotsu029.landformcraft.model.ActorIdentity;
import com.github.nankotsu029.landformcraft.model.ExportManifest;
import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.github.nankotsu029.landformcraft.model.PlacementJournal;
import com.github.nankotsu029.landformcraft.model.PlacementPlan;
import com.github.nankotsu029.landformcraft.model.PlacementState;
import com.github.nankotsu029.landformcraft.model.PlacementTileCheckpoint;
import com.github.nankotsu029.landformcraft.model.PlacementTileState;
import com.github.nankotsu029.landformcraft.model.WorldDescriptor;
import com.github.nankotsu029.landformcraft.model.PlacementOperation;
import com.github.nankotsu029.landformcraft.model.ReservationState;
import com.github.nankotsu029.landformcraft.model.RecoveryClassification;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.StandardOpenOption;

/** Confirm-gated, checkpointed, tile-by-tile placement and undo orchestration. */
public final class PlacementApplicationService {
    public static final Duration CONFIRMATION_TTL = Duration.ofMinutes(10);

    private final Path releasesRoot;
    private final Path snapshotsRoot;
    private final GenerationExecutors executors;
    private final PlacementJournalRepository repository;
    private final PlacementWorldGateway worldGateway;
    private final Clock clock;
    private final DiskBudgetPolicy diskPolicy;
    private final PlacementDiskEstimator diskEstimator = new PlacementDiskEstimator();
    private final FilePlacementSafetyStore safetyStore;
    private final ReleaseVerifier releaseVerifier = new ReleaseVerifier();
    private final ConcurrentHashMap<UUID, Boolean> active = new ConcurrentHashMap<>();
    private final AtomicBoolean acceptingMutations = new AtomicBoolean(true);

    public PlacementApplicationService(
            Path releasesRoot,
            Path snapshotsRoot,
            GenerationExecutors executors,
            PlacementJournalRepository repository,
            PlacementWorldGateway worldGateway,
            Clock clock
    ) {
        this(releasesRoot, snapshotsRoot, executors, repository, worldGateway, clock,
                DiskBudgetPolicy.defaults());
    }

    public PlacementApplicationService(
            Path releasesRoot,
            Path snapshotsRoot,
            GenerationExecutors executors,
            PlacementJournalRepository repository,
            PlacementWorldGateway worldGateway,
            Clock clock,
            DiskBudgetPolicy diskPolicy
    ) {
        this.releasesRoot = releasesRoot.toAbsolutePath().normalize();
        this.snapshotsRoot = snapshotsRoot.toAbsolutePath().normalize();
        this.executors = Objects.requireNonNull(executors, "executors");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.worldGateway = Objects.requireNonNull(worldGateway, "worldGateway");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diskPolicy = Objects.requireNonNull(diskPolicy, "diskPolicy");
        Path safetyFile = Objects.requireNonNull(this.snapshotsRoot.getParent(),
                "snapshotsRoot must have a parent").resolve("placement-safety-state.json");
        this.safetyStore = new FilePlacementSafetyStore(safetyFile, this.snapshotsRoot, this.clock);
    }

    public CompletableFuture<PreparedPlacement> plan(
            String releaseDirectory,
            String worldName,
            int targetX,
            int targetY,
            int targetZ
    ) {
        return plan(releaseDirectory, worldName, targetX, targetY, targetZ, ActorIdentity.system("LEGACY"));
    }

    public CompletableFuture<PreparedPlacement> plan(
            String releaseDirectory,
            String worldName,
            int targetX,
            int targetY,
            int targetZ,
            ActorIdentity actor
    ) {
        requireAcceptingMutations();
        Objects.requireNonNull(actor, "actor");
        Path release = resolveRelease(releaseDirectory);
        CompletableFuture<VerifiedRelease> verified = executors.supplyIo(() -> verifyReleaseWithChecksum(release));
        return verified.thenCompose(verification -> worldGateway.describeWorld(worldName).toCompletableFuture()
                .thenCompose(world -> createPlan(
                        releaseDirectory, verification, world, targetX, targetY, targetZ, actor
                )));
    }

    public CompletableFuture<PlacementJournal> execute(UUID placementId, String confirmationToken) {
        return execute(placementId, confirmationToken, ActorIdentity.system("LEGACY"));
    }

    public CompletableFuture<PlacementJournal> execute(
            UUID placementId, String confirmationToken, ActorIdentity actor
    ) {
        return nonCancellable(exclusive(placementId, () -> required(placementId).thenCompose(journal -> {
            validateConfirmation(journal, ConfirmationAction.APPLY, confirmationToken, actor);
            if (journal.state() != PlacementState.PLANNED) {
                return CompletableFuture.failedFuture(new IllegalStateException("placement is not PLANNED"));
            }
            Path release = resolveRelease(journal.plan().releaseDirectory());
            return executors.runIo(() -> activateReservation(
                    journal, PlacementOperation.APPLY, actor)).thenCompose(ignored ->
                    executors.supplyIo(() -> verifyReleaseWithChecksum(release))).thenCompose(verification ->
                    worldGateway.describeWorld(journal.plan().worldName()).toCompletableFuture()
                            .thenCompose(world -> executeVerified(journal, verification, world, release))
            );
        })));
    }

    public CompletableFuture<PreparedPlacement> prepareUndo(UUID placementId) {
        return prepareUndo(placementId, ActorIdentity.system("LEGACY"));
    }

    public CompletableFuture<PreparedPlacement> prepareUndo(UUID placementId, ActorIdentity actor) {
        return exclusive(placementId, () -> required(placementId).thenCompose(journal -> {
            if (journal.state() != PlacementState.APPLIED) {
                return CompletableFuture.failedFuture(new IllegalStateException("only APPLIED placements can be undone"));
            }
            String token = UUID.randomUUID().toString();
            Instant now = clock.instant();
            Instant expiresAt = now.plus(CONFIRMATION_TTL);
            PlacementJournal prepared = replace(
                    journal, journal.state(), ConfirmationAction.UNDO, actor,
                    confirmationHash(journal.plan(), ConfirmationAction.UNDO, actor, now, expiresAt, token),
                    now, expiresAt, journal.tiles(), journal.reservedBytes(), journal.snapshotBytesUsed(),
                    now, "Undo confirmation prepared"
            );
            return executors.runIo(() -> reserveFor(journal.plan(), PlacementOperation.UNDO, actor,
                            ReservationState.PLANNED, expiresAt, 16L * 1024L * 1024L))
                    .thenCompose(ignored -> repository.save(prepared))
                    .thenApply(saved -> new PreparedPlacement(saved, token));
        }));
    }

    public CompletableFuture<PlacementJournal> undo(UUID placementId, String confirmationToken) {
        return undo(placementId, confirmationToken, ActorIdentity.system("LEGACY"));
    }

    public CompletableFuture<PlacementJournal> undo(
            UUID placementId, String confirmationToken, ActorIdentity actor
    ) {
        return nonCancellable(exclusive(placementId, () -> required(placementId).thenCompose(journal -> {
            validateConfirmation(journal, ConfirmationAction.UNDO, confirmationToken, actor);
            if (journal.state() != PlacementState.APPLIED) {
                return CompletableFuture.failedFuture(new IllegalStateException("placement is not APPLIED"));
            }
            Path release = resolveRelease(journal.plan().releaseDirectory());
            return executors.runIo(() -> activateReservation(
                    journal, PlacementOperation.UNDO, actor)).thenCompose(ignored ->
                    executors.supplyIo(() -> verifyReleaseWithChecksum(release))).thenCompose(verification ->
                    worldGateway.describeWorld(journal.plan().worldName()).toCompletableFuture().thenCompose(world -> {
                validateWorldIdentity(journal.plan(), world);
                ExportManifest manifest = verification.verification().manifest();
                return verifyAppliedTiles(journal.plan(), manifest, release, 0).thenCompose(ignored -> {
                    AtomicReference<PlacementJournal> current = new AtomicReference<>(replace(
                            journal, PlacementState.UNDOING, ConfirmationAction.NONE, journal.plan().actor(),
                            "", Instant.EPOCH, Instant.EPOCH, journal.tiles(), journal.reservedBytes(),
                            journal.snapshotBytesUsed(), clock.instant(), "Undo started"
                    ));
                    CompletableFuture<PlacementJournal> result = save(current.get(), current).thenCompose(saved ->
                            restoreReverse(current, manifest, manifest.tiles().size() - 1)
                    ).thenCompose(saved -> save(replace(
                            current.get(), PlacementState.UNDONE, ConfirmationAction.NONE,
                            current.get().plan().actor(), "", Instant.EPOCH, Instant.EPOCH,
                            current.get().tiles(), current.get().reservedBytes(), current.get().snapshotBytesUsed(),
                            clock.instant(), "Undo completed"
                    ), current)).thenCompose(saved -> releaseSafety(saved.plan().placementId()).thenApply(ignored2 -> saved));
                    return result.handle((value, failure) -> {
                        if (failure == null) {
                            return CompletableFuture.completedFuture(value);
                        }
                        Throwable actual = unwrap(failure);
                        PlacementJournal recovery = replace(
                                current.get(), PlacementState.RECOVERY_REQUIRED,
                                ConfirmationAction.NONE, current.get().plan().actor(), "", Instant.EPOCH, Instant.EPOCH,
                                current.get().tiles(), current.get().reservedBytes(), current.get().snapshotBytesUsed(), clock.instant(),
                                "Undo failed; snapshot recovery required: " + safeMessage(actual)
                        );
                        return save(recovery, current).thenCompose(saved ->
                                CompletableFuture.<PlacementJournal>failedFuture(actual));
                    }).thenCompose(future -> future);
                });
            }));
        })));
    }

    public CompletableFuture<PreparedRecovery> diagnoseRecovery(UUID placementId, ActorIdentity actor) {
        return exclusive(placementId, () -> required(placementId).thenCompose(journal -> {
            if (journal.state() != PlacementState.RECOVERY_REQUIRED) {
                return CompletableFuture.failedFuture(new LandformException(
                        LandformErrorCode.RECOVERY_REQUIRED,
                        "Only RECOVERY_REQUIRED placements can be diagnosed.", "recovery-diagnose",
                        placementId.toString(), "recovery-precondition",
                        "Use apply status first; do not run recovery on a terminal placement."));
            }
            return analyzeRecovery(journal).thenCompose(report -> {
                ConfirmationAction action = switch (report.classification()) {
                    case SAFE_TO_ACCEPT -> ConfirmationAction.RECOVERY_ACCEPT;
                    case SAFE_TO_ROLLBACK, SAFE_TO_RESUME -> ConfirmationAction.RECOVERY_ROLLBACK;
                    case MANUAL_INTERVENTION_REQUIRED, CORRUPTED -> ConfirmationAction.NONE;
                };
                if (action == ConfirmationAction.NONE) {
                    return CompletableFuture.completedFuture(new PreparedRecovery(report, action, ""));
                }
                String token = UUID.randomUUID().toString();
                Instant now = clock.instant();
                Instant expiresAt = now.plus(CONFIRMATION_TTL);
                PlacementOperation operation = action == ConfirmationAction.RECOVERY_ACCEPT
                        ? PlacementOperation.RECOVERY_ACCEPT : PlacementOperation.RECOVERY_ROLLBACK;
                return executors.runIo(() -> reserveFor(
                                journal.plan(), operation, actor, ReservationState.RECOVERY_REQUIRED,
                                expiresAt, Math.max(16L * 1024L * 1024L, journal.snapshotBytesUsed())
                        )).thenCompose(ignored -> repository.save(replace(
                                journal, PlacementState.RECOVERY_REQUIRED, action, actor,
                                confirmationHash(journal.plan(), action, actor, now, expiresAt, token),
                                now, expiresAt, journal.tiles(), journal.reservedBytes(),
                                journal.snapshotBytesUsed(), now,
                                "Recovery diagnosis: " + report.classification()
                        ))).thenApply(saved -> new PreparedRecovery(report, action, token));
            });
        }));
    }

    public CompletableFuture<PlacementJournal> recoverAccept(
            UUID placementId, String confirmationToken, ActorIdentity actor
    ) {
        return nonCancellable(exclusive(placementId, () -> required(placementId).thenCompose(journal -> {
            validateConfirmation(journal, ConfirmationAction.RECOVERY_ACCEPT, confirmationToken, actor);
            return analyzeRecovery(journal).thenCompose(report -> {
                if (report.classification() != RecoveryClassification.SAFE_TO_ACCEPT) {
                    return CompletableFuture.failedFuture(new LandformException(
                            LandformErrorCode.RECOVERY_REQUIRED,
                            "The world cannot be proven identical to the release.", "recovery-accept",
                            placementId.toString(), "recovery-diagnosis",
                            "Do not accept; follow the diagnosis and use rollback or manual intervention."
                    ));
                }
                PlacementJournal accepted = replace(
                        journal, PlacementState.APPLIED, ConfirmationAction.NONE, journal.plan().actor(),
                        "", Instant.EPOCH, Instant.EPOCH, journal.tiles(), journal.reservedBytes(),
                        journal.snapshotBytesUsed(), clock.instant(), "Recovery accepted after full world verification"
                );
                return repository.save(accepted)
                        .thenCompose(saved -> appendRecoveryAudit(saved, actor, "ACCEPT", report)
                                .thenCompose(ignored -> releaseSafety(placementId)).thenApply(ignored -> saved));
            });
        })));
    }

    public CompletableFuture<PlacementJournal> recoverRollback(
            UUID placementId, String confirmationToken, ActorIdentity actor
    ) {
        return nonCancellable(exclusive(placementId, () -> required(placementId).thenCompose(journal -> {
            validateConfirmation(journal, ConfirmationAction.RECOVERY_ROLLBACK, confirmationToken, actor);
            return analyzeRecovery(journal).thenCompose(report -> {
                if (report.classification() == RecoveryClassification.CORRUPTED
                        || report.classification() == RecoveryClassification.MANUAL_INTERVENTION_REQUIRED) {
                    return CompletableFuture.failedFuture(new LandformException(
                            LandformErrorCode.RECOVERY_REQUIRED,
                            "Snapshot rollback cannot be proven safe.", "recovery-rollback",
                            placementId.toString(), "recovery-diagnosis",
                            "Preserve the journal and snapshots and perform manual recovery from backup."
                    ));
                }
                Path release = resolveRelease(journal.plan().releaseDirectory());
                return executors.supplyIo(() -> verifyReleaseWithChecksum(release)).thenCompose(verification -> {
                    AtomicReference<PlacementJournal> current = new AtomicReference<>(replace(
                            journal, PlacementState.ROLLING_BACK, ConfirmationAction.NONE, journal.plan().actor(),
                            "", Instant.EPOCH, Instant.EPOCH, journal.tiles(), journal.reservedBytes(),
                            journal.snapshotBytesUsed(), clock.instant(), "Recovery rollback started"
                    ));
                    return save(current.get(), current)
                            .thenCompose(ignored -> restoreReverse(
                                    current, verification.verification().manifest(),
                                    verification.verification().manifest().tiles().size() - 1))
                            .thenCompose(ignored -> save(replace(
                                    current.get(), PlacementState.ROLLED_BACK, ConfirmationAction.NONE,
                                    current.get().plan().actor(), "", Instant.EPOCH, Instant.EPOCH,
                                    current.get().tiles(), current.get().reservedBytes(),
                                    current.get().snapshotBytesUsed(), clock.instant(), "Recovery rollback completed"
                            ), current))
                            .thenCompose(saved -> appendRecoveryAudit(saved, actor, "ROLLBACK", report)
                                    .thenCompose(ignored -> releaseSafety(placementId)).thenApply(ignored -> saved))
                            .exceptionallyCompose(failure -> {
                                PlacementJournal recovery = replace(
                                        current.get(), PlacementState.RECOVERY_REQUIRED, ConfirmationAction.NONE,
                                        current.get().plan().actor(), "", Instant.EPOCH, Instant.EPOCH,
                                        current.get().tiles(), current.get().reservedBytes(),
                                        current.get().snapshotBytesUsed(), clock.instant(),
                                        "Recovery rollback failed; manual intervention required"
                                );
                                return save(recovery, current).thenCompose(ignored ->
                                        CompletableFuture.failedFuture(unwrap(failure)));
                            });
                });
            });
        })));
    }

    /** Stops new placement mutations; in-flight world edits remain journaled and cannot be caller-cancelled. */
    public void stopAcceptingMutations() {
        acceptingMutations.set(false);
    }

    private CompletableFuture<Void> verifyAppliedTiles(
            PlacementPlan plan,
            ExportManifest manifest,
            Path release,
            int index
    ) {
        if (index >= manifest.tiles().size()) {
            return CompletableFuture.completedFuture(null);
        }
        ManifestTile tile = manifest.tiles().get(index);
        Path schematic = release.resolve(tile.file()).normalize();
        if (!schematic.startsWith(release)) {
            return CompletableFuture.failedFuture(new IllegalStateException("schematic path escaped release root"));
        }
        return worldGateway.verify(plan, tile, schematic).toCompletableFuture().thenCompose(matches -> {
            if (!matches) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "world drift detected before undo at " + tile.id()
                ));
            }
            return verifyAppliedTiles(plan, manifest, release, index + 1);
        });
    }

    public CompletableFuture<PlacementJournal> status(UUID placementId) {
        return required(placementId);
    }

    /** Supplies known placement IDs for non-blocking Paper tab completion. */
    public CompletableFuture<List<UUID>> placementIds() {
        return repository.findAll().thenApply(journals -> journals.stream()
                .map(journal -> journal.plan().placementId())
                .sorted()
                .toList());
    }

    /** Scans verified-release directory shapes on the I/O executor for tab-completion caching. */
    public CompletableFuture<List<String>> releaseDirectories() {
        return executors.supplyIo(() -> {
            if (!Files.isDirectory(releasesRoot)) {
                return List.of();
            }
            try (var paths = Files.walk(releasesRoot, 2)) {
                return paths.filter(path -> !path.equals(releasesRoot))
                        .filter(Files::isDirectory)
                        .filter(path -> Files.isRegularFile(path.resolve("manifest.json")))
                        .filter(path -> Files.isRegularFile(path.resolve("checksums.sha256")))
                        .map(releasesRoot::relativize)
                        .map(path -> path.toString().replace('\\', '/'))
                        .sorted()
                        .toList();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /** Marks interrupted mutations as recovery-required without guessing whether a world edit completed. */
    public CompletableFuture<List<PlacementJournal>> recoverInterrupted() {
        return repository.findAll().thenCompose(journals -> {
            List<CompletableFuture<PlacementJournal>> writes = new ArrayList<>();
            for (PlacementJournal journal : journals) {
                if (journal.state() == PlacementState.APPLYING
                        || journal.state() == PlacementState.ROLLING_BACK
                        || journal.state() == PlacementState.UNDOING) {
                    writes.add(repository.save(replace(
                            journal, PlacementState.RECOVERY_REQUIRED, ConfirmationAction.NONE,
                            journal.plan().actor(), "", Instant.EPOCH, Instant.EPOCH,
                            journal.tiles(), journal.reservedBytes(), journal.snapshotBytesUsed(), clock.instant(),
                            "Server stopped during a world mutation; inspect snapshots before recovery"
                    )));
                }
            }
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
                    .thenCompose(ignored -> repository.findAll())
                    .thenCompose(current -> executors.runIo(() -> {
                        try {
                            safetyStore.rebuild(current);
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    }).thenApply(ignored -> writes.stream().map(write -> write.getNow(null)).toList()));
        });
    }

    private CompletableFuture<PreparedPlacement> createPlan(
            String relativeRelease,
            VerifiedRelease verification,
            WorldDescriptor world,
            int targetX,
            int targetY,
            int targetZ,
            ActorIdentity actor
    ) {
        ExportManifest manifest = verification.verification().manifest();
        int maximumX = Math.addExact(targetX, manifest.width() - 1);
        int maximumY = Math.addExact(targetY, manifest.maxY() - manifest.minY());
        int maximumZ = Math.addExact(targetZ, manifest.length() - 1);
        validateBounds(world, targetX, targetY, targetZ, maximumX, maximumY, maximumZ);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String token = UUID.randomUUID().toString();
        PlacementPlan plan = new PlacementPlan(
                1, id, relativeRelease, verification.checksum(), manifest.requestId(),
                world.worldId(), world.worldName(), actor,
                targetX, targetY, targetZ, targetX, targetY, targetZ, maximumX, maximumY, maximumZ, now
        );
        List<PlacementTileCheckpoint> tiles = manifest.tiles().stream()
                .map(tile -> PlacementTileCheckpoint.pending(tile.id()))
                .toList();
        Instant expiresAt = now.plus(CONFIRMATION_TTL);
        Path release = resolveRelease(relativeRelease);
        return executors.supplyIo(() -> {
            try {
                PlacementDiskEstimate estimate = diskEstimator.estimate(release, manifest, diskPolicy);
                reserveFor(plan, PlacementOperation.APPLY, actor, ReservationState.PLANNED,
                        expiresAt, estimate.totalBytes());
                return estimate;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }).thenCompose(estimate -> {
            PlacementJournal journal = new PlacementJournal(
                    1, plan, PlacementState.PLANNED, ConfirmationAction.APPLY, actor,
                    confirmationHash(plan, ConfirmationAction.APPLY, actor, now, expiresAt, token),
                    now, expiresAt, tiles, estimate.totalBytes(), 0L, now,
                    "Dry-run complete; no world blocks have been changed; disk bytes reserved="
                            + estimate.totalBytes()
            );
            return repository.save(journal)
                    .thenApply(saved -> new PreparedPlacement(saved, token))
                    .exceptionallyCompose(failure -> releaseSafety(id).thenCompose(ignored ->
                            CompletableFuture.failedFuture(unwrap(failure))));
        });
    }

    private CompletableFuture<PlacementJournal> executeVerified(
            PlacementJournal journal,
            VerifiedRelease verification,
            WorldDescriptor world,
            Path release
    ) {
        validateWorldIdentity(journal.plan(), world);
        if (!verification.checksum().equals(journal.plan().releaseChecksum())) {
            return CompletableFuture.failedFuture(new IllegalStateException("release changed after placement planning"));
        }
        AtomicReference<PlacementJournal> current = new AtomicReference<>(replace(
                journal, PlacementState.APPLYING, ConfirmationAction.NONE, journal.plan().actor(),
                "", Instant.EPOCH, Instant.EPOCH, journal.tiles(), journal.reservedBytes(),
                journal.snapshotBytesUsed(), clock.instant(), "Tile placement started"
        ));
        CompletableFuture<PlacementJournal> result = save(current.get(), current)
                .thenCompose(ignored -> applyTile(current, verification.verification().manifest(), release, 0))
                .thenCompose(ignored -> save(replace(
                        current.get(), PlacementState.APPLIED, ConfirmationAction.NONE,
                        current.get().plan().actor(), "", Instant.EPOCH, Instant.EPOCH,
                        current.get().tiles(), current.get().reservedBytes(), current.get().snapshotBytesUsed(),
                        clock.instant(), "All tiles applied and verified"
                ), current));
        return result.handle((value, failure) -> {
            if (failure == null) {
                return releaseSafety(value.plan().placementId()).thenApply(ignored -> value);
            }
            return rollback(current, verification.verification().manifest(), unwrap(failure));
        }).thenCompose(future -> future);
    }

    private CompletableFuture<PlacementJournal> applyTile(
            AtomicReference<PlacementJournal> current,
            ExportManifest manifest,
            Path release,
            int index
    ) {
        if (index >= manifest.tiles().size()) {
            return CompletableFuture.completedFuture(current.get());
        }
        ManifestTile tile = manifest.tiles().get(index);
        Path snapshot = snapshotsRoot.resolve(current.get().plan().placementId().toString())
                .resolve(tile.id() + ".schem").normalize();
        if (!snapshot.startsWith(snapshotsRoot)) {
            return CompletableFuture.failedFuture(new IllegalStateException("snapshot path escaped root"));
        }
        Path schematic = release.resolve(tile.file()).normalize();
        return worldGateway.snapshot(current.get().plan(), tile, snapshot).toCompletableFuture()
                .thenCompose(artifact -> executors.supplyIo(() -> {
                    try {
                        return Files.size(artifact.file());
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                }).thenCompose(bytes -> save(updateTile(
                            current.get(), index, PlacementTileState.SNAPSHOTTED,
                            snapshotsRoot.relativize(artifact.file().toAbsolutePath().normalize())
                                    .toString().replace('\\', '/'),
                            artifact.checksum(), bytes, "Snapshot saved for " + tile.id()
                    ), current)))
                .thenCompose(ignored -> worldGateway.apply(current.get().plan(), tile, schematic).toCompletableFuture())
                .thenCompose(ignored -> save(updateTile(
                        current.get(), index, PlacementTileState.APPLIED,
                        current.get().tiles().get(index).snapshotFile(),
                        current.get().tiles().get(index).snapshotChecksum(), 0L, "Applied " + tile.id()
                ), current))
                .thenCompose(ignored -> worldGateway.verify(
                        current.get().plan(), tile, schematic
                ).toCompletableFuture())
                .thenCompose(matches -> matches
                        ? save(updateTile(
                                current.get(), index, PlacementTileState.VERIFIED,
                                current.get().tiles().get(index).snapshotFile(),
                                current.get().tiles().get(index).snapshotChecksum(), 0L, "Verified " + tile.id()
                        ), current)
                        : CompletableFuture.failedFuture(new IllegalStateException(
                                "world verification failed for " + tile.id()
                        )))
                .thenCompose(ignored -> applyTile(current, manifest, release, index + 1));
    }

    private CompletableFuture<PlacementJournal> rollback(
            AtomicReference<PlacementJournal> current,
            ExportManifest manifest,
            Throwable originalFailure
    ) {
        PlacementJournal rollingBack = replace(
                current.get(), PlacementState.ROLLING_BACK, ConfirmationAction.NONE,
                current.get().plan().actor(), "", Instant.EPOCH, Instant.EPOCH,
                current.get().tiles(), current.get().reservedBytes(), current.get().snapshotBytesUsed(),
                clock.instant(), "Placement failed; rollback started"
        );
        return save(rollingBack, current)
                .thenCompose(ignored -> restoreReverse(current, manifest, manifest.tiles().size() - 1))
                .thenCompose(ignored -> save(replace(
                        current.get(), PlacementState.ROLLED_BACK, ConfirmationAction.NONE,
                        current.get().plan().actor(), "", Instant.EPOCH, Instant.EPOCH,
                        current.get().tiles(), current.get().reservedBytes(), current.get().snapshotBytesUsed(),
                        clock.instant(), "Rollback completed: " + safeMessage(originalFailure)
                ), current))
                .thenCompose(ignored -> releaseSafety(current.get().plan().placementId()))
                .thenCompose(ignored -> CompletableFuture.<PlacementJournal>failedFuture(originalFailure))
                .exceptionallyCompose(rollbackFailure -> {
                    Throwable actual = unwrap(rollbackFailure);
                    if (actual == originalFailure) {
                        return CompletableFuture.failedFuture(originalFailure);
                    }
                    originalFailure.addSuppressed(actual);
                    PlacementJournal recovery = replace(
                            current.get(), PlacementState.RECOVERY_REQUIRED, ConfirmationAction.NONE,
                            current.get().plan().actor(), "", Instant.EPOCH, Instant.EPOCH,
                            current.get().tiles(), current.get().reservedBytes(), current.get().snapshotBytesUsed(),
                            clock.instant(), "Rollback failed; manual recovery required"
                    );
                    return save(recovery, current).thenCompose(ignored ->
                            CompletableFuture.failedFuture(originalFailure));
                });
    }

    private CompletableFuture<PlacementJournal> restoreReverse(
            AtomicReference<PlacementJournal> current,
            ExportManifest manifest,
            int index
    ) {
        if (index < 0) {
            return CompletableFuture.completedFuture(current.get());
        }
        PlacementTileCheckpoint checkpoint = current.get().tiles().get(index);
        if (checkpoint.state() == PlacementTileState.PENDING || checkpoint.state() == PlacementTileState.RESTORED) {
            return restoreReverse(current, manifest, index - 1);
        }
        ManifestTile tile = manifest.tiles().get(index);
        Path snapshot = snapshotsRoot.resolve(checkpoint.snapshotFile()).normalize();
        if (!snapshot.startsWith(snapshotsRoot)) {
            return CompletableFuture.failedFuture(new IllegalStateException("snapshot path escaped root"));
        }
        return worldGateway.restore(
                        current.get().plan(), tile, snapshot, checkpoint.snapshotChecksum()
                ).toCompletableFuture()
                .thenCompose(ignored -> save(updateTile(
                        current.get(), index, PlacementTileState.RESTORED,
                        checkpoint.snapshotFile(), checkpoint.snapshotChecksum(), 0L, "Restored " + tile.id()
                ), current))
                .thenCompose(ignored -> restoreReverse(current, manifest, index - 1));
    }

    private CompletableFuture<PlacementJournal> save(
            PlacementJournal journal,
            AtomicReference<PlacementJournal> current
    ) {
        return repository.save(journal).thenApply(saved -> {
            current.set(saved);
            return saved;
        });
    }

    private CompletableFuture<PlacementJournal> required(UUID id) {
        return repository.find(id).thenCompose(found -> found
                .map(CompletableFuture::completedFuture)
                .orElseGet(() -> CompletableFuture.failedFuture(
                        new IllegalArgumentException("unknown placement: " + id)
                )));
    }

    private <T> CompletableFuture<T> exclusive(UUID id, java.util.function.Supplier<CompletableFuture<T>> operation) {
        requireAcceptingMutations();
        if (active.putIfAbsent(id, Boolean.TRUE) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("placement operation is already running"));
        }
        CompletableFuture<T> result;
        try {
            result = operation.get();
        } catch (RuntimeException exception) {
            active.remove(id);
            return CompletableFuture.failedFuture(exception);
        }
        return result.whenComplete((ignored, failure) -> active.remove(id));
    }

    private void requireAcceptingMutations() {
        if (!acceptingMutations.get()) {
            throw new IllegalStateException("placement service is stopping; new mutations are rejected");
        }
    }

    private static <T> CompletableFuture<T> nonCancellable(CompletableFuture<T> source) {
        CompletableFuture<T> protectedFuture = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }
        };
        source.whenComplete((value, failure) -> {
            if (failure == null) {
                protectedFuture.complete(value);
            } else {
                protectedFuture.completeExceptionally(failure);
            }
        });
        return protectedFuture;
    }

    private Path resolveRelease(String relative) {
        Objects.requireNonNull(relative, "relative");
        Path value = Path.of(relative);
        if (value.isAbsolute() || relative.contains("\\")) {
            throw new IllegalArgumentException("release path must be portable and relative");
        }
        Path resolved = releasesRoot.resolve(value).normalize();
        if (!resolved.startsWith(releasesRoot)) {
            throw new IllegalArgumentException("release path escapes exports root");
        }
        return resolved;
    }

    private ReleaseVerification verifyRelease(Path release) {
        try {
            return releaseVerifier.verifyDirectory(release);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private VerifiedRelease verifyReleaseWithChecksum(Path release) {
        ReleaseVerification verification = verifyRelease(release);
        try {
            return new VerifiedRelease(verification, Sha256.file(release.resolve("checksums.sha256")));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private CompletableFuture<RecoveryReport> analyzeRecovery(PlacementJournal journal) {
        Path release = resolveRelease(journal.plan().releaseDirectory());
        CompletableFuture<RecoveryFiles> files = executors.supplyIo(() -> inspectRecoveryFiles(journal, release));
        return files.thenCompose(inspection -> {
            if (inspection.corrupted()) {
                return CompletableFuture.completedFuture(new RecoveryReport(
                        journal.plan().placementId(), RecoveryClassification.CORRUPTED,
                        inspection.findings(), "Preserve all files and restore from a verified backup."
                ));
            }
            return worldGateway.describeWorld(journal.plan().worldName()).toCompletableFuture()
                    .thenCompose(world -> {
                        try {
                            validateWorldIdentity(journal.plan(), world);
                        } catch (RuntimeException exception) {
                            return CompletableFuture.completedFuture(new RecoveryReport(
                                    journal.plan().placementId(), RecoveryClassification.MANUAL_INTERVENTION_REQUIRED,
                                    append(inspection.findings(), "world:identity-mismatch"),
                                    "Do not mutate the world; restore the original world identity or use backup."
                            ));
                        }
                        return inspectRecoveryWorld(
                                journal, inspection.verification(), release, 0, new ArrayList<>()
                        ).thenApply(observations -> classifyRecovery(journal, inspection.findings(), observations));
                    });
        });
    }

    private RecoveryFiles inspectRecoveryFiles(PlacementJournal journal, Path release) {
        List<String> findings = new ArrayList<>();
        final VerifiedRelease verification;
        try {
            verification = verifyReleaseWithChecksum(release);
            if (!verification.checksum().equals(journal.plan().releaseChecksum())) {
                findings.add("release:checksum-mismatch");
                return new RecoveryFiles(null, true, findings);
            }
            for (PlacementTileCheckpoint checkpoint : journal.tiles()) {
                if (checkpoint.snapshotFile().isEmpty()) {
                    continue;
                }
                Path snapshot = snapshotsRoot.resolve(checkpoint.snapshotFile()).normalize();
                if (!snapshot.startsWith(snapshotsRoot) || Files.isSymbolicLink(snapshot)
                        || !Files.isRegularFile(snapshot)
                        || !Sha256.file(snapshot).equals(checkpoint.snapshotChecksum())) {
                    findings.add(checkpoint.tileId() + ":snapshot-corrupted");
                    return new RecoveryFiles(null, true, findings);
                }
            }
            return new RecoveryFiles(verification, false, findings);
        } catch (RuntimeException | IOException exception) {
            findings.add("release-or-snapshot:corrupted");
            return new RecoveryFiles(null, true, findings);
        }
    }

    private CompletableFuture<List<TileObservation>> inspectRecoveryWorld(
            PlacementJournal journal,
            VerifiedRelease verification,
            Path release,
            int index,
            List<TileObservation> observations
    ) {
        ExportManifest manifest = verification.verification().manifest();
        if (index >= manifest.tiles().size()) {
            return CompletableFuture.completedFuture(List.copyOf(observations));
        }
        ManifestTile tile = manifest.tiles().get(index);
        PlacementTileCheckpoint checkpoint = journal.tiles().get(index);
        Path schematic = release.resolve(tile.file()).normalize();
        CompletableFuture<Boolean> releaseMatch = worldGateway.verify(
                journal.plan(), tile, schematic).toCompletableFuture().exceptionally(ignored -> false);
        return releaseMatch.thenCompose(matchesRelease -> {
            if (checkpoint.snapshotFile().isEmpty()) {
                observations.add(new TileObservation(tile.id(), checkpoint.state(), matchesRelease, false, false));
                return inspectRecoveryWorld(journal, verification, release, index + 1, observations);
            }
            Path snapshot = snapshotsRoot.resolve(checkpoint.snapshotFile()).normalize();
            return worldGateway.verifySnapshot(
                    journal.plan(), tile, snapshot, checkpoint.snapshotChecksum()
            ).toCompletableFuture().handle((matches, failure) -> failure == null && Boolean.TRUE.equals(matches))
                    .thenCompose(matchesSnapshot -> {
                        observations.add(new TileObservation(
                                tile.id(), checkpoint.state(), matchesRelease, matchesSnapshot, true));
                        return inspectRecoveryWorld(journal, verification, release, index + 1, observations);
                    });
        });
    }

    private static RecoveryReport classifyRecovery(
            PlacementJournal journal,
            List<String> fileFindings,
            List<TileObservation> observations
    ) {
        List<String> findings = new ArrayList<>(fileFindings);
        observations.forEach(value -> findings.add(value.tileId() + ":state=" + value.state()
                + ",release=" + value.matchesRelease() + ",snapshot=" + value.matchesSnapshot()));
        boolean allRelease = observations.stream().allMatch(TileObservation::matchesRelease);
        if (allRelease) {
            return new RecoveryReport(journal.plan().placementId(), RecoveryClassification.SAFE_TO_ACCEPT,
                    findings, "Use recovery accept with the one-time token, or choose rollback after re-diagnosis.");
        }
        boolean rollbackSafe = observations.stream().allMatch(value ->
                value.state() == PlacementTileState.PENDING
                        || value.hasSnapshot() && (value.matchesRelease() || value.matchesSnapshot()));
        if (rollbackSafe) {
            boolean resumable = observations.stream().noneMatch(value ->
                    value.state() == PlacementTileState.SNAPSHOTTED && !value.matchesSnapshot());
            RecoveryClassification classification = resumable
                    ? RecoveryClassification.SAFE_TO_RESUME : RecoveryClassification.SAFE_TO_ROLLBACK;
            return new RecoveryReport(journal.plan().placementId(), classification, findings,
                    "Beta recovery does not auto-resume; use the offered rollback token for the conservative path.");
        }
        return new RecoveryReport(journal.plan().placementId(),
                RecoveryClassification.MANUAL_INTERVENTION_REQUIRED, findings,
                "Preserve journal, release, snapshots, and world backup; do not guess a successful state.");
    }

    private CompletableFuture<Void> appendRecoveryAudit(
            PlacementJournal journal,
            ActorIdentity actor,
            String decision,
            RecoveryReport report
    ) {
        return executors.runIo(() -> {
            Path root = snapshotsRoot.getParent();
            if (root == null) {
                throw new IllegalStateException("snapshot root has no parent");
            }
            Path audit = root.resolve("recovery-audit.jsonl").normalize();
            if (!audit.startsWith(root) || Files.isSymbolicLink(audit)) {
                throw new IllegalStateException("unsafe recovery audit path");
            }
            String line = "{\"timestamp\":\"" + clock.instant() + "\",\"placementId\":\""
                    + journal.plan().placementId() + "\",\"actor\":\"" + actor.canonical()
                    + "\",\"decision\":\"" + decision + "\",\"classification\":\""
                    + report.classification() + "\"}\n";
            try {
                Files.createDirectories(root);
                try (FileChannel channel = FileChannel.open(audit, StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                    ByteBuffer bytes = StandardCharsets.UTF_8.encode(line);
                    while (bytes.hasRemaining()) {
                        channel.write(bytes);
                    }
                    channel.force(true);
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private static List<String> append(List<String> values, String value) {
        List<String> result = new ArrayList<>(values);
        result.add(value);
        return List.copyOf(result);
    }

    private void validateConfirmation(
            PlacementJournal journal, ConfirmationAction action, String token, ActorIdentity actor
    ) {
        Objects.requireNonNull(actor, "actor");
        if (!journal.confirmationActor().equals(actor)) {
            throw new LandformException(
                    LandformErrorCode.CONFIRM_ACTOR_MISMATCH,
                    "The confirmation belongs to a different operator.", action.name().toLowerCase(java.util.Locale.ROOT),
                    journal.plan().placementId().toString(), "confirmation",
                    "Use the same player or console that prepared this operation."
            );
        }
        String expected = confirmationHash(
                journal.plan(), action, actor, journal.confirmationCreatedAt(),
                journal.confirmationExpiresAt(), token
        );
        if (journal.confirmationAction() != action
                || !constantTimeEquals(journal.confirmationHash(), expected)
                || !clock.instant().isBefore(journal.confirmationExpiresAt())) {
            throw new LandformException(
                    LandformErrorCode.CONFIRM_INVALID,
                    "The one-time confirmation is invalid, expired, or already used.",
                    action.name().toLowerCase(java.util.Locale.ROOT), journal.plan().placementId().toString(),
                    "confirmation", "Create a new plan with the same operator."
            );
        }
    }

    private static void validateBounds(
            WorldDescriptor world,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {
        if (minY < world.minY() || maxY > world.maxY()
                || minX < world.borderMinX() || maxX > world.borderMaxX()
                || minZ < world.borderMinZ() || maxZ > world.borderMaxZ()) {
            throw new IllegalArgumentException("placement bounds are outside world height or world border");
        }
    }

    private static void validateWorldIdentity(PlacementPlan plan, WorldDescriptor world) {
        if (!plan.worldId().equals(world.worldId()) || !plan.worldName().equals(world.worldName())) {
            throw new IllegalStateException("target world identity changed after planning");
        }
        validateBounds(world, plan.minimumX(), plan.minimumY(), plan.minimumZ(),
                plan.maximumX(), plan.maximumY(), plan.maximumZ());
    }

    private PlacementJournal updateTile(
            PlacementJournal journal,
            int index,
            PlacementTileState state,
            String snapshotFile,
            String checksum,
            long addedSnapshotBytes,
            String message
    ) {
        List<PlacementTileCheckpoint> tiles = new ArrayList<>(journal.tiles());
        tiles.set(index, new PlacementTileCheckpoint(
                tiles.get(index).tileId(), state, snapshotFile, checksum
        ));
        long used = Math.addExact(journal.snapshotBytesUsed(), addedSnapshotBytes);
        return replace(journal, journal.state(), journal.confirmationAction(), journal.confirmationActor(),
                journal.confirmationHash(), journal.confirmationCreatedAt(), journal.confirmationExpiresAt(),
                tiles, journal.reservedBytes(), used, clock.instant(), message);
    }

    private static PlacementJournal replace(
            PlacementJournal journal,
            PlacementState state,
            ConfirmationAction action,
            ActorIdentity actor,
            String hash,
            Instant createdAt,
            Instant expiresAt,
            List<PlacementTileCheckpoint> tiles,
            long reservedBytes,
            long snapshotBytesUsed,
            Instant updatedAt,
            String message
    ) {
        return new PlacementJournal(
                journal.schemaVersion(), journal.plan(), state, action, actor, hash, createdAt, expiresAt,
                tiles, reservedBytes, snapshotBytesUsed, updatedAt, message
        );
    }

    private void reserveFor(
            PlacementPlan plan,
            PlacementOperation operation,
            ActorIdentity actor,
            ReservationState state,
            Instant expiresAt,
            long diskBytes
    ) {
        try {
            safetyStore.reserve(plan, operation, actor, state, expiresAt, diskBytes,
                    diskPolicy.minimumFreeBytes());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void assertReservation(PlacementJournal journal, ActorIdentity actor) {
        try {
            safetyStore.assertOwned(journal.plan().placementId(), actor);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void activateReservation(
            PlacementJournal journal, PlacementOperation operation, ActorIdentity actor
    ) {
        assertReservation(journal, actor);
        reserveFor(journal.plan(), operation, actor, ReservationState.ACTIVE,
                clock.instant().plus(Duration.ofDays(36_500)), journal.reservedBytes());
    }

    private CompletableFuture<Void> releaseSafety(UUID placementId) {
        return executors.runIo(() -> {
            try {
                safetyStore.release(placementId);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private static String confirmationHash(
            PlacementPlan plan,
            ConfirmationAction action,
            ActorIdentity actor,
            Instant createdAt,
            Instant expiresAt,
            String nonce
    ) {
        String binding = action.name() + '\n'
                + plan.releaseChecksum() + '\n'
                + plan.worldId() + '\n'
                + plan.targetX() + ',' + plan.targetY() + ',' + plan.targetZ() + '\n'
                + plan.minimumX() + ',' + plan.minimumY() + ',' + plan.minimumZ() + ':'
                + plan.maximumX() + ',' + plan.maximumY() + ',' + plan.maximumZ() + '\n'
                + actor.canonical() + '\n'
                + createdAt + '\n' + expiresAt + '\n' + nonce;
        return sha256(binding);
    }

    private static String sha256(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof UncheckedIOException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record VerifiedRelease(ReleaseVerification verification, String checksum) {
    }

    private record RecoveryFiles(VerifiedRelease verification, boolean corrupted, List<String> findings) {
        private RecoveryFiles {
            findings = List.copyOf(findings);
        }
    }

    private record TileObservation(
            String tileId,
            PlacementTileState state,
            boolean matchesRelease,
            boolean matchesSnapshot,
            boolean hasSnapshot
    ) {
    }
}
