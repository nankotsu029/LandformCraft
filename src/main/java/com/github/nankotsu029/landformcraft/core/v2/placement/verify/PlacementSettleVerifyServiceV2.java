package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementVerifyEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementJournalStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
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
 * Release 2 bounded settle and full effect-envelope verify (V2-6-07).
 *
 * <p>Starts only from an apply-complete journal ({@code APPLYING} with every tile {@code APPLIED}),
 * advances {@code SETTLING → VERIFYING → APPLIED}, and seals {@link PlacementVerifyEvidenceV2}
 * only after an exact X→Z→Y stream match. Tile checkpoints alone never count as success.</p>
 */
public final class PlacementSettleVerifyServiceV2 implements AutoCloseable {
    private final PlacementWorldGatewayV2 gateway;
    private final PlacementJournalStoreV2 journalStore;
    private final Clock clock;
    private final PlacementSettleVerifyLimitsV2 limits;
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final ThreadPoolExecutor workers;
    private final Semaphore admissions;
    private final ConcurrentHashMap<UUID, Boolean> activeOperations = new ConcurrentHashMap<>();
    private final AtomicInteger activeCount = new AtomicInteger();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final CompletableFuture<Void> termination = new CompletableFuture<>();

    public PlacementSettleVerifyServiceV2(
            PlacementWorldGatewayV2 gateway,
            PlacementJournalStoreV2 journalStore,
            Clock clock
    ) {
        this(gateway, journalStore, clock, PlacementSettleVerifyLimitsV2.defaults());
    }

    public PlacementSettleVerifyServiceV2(
            PlacementWorldGatewayV2 gateway,
            PlacementJournalStoreV2 journalStore,
            Clock clock,
            PlacementSettleVerifyLimitsV2 limits
    ) {
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
                new SettleVerifyWorkerThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.admissions = new Semaphore(
                Math.addExact(limits.workerThreads(), limits.maximumQueuedTransactions()), true);
    }

