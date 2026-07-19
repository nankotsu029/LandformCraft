package com.github.nankotsu029.landformcraft.core.v2.placement.reservation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationLeaseStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationOperationV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSafetyStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strict-atomic Release 2 region／disk reservation ledger. Distinct from v1
 * {@code FilePlacementSafetyStore}; does not snapshot or apply world mutations.
 */
public final class FilePlacementSafetyStoreV2 {
    public static final long MINIMUM_FREE_BYTES = 1_048_576L;
    private static final ConcurrentHashMap<Path, Object> LOCKS = new ConcurrentHashMap<>();

    private final Path file;
    private final Path snapshotsRoot;
    private final Object lock;
    private final Clock clock;
    private final PlacementDiskSpaceProbeV2 diskProbe;
    private final long reservationFloorBytes;
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public FilePlacementSafetyStoreV2(Path file, Path snapshotsRoot, Clock clock) {
        this(file, snapshotsRoot, clock, new FileStoreDiskSpaceProbeV2());
    }

    public FilePlacementSafetyStoreV2(
            Path file,
            Path snapshotsRoot,
            Clock clock,
            PlacementDiskSpaceProbeV2 diskProbe
    ) {
        this(file, snapshotsRoot, clock, diskProbe, MINIMUM_FREE_BYTES);
    }

    public FilePlacementSafetyStoreV2(
            Path file,
            Path snapshotsRoot,
            Clock clock,
            PlacementDiskSpaceProbeV2 diskProbe,
            long reservationFloorBytes
    ) {
        this.file = file.toAbsolutePath().normalize();
        this.snapshotsRoot = snapshotsRoot.toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diskProbe = Objects.requireNonNull(diskProbe, "diskProbe");
        if (reservationFloorBytes < 0L) {
            throw new IllegalArgumentException("reservationFloorBytes must be non-negative");
        }
        this.reservationFloorBytes = reservationFloorBytes;
        this.lock = LOCKS.computeIfAbsent(this.file, ignored -> new Object());
    }

    /**
     * Free bytes that must remain unreserved after any Release 2 admission. V2-11-02 feeds this
     * from {@code disk.minimum-free-bytes} plus {@code disk.safety-margin-bytes}; callers that
     * predate the configured settings keep the historical {@link #MINIMUM_FREE_BYTES} floor.
     */
    public long reservationFloorBytes() {
        return reservationFloorBytes;
    }

