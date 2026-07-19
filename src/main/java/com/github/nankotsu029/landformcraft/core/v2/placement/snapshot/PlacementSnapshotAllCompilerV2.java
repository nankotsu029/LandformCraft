package com.github.nankotsu029.landformcraft.core.v2.placement.snapshot;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FileStoreDiskSpaceProbeV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementDiskSpaceProbeV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationExceptionV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Snapshot-all engine for Release 2 placement (V2-6-04). Snapshots every effect envelope in
 * canonical tile-index order into a staging directory, strict-read-backs every snapshot file and
 * the sealed index, and only then atomically publishes and advances the journal to
 * {@code SNAPSHOT_COMPLETE} — the apply-ready state. Any failure or cancel removes the staging
 * directory so no canonical partial snapshot remains, and the journal stays at
 * {@code CONFIRMATION_ISSUED}. Never calls the gateway's apply entry point.
 */
public final class PlacementSnapshotAllCompilerV2 {
    public static final String STAGING_PREFIX = ".staging-";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PlacementSnapshotFileCodecV2 fileCodec = new PlacementSnapshotFileCodecV2();
    private final FilePlacementSafetyStoreV2 safetyStore;
    private final Clock clock;
    private final PlacementDiskSpaceProbeV2 diskProbe;

    public PlacementSnapshotAllCompilerV2(FilePlacementSafetyStoreV2 safetyStore, Clock clock) {
        this(safetyStore, clock, new FileStoreDiskSpaceProbeV2());
    }