    public CompletionStage<VerifyResultV2> settleAndVerify(PlacementSettleVerifyRequestV2 request) {
        Objects.requireNonNull(request, "request");
        if (!accepting.get()) {
            return failed(new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.SERVICE_CLOSED,
                    "Release 2 settle/verify service is closed",
                    true));
        }
        if (!admissions.tryAcquire()) {
            return failed(new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.QUEUE_SATURATED,
                    "Release 2 settle/verify queue is saturated",
                    true));
        }
        UUID operationId = request.placementPlan().operationId();
        if (activeOperations.putIfAbsent(operationId, Boolean.TRUE) != null) {
            admissions.release();
            return failed(new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.STATE_MISMATCH,
                    "Release 2 settle/verify operation is already active",
                    true));
        }
        Transaction transaction = new Transaction(request);
        activeCount.incrementAndGet();
        try {
            workers.execute(() -> start(transaction));
        } catch (RejectedExecutionException rejected) {
            finishAdmission(transaction);
            return failed(new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.QUEUE_SATURATED,
                    "Release 2 settle/verify worker queue rejected the transaction",
                    true,
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
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.SERVICE_CLOSED,
                        "settle/verify service closed before admission",
                        true);
            }
            preflight(transaction);
            transaction.expected = new PlacementExpectedBlockResolverV2(
                    transaction.request.placementPlan(),
                    transaction.request.envelopePlan(),
                    transaction.request.blockSource(),
                    transaction.request.snapshotBaseline());
            transaction.expectedChecksum = transaction.expected.streamChecksum();
            PlacementJournalV2 settling = sealJournal(
                    transaction.request.applyCompleteJournal(),
                    PlacementJournalStateV2.SETTLING,
                    transaction.request.applyCompleteJournal().tiles(),
                    "Release 2 bounded settle started; tile checkpoints are not final success");
            saveJournal(settling);
            transaction.currentJournal = settling;
            transaction.settleStartedAtMillis = clock.millis();
            submitSettleTick(transaction, 0);
        } catch (Throwable failure) {
            fail(transaction, normalize(failure));
        }
    }

    private void preflight(Transaction transaction) {
        PlacementSettleVerifyRequestV2 request = transaction.request;
        PlacementSettleVerifyPolicyV2 policy = request.policy();
        if (!PlacementSettleVerifyPolicyV2.POLICY_VERSION.equals(policy.policyVersion())) {
            throw new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.UNKNOWN_POLICY,
                    "unknown settle/verify policy version",
                    true);
        }
        PlacementJournalV2 journal = request.applyCompleteJournal();
        if (journal.state() != PlacementJournalStateV2.APPLYING) {
            throw new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.STATE_MISMATCH,
                    "settle/verify requires APPLYING journal with all tiles APPLIED",
                    true);
        }
        if (journal.tiles().stream().anyMatch(tile -> tile.state() != PlacementTileStateV2.APPLIED)) {
            throw new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.TILE_CHECKPOINT_INSUFFICIENT,
                    "settle/verify rejects incomplete APPLIED tile prefix as success",
                    true);
        }
        if (!journal.planChecksum().equals(request.placementPlan().canonicalChecksum())
                || !journal.plan().canonicalChecksum().equals(request.placementPlan().canonicalChecksum())) {
            throw new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.BINDING_MISMATCH,
                    "settle/verify journal plan binding mismatch",
                    true);
        }
        request.containmentEvidence().requireBindings(
                request.placementPlan(), request.envelopePlan(), request.snapshotPlan());
        long volume = request.envelopePlan().unionEffectEnvelope().volumeBlocks();
        if (volume > policy.maximumEffectEnvelopeBlocks()
                || volume > policy.budget().maximumScannedBlocks()) {
            throw new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.RESOURCE_BUDGET_EXCEEDED,
                    "effect envelope exceeds settle/verify scan budget",
                    true);
        }
        if (request.cancellation().isCancellationRequested()) {
            throw new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.CANCELLED,
                    "settle/verify cancelled before the first settle tick",
                    true);
        }
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
            fail(transaction, new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.GATEWAY_FAILURE,
                    "gateway failed during settle tick submission",
                    true,
                    failure));
            return;
        }
        observed.whenComplete((receipt, failure) -> dispatchContinuation(transaction, () -> {
            if (failure != null) {
                fail(transaction, new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.GATEWAY_FAILURE,
                        "scheduler-accepted settle tick did not complete successfully",
                        true,
                        unwrap(failure)));
                return;
            }
            try {
                Objects.requireNonNull(receipt, "settle receipt").requireMatches(tick);
            } catch (RuntimeException invalid) {
                fail(transaction, new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.GATEWAY_RECEIPT_INVALID,
                        "settle tick receipt is invalid",
                        true,
                        invalid));
                return;
            }
            continueSettle(transaction, receipt);
        }));
    }

    private void continueSettle(Transaction transaction, PlacementSettleTickReceiptV2 receipt) {
        try {
            if (!accepting.get()) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.SETTLE_SHUTDOWN,
                        "settle/verify service shutdown during settle",
                        true);
            }
            if (transaction.request.cancellation().isCancellationRequested()) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.CANCELLED,
                        "settle/verify cancelled during settle",
                        true);
            }
            transaction.settleTicks++;
            transaction.updatesInsideEnvelope = Math.addExact(
                    transaction.updatesInsideEnvelope, receipt.updatesInsideEnvelope());
            transaction.updatesOutsideEnvelope = Math.addExact(
                    transaction.updatesOutsideEnvelope, receipt.updatesOutsideEnvelope());
            if (receipt.updatesOutsideEnvelope() > 0) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.SETTLE_OUT_OF_ENVELOPE_UPDATE,
                        "settle observed block updates outside the effect envelope",
                        true);
            }
            long elapsed = Math.subtractExact(clock.millis(), transaction.settleStartedAtMillis);
            if (elapsed > transaction.request.policy().settleTimeoutMillis()) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.SETTLE_TIMEOUT,
                        "bounded settle exceeded the policy timeout before quiescence",
                        true);
            }
            if (receipt.updatesInsideEnvelope() == 0) {
                transaction.quiescentTicks++;
            } else {
                transaction.quiescentTicks = 0;
            }
            if (transaction.quiescentTicks >= transaction.request.policy().quiescenceTicks()) {
                beginVerify(transaction, elapsed);
                return;
            }
            if (transaction.settleTicks >= transaction.request.policy().maximumSettleTicks()) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.SETTLE_TIMEOUT,
                        "bounded settle exhausted maximumSettleTicks before quiescence",
                        true);
            }
            submitSettleTick(transaction, transaction.settleTicks);
        } catch (Throwable failure) {
            fail(transaction, normalize(failure));
        }
    }

    private void beginVerify(Transaction transaction, long settleElapsedMillis) {
        transaction.settleElapsedMillis = settleElapsedMillis;
        PlacementJournalV2 verifying = sealJournal(
                transaction.currentJournal,
                PlacementJournalStateV2.VERIFYING,
                transaction.currentJournal.tiles(),
                "Release 2 full effect-envelope verify started");
        saveJournal(verifying);
        transaction.currentJournal = verifying;
        transaction.verifyCursor = 0L;
        transaction.sliceSequence = 0;
        transaction.observedDigest = sha256();
        transaction.continuity = new ContinuityAccumulator(transaction.request.envelopePlan());
        submitVerifySlice(transaction);
    }

    private void submitVerifySlice(Transaction transaction) {
        WorldAabbV2 envelope = transaction.request.envelopePlan().unionEffectEnvelope();
        long remaining = Math.subtractExact(envelope.volumeBlocks(), transaction.verifyCursor);
        if (remaining == 0L) {
            finishVerify(transaction);
            return;
        }
        int count = Math.toIntExact(Math.min(
                remaining, transaction.request.policy().maximumBlocksPerVerifySlice()));
        if (transaction.sliceSequence >= transaction.request.policy().maximumQueuedVerifySlices()) {
            fail(transaction, new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.VERIFY_SLICE_BUDGET,
                    "verify slice budget exhausted before the effect envelope was fully scanned",
                    true));
            return;
        }
        PlacementVerifyReadSliceV2 slice = new PlacementVerifyReadSliceV2(
                transaction.request.placementPlan().operationId(),
                transaction.request.placementPlan().target().worldId(),
                envelope,
                transaction.verifyCursor,
                count,
                transaction.sliceSequence);
        CompletionStage<PlacementVerifyReadSliceReceiptV2> observed;
        try {
            observed = Objects.requireNonNull(gateway.readVerifySlice(slice), "verify stage");
        } catch (Throwable failure) {
            fail(transaction, new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.GATEWAY_FAILURE,
                    "gateway failed during verify-read submission",
                    true,
                    failure));
            return;
        }
        observed.whenComplete((receipt, failure) -> dispatchContinuation(transaction, () -> {
            if (failure != null) {
                fail(transaction, new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.GATEWAY_FAILURE,
                        "scheduler-accepted verify-read did not complete successfully",
                        true,
                        unwrap(failure)));
                return;
            }
            try {
                Objects.requireNonNull(receipt, "verify receipt").requireMatches(slice);
            } catch (RuntimeException invalid) {
                fail(transaction, new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.GATEWAY_RECEIPT_INVALID,
                        "verify-read receipt is invalid",
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
            if (!accepting.get()) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.SETTLE_SHUTDOWN,
                        "settle/verify service shutdown during verify",
                        true);
            }
            if (transaction.request.cancellation().isCancellationRequested()) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.CANCELLED,
                        "settle/verify cancelled during verify",
                        true);
            }
            WorldAabbV2 envelope = slice.effectEnvelope();
            for (int i = 0; i < receipt.blockStates().size(); i++) {
                long index = Math.addExact(slice.startIndex(), i);
                int[] xyz = decodeCanonicalIndex(envelope, index);
                String expected = transaction.expected.expectedAt(xyz[0], xyz[1], xyz[2]);
                String observed = receipt.blockStates().get(i);
                transaction.observedDigest.update(observed.getBytes(StandardCharsets.UTF_8));
                transaction.observedDigest.update((byte) '\n');
                if (!expected.equals(observed)) {
                    transaction.mismatchCount++;
                    throw new PlacementVerifyExceptionV2(
                            PlacementVerifyFailureCodeV2.VERIFY_MISMATCH,
                            "effect envelope exact verify mismatch at ("
                                    + xyz[0] + "," + xyz[1] + "," + xyz[2] + "): expected="
                                    + expected + " observed=" + observed,
                            true);
                }
                transaction.continuity.accept(
                        xyz[0],
                        xyz[1],
                        xyz[2],
                        expected,
                        transaction.expected.overlayOrdinalAt(xyz[0], xyz[1], xyz[2]),
                        transaction.request.envelopePlan());
                transaction.scannedBlocks++;
            }
            transaction.verifyCursor = Math.addExact(transaction.verifyCursor, slice.blockCount());
            transaction.sliceSequence++;
            transaction.verifySlices++;
            submitVerifySlice(transaction);
        } catch (Throwable failure) {
            fail(transaction, normalize(failure));
        }
    }

    private void finishVerify(Transaction transaction) {
        try {
            String observedChecksum = HexFormat.of().formatHex(transaction.observedDigest.digest());
            if (!transaction.expectedChecksum.equals(observedChecksum)
                    || transaction.mismatchCount != 0
                    || transaction.scannedBlocks != transaction.expected.volumeBlocks()) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.VERIFY_MISMATCH,
                        "effect envelope exact verify stream checksum mismatch",
                        true);
            }
            List<PlacementVerifyEvidenceV2.ContinuityMetricV2> metrics =
                    transaction.continuity.seal(transaction.request.policy());
            PlacementVerifyEvidenceV2 evidence = codec.sealPlacementVerifyEvidence(
                    new PlacementVerifyEvidenceV2(
                            PlacementVerifyEvidenceV2.VERSION,
                            PlacementVerifyEvidenceV2.VERIFY_CONTRACT_VERSION,
                            transaction.request.policy(),
                            transaction.request.placementPlan().placementId(),
                            transaction.request.placementPlan().operationId(),
                            transaction.request.placementPlan().target().worldId(),
                            new PlacementVerifyEvidenceV2.PlacementPlanBinding(
                                    PlacementVerifyEvidenceV2.PlacementPlanBinding.VERSION,
                                    transaction.request.placementPlan().canonicalChecksum(),
                                    PlacementVerifyEvidenceV2.PlacementPlanBinding.CONTRACT_VERSION),
                            new PlacementVerifyEvidenceV2.EnvelopeBinding(
                                    PlacementVerifyEvidenceV2.EnvelopeBinding.VERSION,
                                    transaction.request.envelopePlan().canonicalChecksum(),
                                    transaction.request.envelopePlan().mutationEnvelopeChecksum(),
                                    PlacementVerifyEvidenceV2.EnvelopeBinding.CONTRACT_VERSION),
                            new PlacementVerifyEvidenceV2.SnapshotBinding(
                                    PlacementVerifyEvidenceV2.SnapshotBinding.VERSION,
                                    transaction.request.snapshotPlan().canonicalChecksum(),
                                    PlacementVerifyEvidenceV2.SnapshotBinding.CONTRACT_VERSION),
                            new PlacementVerifyEvidenceV2.JournalBinding(
                                    PlacementVerifyEvidenceV2.JournalBinding.VERSION,
                                    transaction.request.applyCompleteJournal().journalChecksum(),
                                    PlacementVerifyEvidenceV2.JournalBinding.CONTRACT_VERSION),
                            transaction.request.envelopePlan().unionEffectEnvelope(),
                            transaction.expectedChecksum,
                            observedChecksum,
                            metrics,
                            new PlacementVerifyEvidenceV2.SettleStats(
                                    transaction.settleTicks,
                                    transaction.quiescentTicks,
                                    transaction.updatesInsideEnvelope,
                                    transaction.updatesOutsideEnvelope,
                                    transaction.settleElapsedMillis),
                            new PlacementVerifyEvidenceV2.ScanStats(
                                    transaction.scannedBlocks,
                                    transaction.verifySlices,
                                    0,
                                    transaction.continuity.tileSeamSamples()),
                            PlacementVerifyEvidenceV2.Verdict.VERIFIED,
                            clock.instant().toString(),
                            PlacementPlanV2.UNBOUND_CHECKSUM));
            evidence.requireBindings(
                    transaction.request.placementPlan(),
                    transaction.request.envelopePlan(),
                    transaction.request.snapshotPlan(),
                    transaction.request.applyCompleteJournal());

            List<PlacementJournalV2.PlacementTileEntryV2> verifiedTiles = markVerified(
                    transaction.currentJournal.tiles());
            PlacementJournalV2 applied = sealJournal(
                    transaction.currentJournal,
                    PlacementJournalStateV2.APPLIED,
                    verifiedTiles,
                    "Release 2 settle and full effect-envelope verify succeeded");
            transaction.request.verifiedCommit().commit(
                    transaction.request.applyCompleteJournal(), applied, evidence);
            saveJournal(applied);
            transaction.currentJournal = applied;
            complete(transaction, new VerifyResultV2(
                    transaction.request.applyCompleteJournal(),
                    applied,
                    evidence,
                    transaction.settleTicks,
                    transaction.verifySlices,
                    transaction.scannedBlocks));
        } catch (Throwable failure) {
            fail(transaction, normalize(failure));
        }
    }

    private void dispatchContinuation(Transaction transaction, Runnable continuation) {
        if (transaction.finished.get()) {
            return;
        }
        try {
            workers.execute(continuation);
        } catch (RejectedExecutionException rejected) {
            fail(transaction, new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.SETTLE_SHUTDOWN,
                    "settle/verify worker rejected a late gateway completion",
                    true,
                    rejected));
        }
    }

    private void fail(Transaction transaction, PlacementVerifyExceptionV2 failure) {
        if (!transaction.finished.compareAndSet(false, true)) {
            return;
        }
        PlacementVerifyExceptionV2 reported = failure;
        PlacementJournalV2 basis = transaction.currentJournal != null
                ? transaction.currentJournal : transaction.request.applyCompleteJournal();
        try {
            PlacementJournalV2 recovery = sealJournal(
                    basis,
                    PlacementJournalStateV2.RECOVERY_REQUIRED,
                    basis.tiles(),
                    "Release 2 settle/verify requires recovery after " + failure.code());
            saveJournal(recovery);
            transaction.currentJournal = recovery;
        } catch (Throwable persistenceFailure) {
            failure.addSuppressed(persistenceFailure);
            reported = new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.JOURNAL_PERSISTENCE_FAILED,
                    "settle/verify failed and RECOVERY_REQUIRED could not be persisted",
                    true,
                    failure);
        }
        transaction.result.completeExceptionally(reported);
        finishAdmission(transaction);
    }

    private void complete(Transaction transaction, VerifyResultV2 value) {
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
            throw new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.JOURNAL_PERSISTENCE_FAILED,
                    "Release 2 placement journal persistence failed during settle/verify",
                    true,
                    exception);
        }
    }

    private static List<PlacementJournalV2.PlacementTileEntryV2> markVerified(
            List<PlacementJournalV2.PlacementTileEntryV2> entries
    ) {
        List<PlacementJournalV2.PlacementTileEntryV2> updated = new ArrayList<>(entries.size());
        for (PlacementJournalV2.PlacementTileEntryV2 entry : entries) {
            if (entry.state() != PlacementTileStateV2.APPLIED) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.STATE_MISMATCH,
                        "terminal APPLIED requires every tile to start as APPLIED",
                        true);
            }
            updated.add(new PlacementJournalV2.PlacementTileEntryV2(
                    entry.tileId(),
                    entry.tileIndex(),
                    PlacementTileStateV2.VERIFIED,
                    entry.snapshotFile(),
                    entry.snapshotChecksum()));
        }
        return List.copyOf(updated);
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

    private static PlacementVerifyExceptionV2 normalize(Throwable failure) {
        Throwable actual = unwrap(failure);
        if (actual instanceof PlacementVerifyExceptionV2 known) {
            return known;
        }
        return new PlacementVerifyExceptionV2(
                PlacementVerifyFailureCodeV2.SOURCE_INVALID,
                "Release 2 settle/verify orchestration failed",
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

    public record VerifyResultV2(
            PlacementJournalV2 applyCompleteJournal,
            PlacementJournalV2 verifiedJournal,
            PlacementVerifyEvidenceV2 evidence,
            int settleTicks,
            int verifySlices,
            long scannedBlocks
    ) {
        public VerifyResultV2 {
            Objects.requireNonNull(applyCompleteJournal, "applyCompleteJournal");
            Objects.requireNonNull(verifiedJournal, "verifiedJournal");
            Objects.requireNonNull(evidence, "evidence");
            if (settleTicks < 0 || verifySlices < 0 || scannedBlocks < 0) {
                throw new IllegalArgumentException("verify result counts must be non-negative");
            }
        }
    }

    private static final class Transaction {
        private final PlacementSettleVerifyRequestV2 request;
        private final CompletableFuture<VerifyResultV2> result = new CompletableFuture<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean admissionReleased = new AtomicBoolean();
        private PlacementJournalV2 currentJournal;
        private PlacementExpectedBlockResolverV2 expected;
        private String expectedChecksum;
        private long settleStartedAtMillis;
        private int settleTicks;
        private int quiescentTicks;
        private int updatesInsideEnvelope;
        private int updatesOutsideEnvelope;
        private long settleElapsedMillis;
        private long verifyCursor;
        private int sliceSequence;
        private int verifySlices;
        private long scannedBlocks;
        private int mismatchCount;
        private MessageDigest observedDigest;
        private ContinuityAccumulator continuity;

        private Transaction(PlacementSettleVerifyRequestV2 request) {
            this.request = request;
        }
    }

    private static final class ContinuityAccumulator {
        private final EnumMap<PlacementVerifyEvidenceV2.ContinuityRuleV2, long[]> counts =
                new EnumMap<>(PlacementVerifyEvidenceV2.ContinuityRuleV2.class);
        private int tileSeamSamples;
        private final List<WorldAabbV2> mutationRegions = new ArrayList<>();

        private ContinuityAccumulator(PlacementEnvelopePlanV2 envelope) {
            for (PlacementVerifyEvidenceV2.ContinuityRuleV2 rule
                    : PlacementVerifyEvidenceV2.ContinuityRuleV2.values()) {
                counts.put(rule, new long[] {0L, 0L});
            }
            for (PlacementEnvelopePlanV2.TileEnvelopeV2 tile : envelope.tiles()) {
                mutationRegions.add(tile.mutationAabb());
            }
        }

        private void accept(
                int x,
                int y,
                int z,
                String state,
                Integer overlayOrdinal,
                PlacementEnvelopePlanV2 envelope
        ) {
            // Vertical underwater / underground fluid columns.
            if (isFluid(state)) {
                examine(PlacementVerifyEvidenceV2.ContinuityRuleV2.MARINE_UNDERWATER_COLUMN,
                        y > envelope.unionEffectEnvelope().minY(),
                        true);
                examine(PlacementVerifyEvidenceV2.ContinuityRuleV2.UNDERGROUND_FLUID,
                        y < envelope.unionMutationEnvelope().maxY(),
                        true);
            }
            // Surface foundation and volume entrance samples along +X tile seams.
            for (int i = 0; i + 1 < mutationRegions.size(); i++) {
                WorldAabbV2 left = mutationRegions.get(i);
                WorldAabbV2 right = mutationRegions.get(i + 1);
                if (left.maxX() + 1 == right.minX()
                        && x == left.maxX()
                        && z >= Math.max(left.minZ(), right.minZ())
                        && z <= Math.min(left.maxZ(), right.maxZ())
                        && y >= Math.max(left.minY(), right.minY())
                        && y <= Math.min(left.maxY(), right.maxY())) {
                    tileSeamSamples++;
                    boolean solid = isSolid(state);
                    boolean air = isAir(state);
                    examine(PlacementVerifyEvidenceV2.ContinuityRuleV2.SURFACE_FOUNDATION, solid, solid);
                    examine(PlacementVerifyEvidenceV2.ContinuityRuleV2.SURFACE_VOLUME_ENTRANCE,
                            air || solid, true);
                    examine(PlacementVerifyEvidenceV2.ContinuityRuleV2.OVERLAY_CONTINUITY,
                            overlayOrdinal != null, overlayOrdinal != null);
                }
            }
        }

        private void examine(
                PlacementVerifyEvidenceV2.ContinuityRuleV2 rule,
                boolean examined,
                boolean continuous
        ) {
            if (!examined) {
                return;
            }
            long[] pair = counts.get(rule);
            pair[0]++;
            if (continuous) {
                pair[1]++;
            }
        }

        private int tileSeamSamples() {
            return tileSeamSamples;
        }

        private List<PlacementVerifyEvidenceV2.ContinuityMetricV2> seal(
                PlacementSettleVerifyPolicyV2 policy
        ) {
            List<PlacementVerifyEvidenceV2.ContinuityMetricV2> metrics = new ArrayList<>();
            for (PlacementVerifyEvidenceV2.ContinuityRuleV2 rule
                    : PlacementVerifyEvidenceV2.ContinuityRuleV2.values()) {
                long[] pair = counts.get(rule);
                String detail = rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                        + " continuity across effect envelope";
                String hash = HexFormat.of().formatHex(sha256().digest(
                        (rule.name() + ":" + pair[0] + ":" + pair[1]).getBytes(StandardCharsets.UTF_8)));
                metrics.add(new PlacementVerifyEvidenceV2.ContinuityMetricV2(
                        rule, pair[0], pair[1], detail, hash));
            }
            if (metrics.size() > policy.budget().maximumContinuityFindings()) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.RESOURCE_BUDGET_EXCEEDED,
                        "continuity metrics exceed settle/verify budget",
                        true);
            }
            return List.copyOf(metrics);
        }

        private static boolean isFluid(String state) {
            return state.startsWith("minecraft:water") || state.startsWith("minecraft:lava");
        }

        private static boolean isAir(String state) {
            return state.equals("minecraft:air") || state.equals("minecraft:cave_air");
        }

        private static boolean isSolid(String state) {
            return !isFluid(state) && !isAir(state);
        }
    }

    private static final class SettleVerifyWorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(
                    runnable,
                    "lfc-placement-settle-verify-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