    public ReservationResultV2 reserve(
            PlacementReservationPlanV2 sealedPlan,
            PlacementReservationLeaseStateV2 leaseState,
            long minimumFreeBytes
    ) throws IOException {
        Objects.requireNonNull(sealedPlan, "sealedPlan");
        Objects.requireNonNull(leaseState, "leaseState");
        if (minimumFreeBytes < 0) {
            throw new IllegalArgumentException("minimumFreeBytes must be non-negative");
        }
        if (PlacementPlanV2.UNBOUND_CHECKSUM.equals(sealedPlan.canonicalChecksum())) {
            throw new IllegalArgumentException("reservation plan must be sealed before reserve");
        }
        if (!sealedPlan.diskLease().fileStoreKey().equals(fileStoreKey())) {
            throw new PlacementReservationExceptionV2(
                    PlacementReservationFailureCodeV2.TARGET_MISMATCH,
                    "reservation disk lease fileStoreKey mismatch");
        }
        synchronized (lock) {
            Instant now = clock.instant();
            PlacementSafetyStateV2 current = prune(read(), now);
            if (current.regionReservations().size() >= PlacementSafetyStateV2.MAXIMUM_ENTRIES
                    || current.diskReservations().size() >= PlacementSafetyStateV2.MAXIMUM_ENTRIES) {
                throw new PlacementReservationExceptionV2(
                        PlacementReservationFailureCodeV2.ENTRY_BUDGET_EXCEEDED,
                        "placement safety store entry budget exceeded");
            }

            PlacementSafetyStateV2.RegionReservationEntryV2 requested = toRegionEntry(sealedPlan, leaseState);
            for (PlacementSafetyStateV2.RegionReservationEntryV2 existing : current.regionReservations()) {
                if (existing.overlaps(requested)) {
                    throw new PlacementReservationExceptionV2(
                            PlacementReservationFailureCodeV2.REGION_OVERLAP,
                            "another placement already reserves overlapping effect regions");
                }
            }

            String storeKey = sealedPlan.diskLease().fileStoreKey();
            long alreadyReserved = current.diskReservations().stream()
                    .filter(reservation -> reservation.fileStoreKey().equals(storeKey))
                    .filter(reservation -> !reservation.placementId().equals(sealedPlan.placementId()))
                    .mapToLong(PlacementSafetyStateV2.DiskReservationEntryV2::reservedBytes)
                    .reduce(0L, Math::addExact);
            long required;
            try {
                required = Math.addExact(
                        Math.addExact(alreadyReserved, sealedPlan.diskLease().reservedBytes()),
                        minimumFreeBytes);
            } catch (ArithmeticException overflow) {
                throw new PlacementReservationExceptionV2(
                        PlacementReservationFailureCodeV2.DISK_SHORTAGE,
                        "disk reservation arithmetic overflow");
            }
            long usable = diskProbe.usableBytes(snapshotsRoot);
            if (usable <= 0L || required > usable) {
                throw new PlacementReservationExceptionV2(
                        PlacementReservationFailureCodeV2.DISK_SHORTAGE,
                        "not enough unreserved disk space for effect-envelope snapshots");
            }

            List<PlacementSafetyStateV2.RegionReservationEntryV2> regions =
                    new ArrayList<>(current.regionReservations());
            regions.removeIf(value -> value.placementId().equals(sealedPlan.placementId()));
            regions.add(requested);
            regions.sort(Comparator.comparing(value -> value.placementId().toString()));

            List<PlacementSafetyStateV2.DiskReservationEntryV2> disks =
                    new ArrayList<>(current.diskReservations());
            disks.removeIf(value -> value.placementId().equals(sealedPlan.placementId()));
            disks.add(new PlacementSafetyStateV2.DiskReservationEntryV2(
                    sealedPlan.placementId(),
                    storeKey,
                    sealedPlan.diskLease().reservedBytes(),
                    sealedPlan.createdAt(),
                    sealedPlan.expiresAt()));
            disks.sort(Comparator.comparing(value -> value.placementId().toString()));

            PlacementSafetyStateV2 next = codec.sealPlacementSafetyState(new PlacementSafetyStateV2(
                    PlacementSafetyStateV2.VERSION,
                    PlacementSafetyStateV2.SAFETY_CONTRACT_VERSION,
                    regions,
                    disks,
                    current.consumedConfirmationHashes(),
                    PlacementPlanV2.UNBOUND_CHECKSUM));
            writeStrict(next);
            return new ReservationResultV2(sealedPlan, usable, alreadyReserved);
        }
    }

    /** Root directory reserved for Release 2 effect-envelope snapshots (V2-6-04 writes below it). */
    public Path snapshotsRoot() {
        return snapshotsRoot;
    }

    public String fileStoreKey() throws IOException {
        Files.createDirectories(snapshotsRoot);
        if (Files.isSymbolicLink(snapshotsRoot)) {
            throw new IOException("snapshot root must not be a symbolic link");
        }
        return diskProbe.fileStoreKey(snapshotsRoot);
    }

    public void markConfirmationConsumed(String confirmationHash) throws IOException {
        Objects.requireNonNull(confirmationHash, "confirmationHash");
        synchronized (lock) {
            PlacementSafetyStateV2 current = prune(read(), clock.instant());
            if (current.consumedConfirmationHashes().contains(confirmationHash)) {
                throw new PlacementReservationExceptionV2(
                        PlacementReservationFailureCodeV2.CONFIRMATION_REPLAY,
                        "confirmation token has already been consumed");
            }
            if (current.consumedConfirmationHashes().size() >= PlacementSafetyStateV2.MAXIMUM_ENTRIES) {
                throw new PlacementReservationExceptionV2(
                        PlacementReservationFailureCodeV2.ENTRY_BUDGET_EXCEEDED,
                        "consumed confirmation budget exceeded");
            }
            List<String> consumed = new ArrayList<>(current.consumedConfirmationHashes());
            consumed.add(confirmationHash);
            writeStrict(codec.sealPlacementSafetyState(new PlacementSafetyStateV2(
                    PlacementSafetyStateV2.VERSION,
                    PlacementSafetyStateV2.SAFETY_CONTRACT_VERSION,
                    current.regionReservations(),
                    current.diskReservations(),
                    consumed,
                    PlacementPlanV2.UNBOUND_CHECKSUM)));
        }
    }

