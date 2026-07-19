package com.github.nankotsu029.landformcraft.core.v2.placement.rollback;

import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementJournalStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotAllCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotFileCodecV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Release 2 rollback (V2-6-08). Starts only from a persisted {@code RECOVERY_REQUIRED} journal,
 * strictly re-verifies the published snapshot-all evidence before the first world mutation, then
 * restores the snapshotted effect envelopes in reverse canonical tile order as bounded restore
 * slices, checkpoints a canonical RESTORED tile suffix under {@code ROLLING_BACK}, runs a bounded
 * rollback settle, and full-verifies the entire union effect envelope against the snapshot
 * baseline before sealing terminal {@code ROLLED_BACK} and releasing the durable reservation.
 *
 * <p>Any partial failure is classified via {@link PlacementRollbackFailureCodeV2} and leaves the
 * journal in {@code RECOVERY_REQUIRED}; rollback never reports success it cannot prove. Published
 * snapshot files are never deleted here — they remain recovery evidence until a later retention
 * Task. Side effects outside the snapshotted effect envelope are out of scope by contract.</p>
 */
public final class PlacementRollbackServiceV2 implements AutoCloseable {
    private final PlacementSnapshotAllCompilerV2 snapshotCompiler;
    private final FilePlacementSafetyStoreV2 safetyStore;
    private final PlacementWorldGatewayV2 gateway;
    private final PlacementJournalStoreV2 journalStore;
    private final Clock clock;
    private final PlacementRollbackLimitsV2 limits;
    private final PlacementSnapshotFileCodecV2 snapshotCodec = new PlacementSnapshotFileCodecV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final ThreadPoolExecutor workers;
    private final Semaphore admissions;
    private final ConcurrentHashMap<UUID, Boolean> activeOperations = new ConcurrentHashMap<>();
    private final AtomicInteger activeCount = new AtomicInteger();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final CompletableFuture<Void> termination = new CompletableFuture<>();

    public PlacementRollbackServiceV2(
            PlacementSnapshotAllCompilerV2 snapshotCompiler,
            FilePlacementSafetyStoreV2 safetyStore,
            PlacementWorldGatewayV2 gateway,
            PlacementJournalStoreV2 journalStore,
            Clock clock
    ) {
        this(snapshotCompiler, safetyStore, gateway, journalStore, clock,
                PlacementRollbackLimitsV2.defaults());
    }

