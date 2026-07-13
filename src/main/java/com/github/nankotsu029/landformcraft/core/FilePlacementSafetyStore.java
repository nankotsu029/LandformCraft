package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.ActorIdentity;
import com.github.nankotsu029.landformcraft.model.DiskReservation;
import com.github.nankotsu029.landformcraft.model.PlacementOperation;
import com.github.nankotsu029.landformcraft.model.PlacementPlan;
import com.github.nankotsu029.landformcraft.model.PlacementReservation;
import com.github.nankotsu029.landformcraft.model.PlacementSafetyState;
import com.github.nankotsu029.landformcraft.model.ReservationState;
import com.github.nankotsu029.landformcraft.model.PlacementJournal;
import com.github.nankotsu029.landformcraft.model.PlacementState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Strict-atomic reservation store. It never falls back to a non-atomic replace. */
public final class FilePlacementSafetyStore {
    private static final ConcurrentHashMap<Path, Object> LOCKS = new ConcurrentHashMap<>();

    private final Path file;
    private final Path snapshotsRoot;
    private final Object lock;
    private final Clock clock;
    private final LandformDataCodec codec = new LandformDataCodec();

    public FilePlacementSafetyStore(Path file, Path snapshotsRoot, Clock clock) {
        this.file = file.toAbsolutePath().normalize();
        this.snapshotsRoot = snapshotsRoot.toAbsolutePath().normalize();
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.lock = LOCKS.computeIfAbsent(this.file, ignored -> new Object());
    }

    public ReservationResult reserve(
            PlacementPlan plan,
            PlacementOperation operation,
            ActorIdentity actor,
            ReservationState reservationState,
            Instant expiresAt,
            long diskBytes,
            long minimumFreeBytes
    ) throws IOException {
        synchronized (lock) {
            Instant now = clock.instant();
            PlacementSafetyState current = prune(read(), now);
            PlacementReservation requested = new PlacementReservation(
                    plan.placementId(), plan.worldId(), plan.minimumX(), plan.minimumY(), plan.minimumZ(),
                    plan.maximumX(), plan.maximumY(), plan.maximumZ(), operation, actor,
                    now, expiresAt, reservationState
            );
            for (PlacementReservation reservation : current.placementReservations()) {
                if (!reservation.placementId().equals(plan.placementId()) && reservation.overlaps(requested)) {
                    throw new LandformException(
                            LandformErrorCode.PLACEMENT_OVERLAP,
                            "Another placement already reserves part of this world region.",
                            operation.name().toLowerCase(java.util.Locale.ROOT), plan.placementId().toString(),
                            "reservation", "Wait for the other placement to finish or choose non-overlapping bounds."
                    );
                }
            }

            Files.createDirectories(snapshotsRoot);
            if (Files.isSymbolicLink(snapshotsRoot)) {
                throw new IOException("snapshot root must not be a symbolic link");
            }
            FileStore fileStore = Files.getFileStore(snapshotsRoot);
            String storeKey = fileStore.name() + "|" + fileStore.type();
            long alreadyReserved = current.diskReservations().stream()
                    .filter(reservation -> reservation.fileStoreKey().equals(storeKey))
                    .filter(reservation -> !reservation.placementId().equals(plan.placementId()))
                    .mapToLong(DiskReservation::reservedBytes)
                    .reduce(0L, Math::addExact);
            long required;
            try {
                required = Math.addExact(Math.addExact(alreadyReserved, diskBytes), minimumFreeBytes);
            } catch (ArithmeticException exception) {
                throw noSpace(plan, exception);
            }
            long usable = fileStore.getUsableSpace();
            if (usable <= 0L || required > usable) {
                throw noSpace(plan, null);
            }

            List<PlacementReservation> placements = new ArrayList<>(current.placementReservations());
            placements.removeIf(value -> value.placementId().equals(plan.placementId()));
            placements.add(requested);
            placements.sort(Comparator.comparing(value -> value.placementId().toString()));
            List<DiskReservation> disks = new ArrayList<>(current.diskReservations());
            disks.removeIf(value -> value.placementId().equals(plan.placementId()));
            disks.add(new DiskReservation(plan.placementId(), storeKey, diskBytes, now, expiresAt));
            disks.sort(Comparator.comparing(value -> value.placementId().toString()));
            writeStrict(new PlacementSafetyState(1, placements, disks));
            return new ReservationResult(requested, diskBytes, usable, alreadyReserved);
        }
    }

    public void assertOwned(UUID placementId, ActorIdentity actor) throws IOException {
        synchronized (lock) {
            PlacementSafetyState current = prune(read(), clock.instant());
            PlacementReservation reservation = current.placementReservations().stream()
                    .filter(value -> value.placementId().equals(placementId))
                    .findFirst()
                    .orElseThrow(() -> new LandformException(
                            LandformErrorCode.RECOVERY_REQUIRED,
                            "The durable placement reservation is missing.", "placement-execute",
                            placementId.toString(), "reservation-recheck",
                            "Run recovery diagnosis before changing the world."
                    ));
            if (!reservation.actor().equals(actor)) {
                throw new LandformException(
                        LandformErrorCode.CONFIRM_ACTOR_MISMATCH,
                        "The confirmation belongs to a different operator.", "placement-execute",
                        placementId.toString(), "confirmation", "Use the same player or console that created the plan."
                );
            }
        }
    }