    public boolean isConfirmationConsumed(String confirmationHash) throws IOException {
        Objects.requireNonNull(confirmationHash, "confirmationHash");
        synchronized (lock) {
            return read().consumedConfirmationHashes().contains(confirmationHash);
        }
    }

    public void assertOwned(UUID placementId, PlacementPlanV2.PlacementActorV2 actor) throws IOException {
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(actor, "actor");
        synchronized (lock) {
            PlacementSafetyStateV2 current = prune(read(), clock.instant());
            PlacementSafetyStateV2.RegionReservationEntryV2 reservation = current.regionReservations().stream()
                    .filter(value -> value.placementId().equals(placementId))
                    .findFirst()
                    .orElseThrow(() -> new PlacementReservationExceptionV2(
                            PlacementReservationFailureCodeV2.STATE_MISMATCH,
                            "durable placement reservation is missing"));
            if (!reservation.actor().equals(actor)) {
                throw new PlacementReservationExceptionV2(
                        PlacementReservationFailureCodeV2.ACTOR_MISMATCH,
                        "reservation belongs to a different actor");
            }
        }
    }

    public void release(UUID placementId) throws IOException {
        Objects.requireNonNull(placementId, "placementId");
        synchronized (lock) {
            PlacementSafetyStateV2 current = read();
            List<PlacementSafetyStateV2.RegionReservationEntryV2> regions = current.regionReservations().stream()
                    .filter(value -> !value.placementId().equals(placementId))
                    .toList();
            List<PlacementSafetyStateV2.DiskReservationEntryV2> disks = current.diskReservations().stream()
                    .filter(value -> !value.placementId().equals(placementId))
                    .toList();
            writeStrict(codec.sealPlacementSafetyState(new PlacementSafetyStateV2(
                    PlacementSafetyStateV2.VERSION,
                    PlacementSafetyStateV2.SAFETY_CONTRACT_VERSION,
                    regions,
                    disks,
                    current.consumedConfirmationHashes(),
                    PlacementPlanV2.UNBOUND_CHECKSUM)));
        }
    }