    public PlacementSnapshotAllCompilerV2(
            FilePlacementSafetyStoreV2 safetyStore,
            Clock clock,
            PlacementDiskSpaceProbeV2 diskProbe
    ) {
        this.safetyStore = Objects.requireNonNull(safetyStore, "safetyStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diskProbe = Objects.requireNonNull(diskProbe, "diskProbe");
    }

    public PreparedSnapshotAllV2 snapshotAll(SnapshotAllRequestV2 request) {
        Objects.requireNonNull(request, "request");
        PlacementPlanV2 plan = request.confirmedPlan();
        PlacementEnvelopePlanV2 envelope = request.envelopePlan();
        PlacementReservationPlanV2 reservation = request.reservationPlan();

        validateBindings(request, plan, envelope, reservation);
        long reservedBytes = reservation.diskLease().reservedBytes();
        admit(request, envelope, reservedBytes);

        Path snapshotsRoot = safetyStore.snapshotsRoot();
        Path published = snapshotsRoot.resolve(plan.placementId().toString());
        Path staging = snapshotsRoot.resolve(STAGING_PREFIX + plan.placementId());
        if (Files.exists(published, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.ALREADY_PUBLISHED,
                    "snapshot directory already published for " + plan.placementId());
        }
        try {
            Files.createDirectory(staging);
        } catch (FileAlreadyExistsException inProgress) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_IN_PROGRESS,
                    "another snapshot-all is staging for " + plan.placementId()
                            + "; run cleanupAbandoned first if it crashed");
        } catch (IOException exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_IO_FAILURE,
                    "unable to create snapshot staging directory", exception);
        }

        try {
            Instant startedAt = clock.instant();
            PlacementJournalV2 snapshotting = sealJournal(
                    plan,
                    PlacementJournalStateV2.SNAPSHOTTING,
                    request.journal().reservedBytes(),
                    0L,
                    null,
                    startedAt,
                    "snapshotting all effect envelopes before any apply");
            request.journalSink().accept(snapshotting);

            List<PlacementSnapshotPlanV2.TileSnapshotV2> tiles = new ArrayList<>(envelope.tiles().size());
            long bytesUsed = 0L;
            for (PlacementEnvelopePlanV2.TileEnvelopeV2 tile : envelope.tiles()) {
                cancel(request.cancellation());
                Path file = staging.resolve(tile.tileId() + PlacementSnapshotPlanV2.SNAPSHOT_FILE_SUFFIX);
                PlacementSnapshotFileCodecV2.WrittenTileSnapshotV2 written = writeTile(
                        request, plan, tile, file, reservedBytes - bytesUsed);
                bytesUsed = Math.addExact(bytesUsed, written.fileBytes());
                tiles.add(new PlacementSnapshotPlanV2.TileSnapshotV2(
                        tile.tileId(),
                        tile.tileIndex(),
                        tile.effectAabb(),
                        tile.tileId() + PlacementSnapshotPlanV2.SNAPSHOT_FILE_SUFFIX,
                        written.fileBytes(),
                        written.blockCount(),
                        written.artifactChecksum(),
                        written.blockStateStreamChecksum()));
            }

            readBackTiles(request, plan, staging, tiles);
            requireExactTileFileSet(staging, tiles, false);

            PlacementSnapshotPlanV2 snapshotPlan = sealSnapshotPlan(
                    plan, envelope, reservation, request.budget(), tiles, clock.instant());
            snapshotPlan.requireBindings(plan, envelope, reservation);
            Path indexFile = staging.resolve(PlacementSnapshotPlanV2.INDEX_FILE_NAME);
            long indexBytes = writeAndReadBackIndex(indexFile, snapshotPlan);
            long totalBytesUsed = Math.addExact(bytesUsed, indexBytes);
            if (totalBytesUsed > reservedBytes) {
                throw new PlacementSnapshotExceptionV2(
                        PlacementSnapshotFailureCodeV2.DISK_BUDGET_EXCEEDED,
                        "snapshot files and index exceed the reserved disk lease");
            }

            cancel(request.cancellation());
            try {
                Files.move(staging, published, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                throw new PlacementSnapshotExceptionV2(
                        PlacementSnapshotFailureCodeV2.SNAPSHOT_IO_FAILURE,
                        "atomic publish of the snapshot directory failed", exception);
            }

            PlacementJournalV2 complete = sealJournal(
                    plan,
                    PlacementJournalStateV2.SNAPSHOT_COMPLETE,
                    request.journal().reservedBytes(),
                    totalBytesUsed,
                    snapshotPlan,
                    clock.instant(),
                    "all effect envelopes snapshotted and strict-verified; apply-ready");
            request.journalSink().accept(complete);
            return new PreparedSnapshotAllV2(snapshotting, snapshotPlan, complete, published);
        } catch (PlacementSnapshotExceptionV2 exception) {
            deleteRecursively(staging, exception);
            throw exception;
        } catch (RuntimeException exception) {
            PlacementSnapshotExceptionV2 wrapped = new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_IO_FAILURE,
                    "snapshot-all failed and staging was cleaned: " + exception.getMessage(),
                    exception);
            deleteRecursively(staging, wrapped);
            throw wrapped;
        }
    }

    /**
     * Strictly re-verifies a published snapshot directory after restart: sealed index schema／
     * checksum, bindings, exact file set, and every snapshot file's artifact and block-state
     * stream checksums. Never repairs or deletes published evidence.
     */
    public PlacementSnapshotPlanV2 loadPublished(
            PlacementPlanV2 confirmedPlan,
            PlacementEnvelopePlanV2 envelopePlan,
            PlacementReservationPlanV2 reservationPlan,
            CancellationToken cancellation
    ) {
        Objects.requireNonNull(confirmedPlan, "confirmedPlan");
        Objects.requireNonNull(envelopePlan, "envelopePlan");
        Objects.requireNonNull(reservationPlan, "reservationPlan");
        Objects.requireNonNull(cancellation, "cancellation");
        Path published = safetyStore.snapshotsRoot().resolve(confirmedPlan.placementId().toString());
        if (!Files.isDirectory(published, LinkOption.NOFOLLOW_LINKS)) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.STATE_MISMATCH,
                    "no published snapshot directory for " + confirmedPlan.placementId());
        }
        PlacementSnapshotPlanV2 snapshotPlan;
        try {
            snapshotPlan = codec.readPlacementSnapshotPlan(
                    published.resolve(PlacementSnapshotPlanV2.INDEX_FILE_NAME));
        } catch (IOException | IllegalArgumentException exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT,
                    "published snapshot index failed strict read-back: " + exception.getMessage(),
                    exception);
        }
        try {
            snapshotPlan.requireBindings(confirmedPlan, envelopePlan, reservationPlan);
        } catch (IllegalArgumentException exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.BINDING_MISMATCH,
                    exception.getMessage(), exception);
        }
        requireExactTileFileSet(published, snapshotPlan.tiles(), true);
        for (PlacementSnapshotPlanV2.TileSnapshotV2 tile : snapshotPlan.tiles()) {
            cancel(cancellation);
            verifyTileFile(published.resolve(tile.snapshotFile()), snapshotPlan, tile, cancellation);
        }
        return snapshotPlan;
    }

    /**
     * Removes an interrupted staging directory left by a crash. Published directories are never
     * touched: they only exist after full strict verification and atomic move.
     */
    public boolean cleanupAbandoned(UUID placementId) {
        Objects.requireNonNull(placementId, "placementId");
        Path staging = safetyStore.snapshotsRoot().resolve(STAGING_PREFIX + placementId);
        if (!Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        PlacementSnapshotExceptionV2 failure = new PlacementSnapshotExceptionV2(
                PlacementSnapshotFailureCodeV2.SNAPSHOT_IO_FAILURE,
                "unable to remove abandoned snapshot staging for " + placementId);
        if (!deleteRecursively(staging, failure)) {
            throw failure;
        }
        return true;
    }

    private void validateBindings(
            SnapshotAllRequestV2 request,
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation
    ) {
        if (request.journal().state() != PlacementJournalStateV2.CONFIRMATION_ISSUED) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.STATE_MISMATCH,
                    "snapshot-all requires a CONFIRMATION_ISSUED journal");
        }
        if (!request.journal().planChecksum().equals(plan.canonicalChecksum())) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.BINDING_MISMATCH,
                    "journal is bound to a different placement plan");
        }
        try {
            reservation.requirePlacementAndEnvelope(plan, envelope);
        } catch (IllegalArgumentException exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.BINDING_MISMATCH, exception.getMessage(), exception);
        }
        try {
            safetyStore.assertOwned(plan.placementId(), plan.actor());
        } catch (PlacementReservationExceptionV2 exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.RESERVATION_MISSING,
                    "durable reservation lease is missing or foreign: " + exception.getMessage(),
                    exception);
        } catch (IOException exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_IO_FAILURE,
                    "unable to read the placement safety ledger", exception);
        }
    }

    private void admit(
            SnapshotAllRequestV2 request,
            PlacementEnvelopePlanV2 envelope,
            long reservedBytes
    ) {
        PlacementSnapshotPlanV2.ResourceBudget budget = request.budget();
        if (envelope.tiles().size() > budget.maximumTiles()) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_BUDGET_EXCEEDED,
                    "envelope tile count exceeds the snapshot budget");
        }
        if (envelope.diskEstimate().snapshotBytes() > budget.maximumSnapshotBytes()) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_BUDGET_EXCEEDED,
                    "estimated snapshot bytes exceed the snapshot budget");
        }
        long usable;
        try {
            usable = diskProbe.usableBytes(safetyStore.snapshotsRoot());
        } catch (IOException exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.DISK_SHORTAGE,
                    "unable to probe usable snapshot disk space", exception);
        }
        long required;
        try {
            required = Math.addExact(reservedBytes, safetyStore.reservationFloorBytes());
        } catch (ArithmeticException overflow) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.DISK_SHORTAGE, "disk admission arithmetic overflow");
        }
        if (usable <= 0L || required > usable) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.DISK_SHORTAGE,
                    "not enough usable disk space to hold all effect-envelope snapshots");
        }
    }

    private PlacementSnapshotFileCodecV2.WrittenTileSnapshotV2 writeTile(
            SnapshotAllRequestV2 request,
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2.TileEnvelopeV2 tile,
            Path file,
            long remainingDiskBytes
    ) {
        if (remainingDiskBytes < 1L) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.DISK_BUDGET_EXCEEDED,
                    "reserved disk lease exhausted before " + tile.tileId());
        }
        try {
            return fileCodec.write(
                    request.gateway(),
                    plan.target().worldId(),
                    tile.tileId(),
                    tile.effectAabb(),
                    file,
                    request.budget().maximumPaletteEntriesPerTile(),
                    remainingDiskBytes,
                    request.cancellation());
        } catch (IOException exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_IO_FAILURE,
                    "snapshot write failed for " + tile.tileId() + ": " + exception.getMessage(),
                    exception);
        }
    }

    private void readBackTiles(
            SnapshotAllRequestV2 request,
            PlacementPlanV2 plan,
            Path staging,
            List<PlacementSnapshotPlanV2.TileSnapshotV2> tiles
    ) {
        for (PlacementSnapshotPlanV2.TileSnapshotV2 tile : tiles) {
            PlacementSnapshotFileCodecV2.WrittenTileSnapshotV2 readBack;
            try {
                readBack = fileCodec.readStrict(
                        staging.resolve(tile.snapshotFile()),
                        plan.target().worldId(),
                        tile.tileId(),
                        tile.effectAabb(),
                        request.budget().maximumPaletteEntriesPerTile(),
                        request.cancellation());
            } catch (IOException exception) {
                throw new PlacementSnapshotExceptionV2(
                        PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT,
                        "snapshot read-back failed for " + tile.tileId(), exception);
            }
            if (!readBack.artifactChecksum().equals(tile.artifactChecksum())
                    || !readBack.blockStateStreamChecksum().equals(tile.blockStateStreamChecksum())
                    || readBack.fileBytes() != tile.fileBytes()) {
                throw new PlacementSnapshotExceptionV2(
                        PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT,
                        "snapshot file changed between write and read-back for " + tile.tileId());
            }
        }
    }

    private void verifyTileFile(
            Path file,
            PlacementSnapshotPlanV2 snapshotPlan,
            PlacementSnapshotPlanV2.TileSnapshotV2 tile,
            CancellationToken cancellation
    ) {
        PlacementSnapshotFileCodecV2.WrittenTileSnapshotV2 readBack;
        try {
            readBack = fileCodec.readStrict(
                    file,
                    snapshotPlan.worldId(),
                    tile.tileId(),
                    tile.effectAabb(),
                    snapshotPlan.budget().maximumPaletteEntriesPerTile(),
                    cancellation);
        } catch (IOException exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT,
                    "published snapshot failed strict read-back for " + tile.tileId(), exception);
        }
        if (!readBack.artifactChecksum().equals(tile.artifactChecksum())
                || !readBack.blockStateStreamChecksum().equals(tile.blockStateStreamChecksum())
                || readBack.fileBytes() != tile.fileBytes()) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT,
                    "published snapshot checksum mismatch for " + tile.tileId());
        }
    }

    private void requireExactTileFileSet(
            Path directory,
            List<PlacementSnapshotPlanV2.TileSnapshotV2> tiles,
            boolean expectIndex
    ) {
        Set<String> expected = new HashSet<>();
        for (PlacementSnapshotPlanV2.TileSnapshotV2 tile : tiles) {
            expected.add(tile.snapshotFile());
        }
        if (expectIndex) {
            expected.add(PlacementSnapshotPlanV2.INDEX_FILE_NAME);
        }
        Set<String> actual = new HashSet<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (Files.isSymbolicLink(entry) || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new PlacementSnapshotExceptionV2(
                            PlacementSnapshotFailureCodeV2.PATH_UNSAFE,
                            "snapshot directory must contain only regular files: " + entry.getFileName());
                }
                actual.add(entry.getFileName().toString());
            }
        } catch (IOException exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_IO_FAILURE,
                    "unable to enumerate the snapshot directory", exception);
        }
        if (!actual.equals(expected)) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.FILE_SET_MISMATCH,
                    "snapshot directory file set does not match the index");
        }
    }

    private PlacementSnapshotPlanV2 sealSnapshotPlan(
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementSnapshotPlanV2.ResourceBudget budget,
            List<PlacementSnapshotPlanV2.TileSnapshotV2> tiles,
            Instant createdAt
    ) {
        long totalBlocks = 0L;
        long totalFileBytes = 0L;
        for (PlacementSnapshotPlanV2.TileSnapshotV2 tile : tiles) {
            totalBlocks = Math.addExact(totalBlocks, tile.blockCount());
            totalFileBytes = Math.addExact(totalFileBytes, tile.fileBytes());
        }
        return codec.sealPlacementSnapshotPlan(new PlacementSnapshotPlanV2(
                PlacementSnapshotPlanV2.VERSION,
                PlacementSnapshotPlanV2.SNAPSHOT_CONTRACT_VERSION,
                plan.placementId(),
                plan.operationId(),
                plan.target().worldId(),
                new PlacementSnapshotPlanV2.PlacementPlanBinding(
                        PlacementSnapshotPlanV2.PlacementPlanBinding.VERSION,
                        plan.canonicalChecksum(),
                        PlacementSnapshotPlanV2.PlacementPlanBinding.CONTRACT_VERSION),
                new PlacementSnapshotPlanV2.EnvelopeBinding(
                        PlacementSnapshotPlanV2.EnvelopeBinding.VERSION,
                        envelope.canonicalChecksum(),
                        envelope.mutationEnvelopeChecksum(),
                        PlacementSnapshotPlanV2.EnvelopeBinding.CONTRACT_VERSION),
                new PlacementSnapshotPlanV2.ReservationBinding(
                        PlacementSnapshotPlanV2.ReservationBinding.VERSION,
                        reservation.canonicalChecksum(),
                        plan.reservationConfirmationBinding().confirmationHash(),
                        PlacementSnapshotPlanV2.ReservationBinding.CONTRACT_VERSION),
                PlacementSnapshotPlanV2.SNAPSHOT_FILE_FORMAT_VERSION,
                tiles,
                totalBlocks,
                totalFileBytes,
                budget,
                createdAt.toString(),
                PlacementPlanV2.UNBOUND_CHECKSUM));
    }

    private long writeAndReadBackIndex(Path indexFile, PlacementSnapshotPlanV2 snapshotPlan) {
        try {
            codec.writePlacementSnapshotPlan(indexFile, snapshotPlan);
            PlacementSnapshotPlanV2 readBack = codec.readPlacementSnapshotPlan(indexFile);
            if (!readBack.equals(snapshotPlan)) {
                throw new PlacementSnapshotExceptionV2(
                        PlacementSnapshotFailureCodeV2.SNAPSHOT_CORRUPT,
                        "snapshot index changed between write and read-back");
            }
            return Files.size(indexFile);
        } catch (IOException exception) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.SNAPSHOT_IO_FAILURE,
                    "snapshot index write/read-back failed", exception);
        }
    }

    private PlacementJournalV2 sealJournal(
            PlacementPlanV2 plan,
            PlacementJournalStateV2 state,
            long reservedBytes,
            long snapshotBytesUsed,
            PlacementSnapshotPlanV2 snapshotPlan,
            Instant updatedAt,
            String message
    ) {
        List<PlacementJournalV2.PlacementTileEntryV2> entries =
                new ArrayList<>(plan.tileOrder().tiles().size());
        Map<String, PlacementSnapshotPlanV2.TileSnapshotV2> byId = new HashMap<>();
        if (snapshotPlan != null) {
            for (PlacementSnapshotPlanV2.TileSnapshotV2 tile : snapshotPlan.tiles()) {
                byId.put(tile.tileId(), tile);
            }
        }
        for (PlacementPlanV2.TileRefV2 tile : plan.tileOrder().tiles()) {
            if (snapshotPlan == null) {
                entries.add(new PlacementJournalV2.PlacementTileEntryV2(
                        tile.tileId(), tile.tileIndex(), PlacementTileStateV2.PENDING, "", ""));
            } else {
                PlacementSnapshotPlanV2.TileSnapshotV2 snapshot = byId.get(tile.tileId());
                if (snapshot == null) {
                    throw new PlacementSnapshotExceptionV2(
                            PlacementSnapshotFailureCodeV2.BINDING_MISMATCH,
                            "snapshot plan is missing tile " + tile.tileId());
                }
                entries.add(new PlacementJournalV2.PlacementTileEntryV2(
                        tile.tileId(),
                        tile.tileIndex(),
                        PlacementTileStateV2.SNAPSHOTTED,
                        plan.placementId() + "/" + snapshot.snapshotFile(),
                        snapshot.artifactChecksum()));
            }
        }
        return codec.sealPlacementJournal(new PlacementJournalV2(
                PlacementJournalV2.VERSION,
                PlacementJournalV2.JOURNAL_CONTRACT_VERSION,
                plan,
                plan.canonicalChecksum(),
                state,
                entries,
                reservedBytes,
                snapshotBytesUsed,
                updatedAt.toString(),
                message,
                PlacementPlanV2.UNBOUND_CHECKSUM));
    }

    private static void cancel(CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw new PlacementSnapshotExceptionV2(
                    PlacementSnapshotFailureCodeV2.CANCELLED, "snapshot-all was cancelled");
        }
    }

    private static boolean deleteRecursively(Path root, Throwable context) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
            return true;
        } catch (IOException exception) {
            context.addSuppressed(exception);
            return false;
        }
    }

    public record SnapshotAllRequestV2(
            PlacementPlanV2 confirmedPlan,
            PlacementEnvelopePlanV2 envelopePlan,
            PlacementReservationPlanV2 reservationPlan,
            PlacementJournalV2 journal,
            PlacementWorldGatewayV2 gateway,
            PlacementSnapshotPlanV2.ResourceBudget budget,
            CancellationToken cancellation,
            Consumer<PlacementJournalV2> journalSink
    ) {
        public SnapshotAllRequestV2 {
            Objects.requireNonNull(confirmedPlan, "confirmedPlan");
            Objects.requireNonNull(envelopePlan, "envelopePlan");
            Objects.requireNonNull(reservationPlan, "reservationPlan");
            Objects.requireNonNull(journal, "journal");
            Objects.requireNonNull(gateway, "gateway");
            Objects.requireNonNull(budget, "budget");
            Objects.requireNonNull(cancellation, "cancellation");
            Objects.requireNonNull(journalSink, "journalSink");
        }
    }

    public record PreparedSnapshotAllV2(
            PlacementJournalV2 snapshottingJournal,
            PlacementSnapshotPlanV2 snapshotPlan,
            PlacementJournalV2 snapshotCompleteJournal,
            Path publishedDirectory
    ) {
        public PreparedSnapshotAllV2 {
            Objects.requireNonNull(snapshottingJournal, "snapshottingJournal");
            Objects.requireNonNull(snapshotPlan, "snapshotPlan");
            Objects.requireNonNull(snapshotCompleteJournal, "snapshotCompleteJournal");
            Objects.requireNonNull(publishedDirectory, "publishedDirectory");
            if (snapshotCompleteJournal.state() != PlacementJournalStateV2.SNAPSHOT_COMPLETE) {
                throw new IllegalArgumentException("prepared journal must be SNAPSHOT_COMPLETE");
            }
        }
    }
}