    public void release(UUID placementId) throws IOException {
        synchronized (lock) {
            PlacementSafetyState current = read();
            List<PlacementReservation> placements = current.placementReservations().stream()
                    .filter(value -> !value.placementId().equals(placementId)).toList();
            List<DiskReservation> disks = current.diskReservations().stream()
                    .filter(value -> !value.placementId().equals(placementId)).toList();
            writeStrict(new PlacementSafetyState(1, placements, disks));
        }
    }

    /** Reconstructs durable leases from non-terminal journals after startup without trusting stale lease files. */
    public void rebuild(List<PlacementJournal> journals) throws IOException {
        synchronized (lock) {
            Files.createDirectories(snapshotsRoot);
            FileStore store = Files.getFileStore(snapshotsRoot);
            String storeKey = store.name() + "|" + store.type();
            Instant now = clock.instant();
            Instant recoveryExpiry = now.plus(java.time.Duration.ofDays(36_500));
            List<PlacementReservation> placements = new ArrayList<>();
            List<DiskReservation> disks = new ArrayList<>();
            for (PlacementJournal journal : journals) {
                PlacementState placementState = journal.state();
                boolean planned = placementState == PlacementState.PLANNED
                        && journal.confirmationExpiresAt().isAfter(now);
                boolean active = placementState == PlacementState.APPLYING
                        || placementState == PlacementState.ROLLING_BACK
                        || placementState == PlacementState.UNDOING;
                boolean recovery = placementState == PlacementState.RECOVERY_REQUIRED;
                if (!planned && !active && !recovery) {
                    continue;
                }
                PlacementPlan plan = journal.plan();
                Instant expiresAt = planned ? journal.confirmationExpiresAt() : recoveryExpiry;
                ReservationState state = recovery ? ReservationState.RECOVERY_REQUIRED
                        : active ? ReservationState.ACTIVE : ReservationState.PLANNED;
                PlacementOperation operation = placementState == PlacementState.UNDOING
                        ? PlacementOperation.UNDO : PlacementOperation.APPLY;
                ActorIdentity actor = journal.confirmationAction() == com.github.nankotsu029.landformcraft.model.ConfirmationAction.NONE
                        ? plan.actor() : journal.confirmationActor();
                placements.add(new PlacementReservation(
                        plan.placementId(), plan.worldId(), plan.minimumX(), plan.minimumY(), plan.minimumZ(),
                        plan.maximumX(), plan.maximumY(), plan.maximumZ(), operation, actor,
                        now, expiresAt, state
                ));
                disks.add(new DiskReservation(
                        plan.placementId(), storeKey, journal.reservedBytes(), now, expiresAt));
            }
            placements.sort(Comparator.comparing(value -> value.placementId().toString()));
            disks.sort(Comparator.comparing(value -> value.placementId().toString()));
            writeStrict(new PlacementSafetyState(1, placements, disks));
        }
    }

    public PlacementSafetyState read() throws IOException {
        if (!Files.exists(file)) {
            return PlacementSafetyState.empty();
        }
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IOException("placement safety state must be a regular non-symbolic file");
        }
        return codec.readPlacementSafetyState(file);
    }

    private PlacementSafetyState prune(PlacementSafetyState state, Instant now) throws IOException {
        List<UUID> expired = state.placementReservations().stream()
                .filter(value -> value.state() != ReservationState.RECOVERY_REQUIRED)
                .filter(value -> !value.expiresAt().isAfter(now))
                .map(PlacementReservation::placementId).toList();
        if (expired.isEmpty()) {
            return state;
        }
        PlacementSafetyState pruned = new PlacementSafetyState(1,
                state.placementReservations().stream()
                        .filter(value -> !expired.contains(value.placementId())).toList(),
                state.diskReservations().stream()
                        .filter(value -> !expired.contains(value.placementId())).toList());
        writeStrict(pruned);
        return pruned;
    }

    private void writeStrict(PlacementSafetyState state) throws IOException {
        Path parent = file.getParent();
        if (parent == null) {
            throw new IOException("placement safety path has no parent");
        }
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent)) {
            throw new IOException("placement safety directory must not be a symbolic link");
        }
        byte[] bytes = codec.writeJsonString(state).getBytes(StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(parent, ".safety-", ".tmp");
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
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for placement safety state", exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static LandformException noSpace(PlacementPlan plan, Throwable cause) {
        return new LandformException(
                LandformErrorCode.SNAPSHOT_NO_SPACE,
                "Not enough unreserved disk space is available for snapshots and rollback.",
                "placement-plan", plan.placementId().toString(), "disk-reservation",
                "Free disk space, run snapshot cleanup, or use a smaller release.", cause
        );
    }

    public record ReservationResult(
            PlacementReservation placementReservation,
            long reservedBytes,
            long usableBytes,
            long bytesReservedByOtherPlacements
    ) {
    }
}