    /**
     * Rebuilds leases from non-terminal journals after restart without trusting a stale ledger file.
     * Journals at {@code CONFIRMATION_ISSUED}／{@code RESERVATION_BOUND}／active apply states are kept.
     *
     * <p>V2-11-02: each rebuilt region lease covers the sealed <em>effect</em> envelope AABBs of the
     * placement, not the plan target box. The effect envelope is the mutation box expanded by the
     * fluid／gravity containment radii, so a target-based rebuild would under-reserve the halo and
     * let a third party bind an overlapping placement after a restart. The disk lease is likewise
     * rebuilt from the sealed envelope disk estimate rather than the journal's reported bytes.
     */
    public void rebuild(List<RebuildEntryV2> entries) throws IOException {
        Objects.requireNonNull(entries, "entries");
        List<PlacementJournalV2> journals = new ArrayList<>(entries.size());
        java.util.Map<UUID, PlacementEnvelopePlanV2> envelopes = new java.util.HashMap<>();
        for (RebuildEntryV2 entry : entries) {
            Objects.requireNonNull(entry, "entries");
            // The journal's plan has advanced past the envelope-bound checksum (reservation and
            // confirmation bindings re-seal it), so bind through the plan's envelope references
            // rather than the envelope's own source-plan checksum.
            PlacementPlanV2.EnvelopeReferencesV2 references = entry.journal().plan().envelopeReferences();
            if (!references.bound()
                    || !references.effectEnvelopePlanChecksum().equals(entry.envelope().canonicalChecksum())
                    || !references.mutationEnvelopePlanChecksum()
                    .equals(entry.envelope().mutationEnvelopeChecksum())) {
                throw new IOException(
                        "restart reservation rebuild envelope does not match placement "
                                + entry.journal().plan().placementId());
            }
            journals.add(entry.journal());
            envelopes.put(entry.journal().plan().placementId(), entry.envelope());
        }
        synchronized (lock) {
            Files.createDirectories(snapshotsRoot);
            String storeKey = diskProbe.fileStoreKey(snapshotsRoot);
            Instant now = clock.instant();
            Instant recoveryExpiry = now.plus(java.time.Duration.ofDays(36_500));
            List<PlacementSafetyStateV2.RegionReservationEntryV2> regions = new ArrayList<>();
            List<PlacementSafetyStateV2.DiskReservationEntryV2> disks = new ArrayList<>();
            for (PlacementJournalV2 journal : journals) {
                Objects.requireNonNull(journal, "journals");
                boolean planned = switch (journal.state()) {
                    case RESERVATION_BOUND, CONFIRMATION_ISSUED -> {
                        String expires = journal.plan().reservationConfirmationBinding().confirmationIssued()
                                ? journal.plan().reservationConfirmationBinding().confirmationExpiresAt()
                                : now.plus(java.time.Duration.ofMinutes(10)).toString();
                        yield Instant.parse(expires).isAfter(now);
                    }
                    default -> false;
                };
                boolean active = switch (journal.state()) {
                    case SNAPSHOTTING, SNAPSHOT_COMPLETE, APPLYING, SETTLING, VERIFYING,
                            ROLLING_BACK, UNDOING -> true;
                    default -> false;
                };
                boolean recovery = journal.state()
                        == com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementJournalStateV2.RECOVERY_REQUIRED;
                if (!planned && !active && !recovery) {
                    continue;
                }
                PlacementPlanV2 plan = journal.plan();
                String createdAt = now.toString();
                String expiresAt = planned
                        ? (plan.reservationConfirmationBinding().confirmationIssued()
                        ? plan.reservationConfirmationBinding().confirmationExpiresAt()
                        : now.plus(java.time.Duration.ofMinutes(10)).toString())
                        : recoveryExpiry.toString();
                PlacementReservationLeaseStateV2 leaseState = recovery
                        ? PlacementReservationLeaseStateV2.RECOVERY_REQUIRED
                        : active
                        ? PlacementReservationLeaseStateV2.ACTIVE
                        : PlacementReservationLeaseStateV2.PLANNED;
                PlacementReservationOperationV2 operation = journal.state()
                        == com.github.nankotsu029.landformcraft.model.v2.placement
                        .PlacementJournalStateV2.UNDOING
                        ? PlacementReservationOperationV2.UNDO
                        : PlacementReservationOperationV2.APPLY;
                PlacementPlanV2.PlacementActorV2 actor = plan.reservationConfirmationBinding().confirmationIssued()
                        ? plan.reservationConfirmationBinding().confirmationActor()
                        : plan.actor();
                PlacementEnvelopePlanV2 envelope = envelopes.get(plan.placementId());
                if (envelope == null) {
                    throw new IOException(
                            "restart reservation rebuild requires the sealed effect envelope for "
                                    + plan.placementId());
                }
                List<WorldAabbV2> regionAabbs = envelope.tiles().stream()
                        .map(PlacementEnvelopePlanV2.TileEnvelopeV2::effectAabb)
                        .toList();
                String reservationChecksum = plan.reservationConfirmationBinding().reservationBound()
                        ? plan.reservationConfirmationBinding().reservationChecksum()
                        : PlacementPlanV2.UNBOUND_CHECKSUM;
                regions.add(new PlacementSafetyStateV2.RegionReservationEntryV2(
                        plan.placementId(),
                        plan.target().worldId(),
                        regionAabbs,
                        operation,
                        actor,
                        leaseState,
                        createdAt,
                        expiresAt,
                        reservationChecksum));
                long reserved = Math.max(
                        Math.max(1L, journal.reservedBytes()),
                        envelope.diskEstimate().totalBytes());
                disks.add(new PlacementSafetyStateV2.DiskReservationEntryV2(
                        plan.placementId(), storeKey, reserved, createdAt, expiresAt));
            }
            regions.sort(Comparator.comparing(value -> value.placementId().toString()));
            disks.sort(Comparator.comparing(value -> value.placementId().toString()));
            // Region／disk leases are recomputed from the journals and their sealed effect
            // envelopes, but the consumed-confirmation set is a monotonic anti-replay record:
            // dropping it on rebuild would let an already-spent confirmation token be reused.
            List<String> consumed = List.of();
            if (Files.exists(file)) {
                consumed = read().consumedConfirmationHashes();
            }
            writeStrict(codec.sealPlacementSafetyState(new PlacementSafetyStateV2(
                    PlacementSafetyStateV2.VERSION,
                    PlacementSafetyStateV2.SAFETY_CONTRACT_VERSION,
                    regions,
                    disks,
                    consumed,
                    PlacementPlanV2.UNBOUND_CHECKSUM)));
        }
    }