    public PlacementRollbackServiceV2(
            PlacementSnapshotAllCompilerV2 snapshotCompiler,
            FilePlacementSafetyStoreV2 safetyStore,
            PlacementWorldGatewayV2 gateway,
            PlacementJournalStoreV2 journalStore,
            Clock clock,
            PlacementRollbackLimitsV2 limits
    ) {
        this.snapshotCompiler = Objects.requireNonNull(snapshotCompiler, "snapshotCompiler");
        this.safetyStore = Objects.requireNonNull(safetyStore, "safetyStore");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.journalStore = Objects.requireNonNull(journalStore, "journalStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.limits = Objects.requireNonNull(limits, "limits");
        int queueCapacity = Math.addExact(limits.maximumQueuedTransactions(), limits.workerThreads());
        this.workers = new ThreadPoolExecutor(
                limits.workerThreads(),
                limits.workerThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new RollbackWorkerThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.admissions = new Semaphore(
                Math.addExact(limits.workerThreads(), limits.maximumQueuedTransactions()), true);
    }

    public CompletionStage<RollbackResultV2> rollback(PlacementRollbackRequestV2 request) {
        Objects.requireNonNull(request, "request");
        if (!accepting.get()) {
            return failed(new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.SERVICE_CLOSED,
                    "Release 2 rollback service is closed",
                    false));
        }
        if (!admissions.tryAcquire()) {
            return failed(new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.QUEUE_SATURATED,
                    "Release 2 rollback queue is saturated",
                    false));
        }
        UUID operationId = request.placementPlan().operationId();
        if (activeOperations.putIfAbsent(operationId, Boolean.TRUE) != null) {
            admissions.release();
            return failed(new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.STATE_MISMATCH,
                    "Release 2 rollback operation is already active",
                    false));
        }
        Transaction transaction = new Transaction(request);
        activeCount.incrementAndGet();
        try {
            workers.execute(() -> start(transaction));
        } catch (RejectedExecutionException rejected) {
            finishAdmission(transaction);
            return failed(new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.QUEUE_SATURATED,
                    "Release 2 rollback worker queue rejected the transaction",
                    false,
                    rejected));
        }
        return transaction.result.minimalCompletionStage();
    }

    @Override
    public void close() {
        if (!accepting.compareAndSet(true, false)) {
            return;
        }
        if (activeCount.get() == 0) {
            workers.shutdown();
            termination.complete(null);
        }
    }

    public CompletionStage<Void> termination() {
        return termination.minimalCompletionStage();
    }

    private void start(Transaction transaction) {
        try {
            if (!accepting.get()) {
                throw new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.SERVICE_CLOSED,
                        "rollback service closed before admission",
                        false);
            }
            preflight(transaction);
            buildBaseline(transaction);
            PlacementJournalV2 rollingBack = sealJournal(
                    transaction.request.failedJournal(),
                    PlacementJournalStateV2.ROLLING_BACK,
                    transaction.request.failedJournal().tiles(),
                    "Release 2 reverse-order rollback started from RECOVERY_REQUIRED");
            saveJournal(rollingBack);
            transaction.currentJournal = rollingBack;
            transaction.restoreTileCursor = rollingBack.tiles().size() - 1;
            submitNextRestoreSlice(transaction);
        } catch (Throwable failure) {
            fail(transaction, normalize(failure));
        }
    }

    private void preflight(Transaction transaction) {
        PlacementRollbackRequestV2 request = transaction.request;
        PlacementSettleVerifyPolicyV2 policy = request.policy();
        if (!PlacementSettleVerifyPolicyV2.POLICY_VERSION.equals(policy.policyVersion())) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.UNKNOWN_POLICY,
                    "unknown rollback settle/verify policy version",
                    false);
        }
        PlacementJournalV2 journal = request.failedJournal();
        if (journal.state() != PlacementJournalStateV2.RECOVERY_REQUIRED) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.STATE_MISMATCH,
                    "rollback requires a RECOVERY_REQUIRED journal",
                    false);
        }
        for (PlacementJournalV2.PlacementTileEntryV2 tile : journal.tiles()) {
            if (tile.state() != PlacementTileStateV2.SNAPSHOTTED
                    && tile.state() != PlacementTileStateV2.APPLIED) {
                throw new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.STATE_MISMATCH,
                        "rollback requires every tile SNAPSHOTTED or APPLIED, found "
                                + tile.state() + " for " + tile.tileId(),
                        false);
            }
        }
        if (!journal.planChecksum().equals(request.placementPlan().canonicalChecksum())
                || !journal.plan().canonicalChecksum()
                .equals(request.placementPlan().canonicalChecksum())) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.BINDING_MISMATCH,
                    "rollback journal plan binding mismatch",
                    false);
        }
        try {
            request.snapshotPlan().requireBindings(
                    request.placementPlan(), request.envelopePlan(), request.reservationPlan());
        } catch (IllegalArgumentException mismatch) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.BINDING_MISMATCH,
                    mismatch.getMessage(),
                    false,
                    mismatch);
        }
        try {
            safetyStore.assertOwned(
                    request.placementPlan().placementId(), request.placementPlan().actor());
        } catch (PlacementReservationExceptionV2 missing) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.RESERVATION_MISSING,
                    "durable reservation lease is missing or foreign: " + missing.getMessage(),
                    false,
                    missing);
        } catch (IOException io) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.RESERVATION_MISSING,
                    "unable to read the placement safety ledger",
                    false,
                    io);
        }
        long volume = request.envelopePlan().unionEffectEnvelope().volumeBlocks();
        if (volume > policy.maximumEffectEnvelopeBlocks()
                || volume > policy.budget().maximumScannedBlocks()) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.RESOURCE_BUDGET_EXCEEDED,
                    "effect envelope exceeds rollback restore/verify budget",
                    false);
        }
        long totalSlices = 0L;
        for (PlacementSnapshotPlanV2.TileSnapshotV2 tile : request.snapshotPlan().tiles()) {
            long tileSlices = (tile.blockCount() + limits.maximumBlocksPerRestoreSlice() - 1L)
                    / limits.maximumBlocksPerRestoreSlice();
            totalSlices = Math.addExact(totalSlices, tileSlices);
        }
        if (totalSlices > limits.maximumRestoreSlices()) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.RESTORE_SLICE_BUDGET,
                    "restore slice budget exceeded before the first restore submission",
                    false);
        }
        if (request.cancellation().isCancellationRequested()) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.CANCELLED,
                    "rollback cancelled before the first restore slice",
                    false);
        }
    }

    /**
     * Strictly re-verifies the published snapshot directory and decodes every snapshot file into
     * the in-memory baseline for the union effect envelope. Rejects missing／tampered snapshots,
     * inconsistent overlapping tiles, and coverage gaps before any world mutation.
     */
    private void buildBaseline(Transaction transaction) {
        PlacementRollbackRequestV2 request = transaction.request;
        PlacementSnapshotPlanV2 published;
        try {
            published = snapshotCompiler.loadPublished(
                    request.placementPlan(),
                    request.envelopePlan(),
                    request.reservationPlan(),
                    request.cancellation());
        } catch (PlacementSnapshotExceptionV2 snapshotFailure) {
            throw new PlacementRollbackExceptionV2(
                    classifySnapshotFailure(snapshotFailure),
                    "published snapshot failed strict re-verification: "
                            + snapshotFailure.getMessage(),
                    false,
                    snapshotFailure);
        }
        if (!published.canonicalChecksum().equals(request.snapshotPlan().canonicalChecksum())) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.BINDING_MISMATCH,
                    "published snapshot index differs from the requested snapshot plan",
                    false);
        }
        Path publishedDirectory = safetyStore.snapshotsRoot()
                .resolve(request.placementPlan().placementId().toString());
        Map<Coord, String> baseline = new HashMap<>();
        for (PlacementSnapshotPlanV2.TileSnapshotV2 tile : published.tiles()) {
            WorldAabbV2 region = tile.effectAabb();
            PlacementSnapshotFileCodecV2.WrittenTileSnapshotV2 read;
            try {
                read = snapshotCodec.readStrict(
                        publishedDirectory.resolve(tile.snapshotFile()),
                        request.placementPlan().target().worldId(),
                        tile.tileId(),
                        region,
                        PlacementSnapshotPlanV2.MAXIMUM_PALETTE_ENTRIES_PER_TILE,
                        request.cancellation(),
                        (index, state) -> {
                            int[] xyz = decodeCanonicalIndex(region, index);
                            Coord coord = new Coord(xyz[0], xyz[1], xyz[2]);
                            String existing = baseline.putIfAbsent(coord, state);
                            if (existing != null && !existing.equals(state)) {
                                throw new PlacementRollbackExceptionV2(
                                        PlacementRollbackFailureCodeV2.SNAPSHOT_TAMPERED,
                                        "overlapping snapshot tiles disagree at ("
                                                + xyz[0] + "," + xyz[1] + "," + xyz[2] + ")",
                                        false);
                            }
                        });
            } catch (PlacementSnapshotExceptionV2 snapshotFailure) {
                throw new PlacementRollbackExceptionV2(
                        classifySnapshotFailure(snapshotFailure),
                        "snapshot file failed strict decode for " + tile.tileId() + ": "
                                + snapshotFailure.getMessage(),
                        false,
                        snapshotFailure);
            } catch (IOException io) {
                throw new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.SNAPSHOT_TAMPERED,
                        "snapshot file unreadable for " + tile.tileId(),
                        false,
                        io);
            }
            if (!read.artifactChecksum().equals(tile.artifactChecksum())
                    || !read.blockStateStreamChecksum().equals(tile.blockStateStreamChecksum())) {
                throw new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.SNAPSHOT_TAMPERED,
                        "snapshot checksum mismatch against the sealed index for " + tile.tileId(),
                        false);
            }
        }
        WorldAabbV2 union = request.envelopePlan().unionEffectEnvelope();
        MessageDigest digest = sha256();
        for (int y = union.minY(); y <= union.maxY(); y++) {
            for (int z = union.minZ(); z <= union.maxZ(); z++) {
                for (int x = union.minX(); x <= union.maxX(); x++) {
                    String state = baseline.get(new Coord(x, y, z));
                    if (state == null) {
                        throw new PlacementRollbackExceptionV2(
                                PlacementRollbackFailureCodeV2.SNAPSHOT_COVERAGE_GAP,
                                "snapshot tiles do not cover the union effect envelope at ("
                                        + x + "," + y + "," + z + ")",
                                false);
                    }
                    digest.update(state.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
            }
        }
        transaction.baseline = Map.copyOf(baseline);
        transaction.baselineChecksum = HexFormat.of().formatHex(digest.digest());
    }

    private void submitNextRestoreSlice(Transaction transaction) {
        try {
            requireOpen(transaction, true);
            if (transaction.restoreTileCursor < 0) {
                beginSettle(transaction);
                return;
            }
            PlacementSnapshotPlanV2.TileSnapshotV2 tile = transaction.request.snapshotPlan()
                    .tiles().get(transaction.restoreTileCursor);
            WorldAabbV2 region = tile.effectAabb();
            long volume = region.volumeBlocks();
            if (transaction.restoreBlockCursor >= volume) {
                checkpointRestoredTile(transaction);
                transaction.restoreTileCursor--;
                transaction.restoreBlockCursor = 0L;
                submitNextRestoreSlice(transaction);
                return;
            }
            int count = Math.toIntExact(Math.min(
                    volume - transaction.restoreBlockCursor,
                    limits.maximumBlocksPerRestoreSlice()));
            List<PlacementRestoreSliceV2.RestoreBlockV2> blocks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int[] xyz = decodeCanonicalIndex(region, transaction.restoreBlockCursor + i);
                blocks.add(new PlacementRestoreSliceV2.RestoreBlockV2(
                        xyz[0],
                        xyz[1],
                        xyz[2],
                        transaction.baseline.get(new Coord(xyz[0], xyz[1], xyz[2]))));
            }
            PlacementRestoreSliceV2 slice = new PlacementRestoreSliceV2(
                    PlacementRestoreSliceV2.GATEWAY_CONTRACT_VERSION,
                    transaction.request.placementPlan().placementId(),
                    transaction.request.placementPlan().operationId(),
                    transaction.request.placementPlan().target().worldId(),
                    tile.tileId(),
                    tile.tileIndex(),
                    transaction.restoreSliceSequence,
                    region,
                    blocks);
            CompletionStage<PlacementRestoreSliceReceiptV2> observed;
            try {
                observed = Objects.requireNonNull(gateway.restoreBlockSlice(slice), "restore stage");
            } catch (Throwable failure) {
                fail(transaction, new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.RESTORE_GATEWAY_FAILURE,
                        "gateway failed during restore slice submission",
                        true,
                        failure));
                return;
            }
            observed.whenComplete((receipt, failure) -> dispatchContinuation(transaction, () -> {
                if (failure != null) {
                    fail(transaction, new PlacementRollbackExceptionV2(
                            PlacementRollbackFailureCodeV2.RESTORE_GATEWAY_FAILURE,
                            "scheduler-accepted restore slice did not complete successfully",
                            true,
                            unwrap(failure)));
                    return;
                }
                try {
                    Objects.requireNonNull(receipt, "restore receipt").requireMatches(slice);
                } catch (RuntimeException invalid) {
                    fail(transaction, new PlacementRollbackExceptionV2(
                            PlacementRollbackFailureCodeV2.RESTORE_RECEIPT_INVALID,
                            "restore slice receipt is invalid",
                            true,
                            invalid));
                    return;
                }
                transaction.restoreBlockCursor += slice.blocks().size();
                transaction.restoreSliceSequence++;
                transaction.restoredBlocks = Math.addExact(
                        transaction.restoredBlocks, slice.blocks().size());
                submitNextRestoreSlice(transaction);
            }));
        } catch (Throwable failure) {
            fail(transaction, normalize(failure));
        }
    }

    private void checkpointRestoredTile(Transaction transaction) {
        List<PlacementJournalV2.PlacementTileEntryV2> entries =
                new ArrayList<>(transaction.currentJournal.tiles());
        PlacementJournalV2.PlacementTileEntryV2 entry =
                entries.get(transaction.restoreTileCursor);
        entries.set(transaction.restoreTileCursor, new PlacementJournalV2.PlacementTileEntryV2(
                entry.tileId(),
                entry.tileIndex(),
                PlacementTileStateV2.RESTORED,
                entry.snapshotFile(),
                entry.snapshotChecksum()));
        PlacementJournalV2 checkpoint = sealJournal(
                transaction.currentJournal,
                PlacementJournalStateV2.ROLLING_BACK,
                entries,
                "Release 2 rollback restored tile " + entry.tileId()
                        + " (reverse canonical order)");
        saveJournal(checkpoint);
        transaction.currentJournal = checkpoint;
        transaction.restoredTiles++;
    }

    private void beginSettle(Transaction transaction) {
        transaction.settleStartedAtMillis = clock.millis();
        submitSettleTick(transaction, 0);
    }

    private void submitSettleTick(Transaction transaction, int tickIndex) {
        PlacementSettleTickV2 tick = new PlacementSettleTickV2(
                transaction.request.placementPlan().operationId(),
                transaction.request.placementPlan().target().worldId(),
                transaction.request.envelopePlan().unionEffectEnvelope(),
                tickIndex);
        CompletionStage<PlacementSettleTickReceiptV2> observed;
        try {
            observed = Objects.requireNonNull(gateway.advanceSettleTick(tick), "settle stage");
        } catch (Throwable failure) {
            fail(transaction, new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.GATEWAY_FAILURE,
                    "gateway failed during rollback settle tick submission",
                    true,
                    failure));
            return;
        }
        observed.whenComplete((receipt, failure) -> dispatchContinuation(transaction, () -> {
            if (failure != null) {
                fail(transaction, new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.GATEWAY_FAILURE,
                        "scheduler-accepted rollback settle tick did not complete successfully",
                        true,
                        unwrap(failure)));
                return;
            }
            try {
                Objects.requireNonNull(receipt, "settle receipt").requireMatches(tick);
            } catch (RuntimeException invalid) {
                fail(transaction, new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.GATEWAY_RECEIPT_INVALID,
                        "rollback settle tick receipt is invalid",
                        true,
                        invalid));
                return;
            }
            continueSettle(transaction, receipt);
        }));
    }

    private void continueSettle(Transaction transaction, PlacementSettleTickReceiptV2 receipt) {
        try {
            requireOpen(transaction, true);
            transaction.settleTicks++;
            if (receipt.updatesOutsideEnvelope() > 0) {
                throw new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.SETTLE_OUT_OF_ENVELOPE_UPDATE,
                        "rollback settle observed block updates outside the effect envelope",
                        true);
            }
            long elapsed = Math.subtractExact(clock.millis(), transaction.settleStartedAtMillis);
            if (elapsed > transaction.request.policy().settleTimeoutMillis()) {
                throw new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.SETTLE_TIMEOUT,
                        "bounded rollback settle exceeded the policy timeout before quiescence",
                        true);
            }
            if (receipt.updatesInsideEnvelope() == 0) {
                transaction.quiescentTicks++;
            } else {
                transaction.quiescentTicks = 0;
            }
            if (transaction.quiescentTicks >= transaction.request.policy().quiescenceTicks()) {
                beginVerify(transaction);
                return;
            }
            if (transaction.settleTicks >= transaction.request.policy().maximumSettleTicks()) {
                throw new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.SETTLE_TIMEOUT,
                        "bounded rollback settle exhausted maximumSettleTicks before quiescence",
                        true);
            }
            submitSettleTick(transaction, transaction.settleTicks);
        } catch (Throwable failure) {
            fail(transaction, normalize(failure));
        }
    }

    private void beginVerify(Transaction transaction) {
        transaction.verifyCursor = 0L;
        transaction.verifySliceSequence = 0;
        transaction.observedDigest = sha256();
        submitVerifySlice(transaction);
    }

    private void submitVerifySlice(Transaction transaction) {
        WorldAabbV2 envelope = transaction.request.envelopePlan().unionEffectEnvelope();
        long remaining = Math.subtractExact(envelope.volumeBlocks(), transaction.verifyCursor);
        if (remaining == 0L) {
            finishVerify(transaction);
            return;
        }
        if (transaction.verifySliceSequence
                >= transaction.request.policy().maximumQueuedVerifySlices()) {
            fail(transaction, new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.VERIFY_SLICE_BUDGET,
                    "rollback verify slice budget exhausted before the envelope was fully scanned",
                    true));
            return;
        }
        int count = Math.toIntExact(Math.min(
                remaining, transaction.request.policy().maximumBlocksPerVerifySlice()));
        PlacementVerifyReadSliceV2 slice = new PlacementVerifyReadSliceV2(
                transaction.request.placementPlan().operationId(),
                transaction.request.placementPlan().target().worldId(),
                envelope,
                transaction.verifyCursor,
                count,
                transaction.verifySliceSequence);
        CompletionStage<PlacementVerifyReadSliceReceiptV2> observed;
        try {
            observed = Objects.requireNonNull(gateway.readVerifySlice(slice), "verify stage");
        } catch (Throwable failure) {
            fail(transaction, new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.GATEWAY_FAILURE,
                    "gateway failed during rollback verify-read submission",
                    true,
                    failure));
            return;
        }
        observed.whenComplete((receipt, failure) -> dispatchContinuation(transaction, () -> {
            if (failure != null) {
                fail(transaction, new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.GATEWAY_FAILURE,
                        "scheduler-accepted rollback verify-read did not complete successfully",
                        true,
                        unwrap(failure)));
                return;
            }
            try {
                Objects.requireNonNull(receipt, "verify receipt").requireMatches(slice);
            } catch (RuntimeException invalid) {
                fail(transaction, new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.GATEWAY_RECEIPT_INVALID,
                        "rollback verify-read receipt is invalid",
                        true,
                        invalid));
                return;
            }
            continueVerify(transaction, slice, receipt);
        }));
    }

    private void continueVerify(
            Transaction transaction,
            PlacementVerifyReadSliceV2 slice,
            PlacementVerifyReadSliceReceiptV2 receipt
    ) {
        try {
            requireOpen(transaction, true);
            WorldAabbV2 envelope = slice.effectEnvelope();
            for (int i = 0; i < receipt.blockStates().size(); i++) {
                long index = Math.addExact(slice.startIndex(), i);
                int[] xyz = decodeCanonicalIndex(envelope, index);
                String expected = transaction.baseline.get(new Coord(xyz[0], xyz[1], xyz[2]));
                String observedState = receipt.blockStates().get(i);
                transaction.observedDigest.update(observedState.getBytes(StandardCharsets.UTF_8));
                transaction.observedDigest.update((byte) '\n');
                if (!Objects.equals(expected, observedState)) {
                    throw new PlacementRollbackExceptionV2(
                            PlacementRollbackFailureCodeV2.VERIFY_MISMATCH,
                            "rollback exact verify mismatch against the snapshot baseline at ("
                                    + xyz[0] + "," + xyz[1] + "," + xyz[2] + ")",
                            true);
                }
                transaction.scannedBlocks++;
            }
            transaction.verifyCursor = Math.addExact(transaction.verifyCursor, slice.blockCount());
            transaction.verifySliceSequence++;
            transaction.verifySlices++;
            submitVerifySlice(transaction);
        } catch (Throwable failure) {
            fail(transaction, normalize(failure));
        }
    }

    private void finishVerify(Transaction transaction) {
        try {
            String observedChecksum = HexFormat.of().formatHex(transaction.observedDigest.digest());
            if (!transaction.baselineChecksum.equals(observedChecksum)
                    || transaction.scannedBlocks
                    != transaction.request.envelopePlan().unionEffectEnvelope().volumeBlocks()) {
                throw new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.VERIFY_MISMATCH,
                        "rollback verify stream checksum mismatch against the snapshot baseline",
                        true);
            }
            PlacementJournalV2 rolledBack = sealJournal(
                    transaction.currentJournal,
                    PlacementJournalStateV2.ROLLED_BACK,
                    transaction.currentJournal.tiles(),
                    "Release 2 rollback restored, settled, and full-verified the effect envelope");
            saveJournal(rolledBack);
            transaction.currentJournal = rolledBack;
            try {
                safetyStore.release(transaction.request.placementPlan().placementId());
            } catch (IOException | RuntimeException releaseFailure) {
                throw new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.RESERVATION_RELEASE_FAILED,
                        "rollback sealed ROLLED_BACK but the reservation lease release failed",
                        true,
                        releaseFailure);
            }
            complete(transaction, new RollbackResultV2(
                    transaction.request.failedJournal(),
                    rolledBack,
                    transaction.restoredTiles,
                    transaction.restoreSliceSequence,
                    transaction.restoredBlocks,
                    transaction.settleTicks,
                    transaction.verifySlices,
                    transaction.scannedBlocks,
                    transaction.baselineChecksum,
                    observedChecksum));
        } catch (Throwable failure) {
            fail(transaction, normalize(failure));
        }
    }

    private void requireOpen(Transaction transaction, boolean mutationMayHaveOccurred) {
        if (!accepting.get()) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.ROLLBACK_SHUTDOWN,
                    "rollback service shutdown while a rollback was in flight",
                    mutationMayHaveOccurred);
        }
        if (transaction.request.cancellation().isCancellationRequested()) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.CANCELLED,
                    "rollback cancelled while a rollback was in flight",
                    mutationMayHaveOccurred);
        }
    }

    private void dispatchContinuation(Transaction transaction, Runnable continuation) {
        if (transaction.finished.get()) {
            return;
        }
        try {
            workers.execute(continuation);
        } catch (RejectedExecutionException rejected) {
            fail(transaction, new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.ROLLBACK_SHUTDOWN,
                    "rollback worker rejected a late gateway completion",
                    true,
                    rejected));
        }
    }

    private void fail(Transaction transaction, PlacementRollbackExceptionV2 failure) {
        if (!transaction.finished.compareAndSet(false, true)) {
            return;
        }
        PlacementRollbackExceptionV2 reported = failure;
        if (transaction.currentJournal != null
                && transaction.currentJournal.state() != PlacementJournalStateV2.ROLLED_BACK) {
            try {
                PlacementJournalV2 recovery = sealJournal(
                        transaction.currentJournal,
                        PlacementJournalStateV2.RECOVERY_REQUIRED,
                        transaction.currentJournal.tiles(),
                        "Release 2 rollback requires recovery after " + failure.code());
                saveJournal(recovery);
                transaction.currentJournal = recovery;
            } catch (Throwable persistenceFailure) {
                failure.addSuppressed(persistenceFailure);
                reported = new PlacementRollbackExceptionV2(
                        PlacementRollbackFailureCodeV2.JOURNAL_PERSISTENCE_FAILED,
                        "rollback failed and RECOVERY_REQUIRED could not be persisted",
                        true,
                        failure);
            }
        }
        transaction.result.completeExceptionally(reported);
        finishAdmission(transaction);
    }

    private void complete(Transaction transaction, RollbackResultV2 value) {
        if (!transaction.finished.compareAndSet(false, true)) {
            return;
        }
        transaction.result.complete(value);
        finishAdmission(transaction);
    }

    private void finishAdmission(Transaction transaction) {
        if (!transaction.admissionReleased.compareAndSet(false, true)) {
            return;
        }
        activeOperations.remove(transaction.request.placementPlan().operationId());
        admissions.release();
        if (activeCount.decrementAndGet() == 0 && !accepting.get()) {
            workers.shutdown();
            termination.complete(null);
        }
    }

    private PlacementJournalV2 sealJournal(
            PlacementJournalV2 basis,
            PlacementJournalStateV2 state,
            List<PlacementJournalV2.PlacementTileEntryV2> entries,
            String message
    ) {
        return codec.sealPlacementJournal(new PlacementJournalV2(
                PlacementJournalV2.VERSION,
                PlacementJournalV2.JOURNAL_CONTRACT_VERSION,
                basis.plan(),
                basis.planChecksum(),
                state,
                entries,
                basis.reservedBytes(),
                basis.snapshotBytesUsed(),
                clock.instant().toString(),
                message,
                PlacementPlanV2.UNBOUND_CHECKSUM));
    }

    private void saveJournal(PlacementJournalV2 journal) {
        try {
            journalStore.save(journal);
        } catch (IOException | RuntimeException exception) {
            throw new PlacementRollbackExceptionV2(
                    PlacementRollbackFailureCodeV2.JOURNAL_PERSISTENCE_FAILED,
                    "Release 2 placement journal persistence failed during rollback",
                    true,
                    exception);
        }
    }

    private static PlacementRollbackFailureCodeV2 classifySnapshotFailure(
            PlacementSnapshotExceptionV2 failure
    ) {
        return switch (failure.failureCode()) {
            case STATE_MISMATCH -> PlacementRollbackFailureCodeV2.SNAPSHOT_MISSING;
            case BINDING_MISMATCH -> PlacementRollbackFailureCodeV2.BINDING_MISMATCH;
            case CANCELLED -> PlacementRollbackFailureCodeV2.CANCELLED;
            default -> PlacementRollbackFailureCodeV2.SNAPSHOT_TAMPERED;
        };
    }

    static int[] decodeCanonicalIndex(WorldAabbV2 region, long index) {
        long width = (long) region.maxX() - region.minX() + 1L;
        long length = (long) region.maxZ() - region.minZ() + 1L;
        long plane = Math.multiplyExact(width, length);
        long yOffset = index / plane;
        long rem = index % plane;
        long zOffset = rem / width;
        long xOffset = rem % width;
        return new int[] {
                Math.toIntExact(region.minX() + xOffset),
                Math.toIntExact(region.minY() + yOffset),
                Math.toIntExact(region.minZ() + zOffset)
        };
    }

    private static PlacementRollbackExceptionV2 normalize(Throwable failure) {
        Throwable actual = unwrap(failure);
        if (actual instanceof PlacementRollbackExceptionV2 known) {
            return known;
        }
        return new PlacementRollbackExceptionV2(
                PlacementRollbackFailureCodeV2.STATE_MISMATCH,
                "Release 2 rollback orchestration failed",
                true,
                actual);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.<T>failedFuture(failure).minimalCompletionStage();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** Terminal rollback evidence returned only after ROLLED_BACK was sealed and the lease freed. */
    public record RollbackResultV2(
            PlacementJournalV2 failedJournal,
            PlacementJournalV2 rolledBackJournal,
            int restoredTiles,
            long restoreSlices,
            long restoredBlocks,
            int settleTicks,
            int verifySlices,
            long scannedBlocks,
            String baselineChecksum,
            String observedChecksum
    ) {
        public RollbackResultV2 {
            Objects.requireNonNull(failedJournal, "failedJournal");
            Objects.requireNonNull(rolledBackJournal, "rolledBackJournal");
            Objects.requireNonNull(baselineChecksum, "baselineChecksum");
            Objects.requireNonNull(observedChecksum, "observedChecksum");
            if (restoredTiles < 1 || restoreSlices < 1 || restoredBlocks < 1
                    || settleTicks < 0 || verifySlices < 0 || scannedBlocks < 0) {
                throw new IllegalArgumentException("rollback result counts out of range");
            }
        }
    }

    private record Coord(int x, int y, int z) {
    }

    private static final class Transaction {
        private final PlacementRollbackRequestV2 request;
        private final CompletableFuture<RollbackResultV2> result = new CompletableFuture<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean admissionReleased = new AtomicBoolean();
        private PlacementJournalV2 currentJournal;
        private Map<Coord, String> baseline;
        private String baselineChecksum;
        private int restoreTileCursor;
        private long restoreBlockCursor;
        private long restoreSliceSequence;
        private long restoredBlocks;
        private int restoredTiles;
        private long settleStartedAtMillis;
        private int settleTicks;
        private int quiescentTicks;
        private long verifyCursor;
        private int verifySliceSequence;
        private int verifySlices;
        private long scannedBlocks;
        private MessageDigest observedDigest;

        private Transaction(PlacementRollbackRequestV2 request) {
            this.request = request;
        }
    }

    private static final class RollbackWorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(
                    runnable,
                    "lfc-placement-rollback-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