    public PlacementSafetyStateV2 read() throws IOException {
        if (!Files.exists(file)) {
            return PlacementSafetyStateV2.empty();
        }
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IOException("placement safety state must be a regular non-symbolic file");
        }
        return codec.readPlacementSafetyStateV2(file);
    }

    private PlacementSafetyStateV2 prune(PlacementSafetyStateV2 state, Instant now) throws IOException {
        List<UUID> expired = state.regionReservations().stream()
                .filter(value -> value.state() != PlacementReservationLeaseStateV2.RECOVERY_REQUIRED)
                .filter(value -> !Instant.parse(value.expiresAt()).isAfter(now))
                .map(PlacementSafetyStateV2.RegionReservationEntryV2::placementId)
                .toList();
        if (expired.isEmpty()) {
            return state;
        }
        PlacementSafetyStateV2 pruned = codec.sealPlacementSafetyState(new PlacementSafetyStateV2(
                PlacementSafetyStateV2.VERSION,
                PlacementSafetyStateV2.SAFETY_CONTRACT_VERSION,
                state.regionReservations().stream()
                        .filter(value -> !expired.contains(value.placementId()))
                        .toList(),
                state.diskReservations().stream()
                        .filter(value -> !expired.contains(value.placementId()))
                        .toList(),
                state.consumedConfirmationHashes(),
                PlacementPlanV2.UNBOUND_CHECKSUM));
        writeStrict(pruned);
        return pruned;
    }

    private void writeStrict(PlacementSafetyStateV2 state) throws IOException {
        Path parent = file.getParent();
        if (parent == null) {
            throw new IOException("placement safety path has no parent");
        }
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent)) {
            throw new IOException("placement safety directory must not be a symbolic link");
        }
        byte[] bytes = codec.canonicalPlacementSafetyState(state).getBytes(StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(parent, ".safety-v2-", ".tmp");
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

    private static PlacementSafetyStateV2.RegionReservationEntryV2 toRegionEntry(
            PlacementReservationPlanV2 plan,
            PlacementReservationLeaseStateV2 leaseState
    ) {
        List<WorldAabbV2> regions = plan.regions().stream()
                .map(PlacementReservationPlanV2.RegionLeaseV2::region)
                .toList();
        return new PlacementSafetyStateV2.RegionReservationEntryV2(
                plan.placementId(),
                plan.worldId(),
                regions,
                plan.operation(),
                plan.actor(),
                leaseState,
                plan.createdAt(),
                plan.expiresAt(),
                plan.canonicalChecksum());
    }

    public record ReservationResultV2(
            PlacementReservationPlanV2 reservationPlan,
            long usableBytes,
            long bytesReservedByOtherPlacements
    ) {
    }

    /**
     * One non-terminal placement plus its sealed envelope. The envelope is required so the
     * rebuilt lease covers the effect envelope rather than the plan target box (V2-11-02).
     */
    public record RebuildEntryV2(PlacementJournalV2 journal, PlacementEnvelopePlanV2 envelope) {
        public RebuildEntryV2 {
            Objects.requireNonNull(journal, "journal");
            Objects.requireNonNull(envelope, "envelope");
        }
    }
}
