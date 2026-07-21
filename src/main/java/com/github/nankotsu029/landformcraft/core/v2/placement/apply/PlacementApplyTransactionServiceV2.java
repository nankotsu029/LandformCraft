package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.PlacementBlockPhysicsCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPhysicsClassV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
 * Release 2 apply-only transaction orchestrator (V2-6-06).
 *
 * <p>The service owns a bounded worker pool, but not the Paper/WorldEdit gateway. It strictly
 * verifies every earlier V2-6 gate before persisting {@code APPLYING}, dispatches canonical tiles
 * as bounded scheduler slices, and records only a canonical APPLIED tile prefix. It deliberately
 * stops before settle/full verify, rollback, Undo, Recovery, or production capability enablement.</p>
 */
public final class PlacementApplyTransactionServiceV2 implements AutoCloseable {
    private static final String STREAM_VERSION = "release-2-placement-final-block-stream-v1";

    private final PlacementApplyPrerequisiteVerifierV2 prerequisiteVerifier;
    private final PlacementWorldGatewayV2 gateway;
    private final PlacementJournalStoreV2 journalStore;
    private final Clock clock;
    private final PlacementApplyLimitsV2 limits;
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final ThreadPoolExecutor workers;
    private final Semaphore admissions;
    private final ConcurrentHashMap<UUID, Boolean> activeOperations = new ConcurrentHashMap<>();
    private final AtomicInteger activeCount = new AtomicInteger();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final CompletableFuture<Void> termination = new CompletableFuture<>();

    public PlacementApplyTransactionServiceV2(
            PlacementApplyPrerequisiteVerifierV2 prerequisiteVerifier,
            PlacementWorldGatewayV2 gateway,
            PlacementJournalStoreV2 journalStore,
            Clock clock
    ) {
        this(prerequisiteVerifier, gateway, journalStore, clock, PlacementApplyLimitsV2.defaults());
    }

    public PlacementApplyTransactionServiceV2(
            PlacementApplyPrerequisiteVerifierV2 prerequisiteVerifier,
            PlacementWorldGatewayV2 gateway,
            PlacementJournalStoreV2 journalStore,
            Clock clock,
            PlacementApplyLimitsV2 limits
    ) {
        this.prerequisiteVerifier = Objects.requireNonNull(prerequisiteVerifier, "prerequisiteVerifier");
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
                new ApplyWorkerThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.admissions = new Semaphore(
                Math.addExact(limits.workerThreads(), limits.maximumQueuedTransactions()), true);
    }

    /**
     * Starts an apply transaction and returns a read-only observer stage. Cancelling or timing out
     * a derived observer cannot cancel an accepted scheduler operation.
     */
    public CompletionStage<ApplyResultV2> apply(PlacementApplyRequestV2 request) {
        Objects.requireNonNull(request, "request");
        if (!accepting.get()) {
            return failed(new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.SERVICE_CLOSED,
                    "Release 2 apply service is closed",
                    false));
        }
        if (!admissions.tryAcquire()) {
            return failed(new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.QUEUE_SATURATED,
                    "Release 2 apply transaction queue is saturated",
                    false));
        }
        UUID operationId = request.placementPlan().operationId();
        if (activeOperations.putIfAbsent(operationId, Boolean.TRUE) != null) {
            admissions.release();
            return failed(new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.STATE_MISMATCH,
                    "Release 2 apply operation is already active",
                    false));
        }

        Transaction transaction = new Transaction(request);
        activeCount.incrementAndGet();
        try {
            workers.execute(() -> start(transaction));
        } catch (RejectedExecutionException rejected) {
            finishAdmission(transaction);
            return failed(new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.QUEUE_SATURATED,
                    "Release 2 apply worker queue rejected the transaction",
                    false,
                    rejected));
        }
        return transaction.result.minimalCompletionStage();
    }

    /** Stops new transactions. Accepted gateway work remains observable until its real completion. */
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
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.SERVICE_CLOSED,
                        "Release 2 apply service closed before transaction admission",
                        false);
            }
            prerequisiteVerifier.verify(transaction.request);
            admit(transaction.request);
            transaction.sourceBinding = transaction.request.blockSource().binding();
            transaction.preflight = preflight(transaction.request, transaction.sourceBinding);
            if (!transaction.request.blockSource().binding().equals(transaction.sourceBinding)) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.SOURCE_INVALID,
                        "canonical block source binding changed during preflight",
                        false);
            }
            if (transaction.request.cancellation().isCancellationRequested()) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.CANCELLED_BEFORE_COMMIT,
                        "Release 2 apply was cancelled before the first scheduler submission",
                        false);
            }
            if (!accepting.get()) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.SERVICE_CLOSED,
                        "Release 2 apply service closed before the first scheduler submission",
                        false);
            }

            transaction.tileCursor = new TileSliceCursor(transaction, 0);
            PlacementApplySliceV2 first = transaction.tileCursor.nextSlice();
            if (first == null) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.SOURCE_INVALID,
                        "canonical block source produced no apply mutations",
                        false);
            }
            PlacementJournalV2 applying = sealJournal(
                    transaction.request.journal(),
                    PlacementJournalStateV2.APPLYING,
                    transaction.request.journal().tiles(),
                    "Release 2 canonical apply started; settle and full verify pending");
            saveJournal(applying, false);
            transaction.currentJournal = applying;
            transaction.startedJournal = applying;
            submitSlice(transaction, first);
        } catch (Throwable failure) {
            fail(transaction, normalize(failure, false));
        }
    }

    private void admit(PlacementApplyRequestV2 request) {
        PlacementCanonicalBlockSourceV2.SourceBindingV2 binding = request.blockSource().binding();
        if (binding.overlayOrdinals().size() > limits.maximumOverlayOrdinals()) {
            throw new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.RESOURCE_BUDGET_EXCEEDED,
                    "canonical block source exceeds the overlay ordinal budget",
                    false);
        }
        long sliceBytes = limits.maximumSliceWorkingBytes();
        if (sliceBytes > request.placementPlan().budget().maximumWorkingBytes()) {
            throw new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.RESOURCE_BUDGET_EXCEEDED,
                    "scheduler slice working-set estimate exceeds the placement plan budget",
                    false);
        }
        long totalBlocks = 0L;
        for (PlacementEnvelopePlanV2.TileEnvelopeV2 tile : request.envelopePlan().tiles()) {
            totalBlocks = Math.addExact(totalBlocks, tile.mutationAabb().volumeBlocks());
            if (totalBlocks > limits.maximumBlocksPerTransaction()) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.RESOURCE_BUDGET_EXCEEDED,
                        "Release 2 apply block count exceeds transaction admission",
                        false);
            }
        }
    }

    private List<TilePreflight> preflight(
            PlacementApplyRequestV2 request,
            PlacementCanonicalBlockSourceV2.SourceBindingV2 binding
    ) {
        List<TilePreflight> result = new ArrayList<>(request.placementPlan().tileOrder().tiles().size());
        for (int index = 0; index < request.placementPlan().tileOrder().tiles().size(); index++) {
            cancelBeforeCommit(request);
            PlacementPlanV2.TileRefV2 tile = request.placementPlan().tileOrder().tiles().get(index);
            PlacementEnvelopePlanV2.TileEnvelopeV2 envelopeTile = request.envelopePlan().tiles().get(index);
            if (tile.tileIndex() != envelopeTile.tileIndex() || !tile.tileId().equals(envelopeTile.tileId())) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.BINDING_MISMATCH,
                        "placement tile order differs from the envelope plan",
                        false);
            }
            StreamEvidence evidence = scanSource(request, binding, tile, envelopeTile);
            result.add(new TilePreflight(tile, envelopeTile, evidence.checksum(), evidence.passCounts()));
        }
        return List.copyOf(result);
    }

    private StreamEvidence scanSource(
            PlacementApplyRequestV2 request,
            PlacementCanonicalBlockSourceV2.SourceBindingV2 binding,
            PlacementPlanV2.TileRefV2 tile,
            PlacementEnvelopePlanV2.TileEnvelopeV2 envelopeTile
    ) {
        StreamValidator validator = new StreamValidator(binding, tile, envelopeTile);
        try (PlacementCanonicalBlockSourceV2.BlockCursorV2 cursor =
                     request.blockSource().openTile(
                             request.placementPlan(), tile, envelopeTile.mutationAabb())) {
            PlacementDesiredBlockV2 block;
            while ((block = cursor.next()) != null) {
                validator.accept(block);
            }
        } catch (IOException | RuntimeException exception) {
            throw new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.SOURCE_INVALID,
                    "canonical block source failed strict streaming preflight",
                    false,
                    exception);
        }
        return validator.finish();
    }

    private void submitSlice(Transaction transaction, PlacementApplySliceV2 slice) {
        transaction.submissionAttempted = true;
        CompletionStage<PlacementApplySliceReceiptV2> observed;
        try {
            observed = Objects.requireNonNull(gateway.applyBlockSlice(slice), "gateway apply stage");
        } catch (Throwable failure) {
            fail(transaction, new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.GATEWAY_FAILURE,
                    "gateway rejected or failed during scheduler submission",
                    true,
                    failure));
            return;
        }
        observed.whenComplete((receipt, failure) -> dispatchContinuation(transaction, () -> {
            if (failure != null) {
                fail(transaction, new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.GATEWAY_FAILURE,
                        "scheduler-accepted Release 2 mutation did not complete successfully",
                        true,
                        unwrap(failure)));
                return;
            }
            try {
                Objects.requireNonNull(receipt, "gateway receipt").requireMatches(slice);
            } catch (RuntimeException invalid) {
                fail(transaction, new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.GATEWAY_RECEIPT_INVALID,
                        "gateway completion or resource-close receipt is invalid",
                        true,
                        invalid));
                return;
            }
            transaction.acceptedMutation = true;
            transaction.totalSlices++;
            transaction.totalMutations = Math.addExact(
                    transaction.totalMutations, receipt.appliedMutations());
            continueApply(transaction);
        }));
    }

    private void continueApply(Transaction transaction) {
        try {
            if (!accepting.get()) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.SHUTDOWN_AFTER_COMMIT,
                        "apply service shutdown after a scheduler-accepted mutation",
                        true);
            }
            if (transaction.request.cancellation().isCancellationRequested()) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.CANCELLED_AFTER_COMMIT,
                        "apply cancellation observed after a scheduler-accepted mutation",
                        true);
            }
            PlacementApplySliceV2 next = transaction.tileCursor.nextSlice();
            if (next != null) {
                submitSlice(transaction, next);
                return;
            }

            int completedTile = transaction.tileIndex;
            List<PlacementJournalV2.PlacementTileEntryV2> entries = markApplied(
                    transaction.currentJournal.tiles(), completedTile);
            PlacementJournalV2 checkpoint = sealJournal(
                    transaction.currentJournal,
                    PlacementJournalStateV2.APPLYING,
                    entries,
                    "canonical tile " + completedTile + " applied; settle/full verify pending");
            saveJournal(checkpoint, true);
            transaction.currentJournal = checkpoint;
            transaction.tileCursor.close();
            transaction.tileCursor = null;
            transaction.tileIndex++;
            if (transaction.tileIndex >= transaction.preflight.size()) {
                complete(transaction, new ApplyResultV2(
                        transaction.startedJournal,
                        checkpoint,
                        transaction.totalSlices,
                        transaction.totalMutations,
                        transaction.preflight.stream().map(TilePreflight::semanticChecksum).toList()));
                return;
            }
            transaction.tileCursor = new TileSliceCursor(transaction, transaction.tileIndex);
            PlacementApplySliceV2 first = transaction.tileCursor.nextSlice();
            if (first == null) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.SOURCE_INVALID,
                        "canonical block source produced an empty tile",
                        true);
            }
            submitSlice(transaction, first);
        } catch (Throwable failure) {
            fail(transaction, normalize(failure, true));
        }
    }

    private void dispatchContinuation(Transaction transaction, Runnable continuation) {
        if (transaction.finished.get()) {
            return;
        }
        try {
            workers.execute(continuation);
        } catch (RejectedExecutionException rejected) {
            fail(transaction, new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.SHUTDOWN_AFTER_COMMIT,
                    "apply worker rejected a late gateway completion",
                    transaction.submissionAttempted,
                    rejected));
        }
    }

    private void fail(Transaction transaction, PlacementApplyExceptionV2 failure) {
        if (!transaction.finished.compareAndSet(false, true)) {
            return;
        }
        closeCursor(transaction, failure);
        PlacementApplyExceptionV2 reported = failure;
        if (failure.worldMutationMayHaveOccurred() || transaction.submissionAttempted) {
            PlacementJournalV2 basis = transaction.currentJournal != null
                    ? transaction.currentJournal : transaction.request.journal();
            try {
                PlacementJournalV2 recovery = sealJournal(
                        basis,
                        PlacementJournalStateV2.RECOVERY_REQUIRED,
                        basis.tiles(),
                        "Release 2 apply requires recovery after " + failure.code());
                saveJournal(recovery, true);
                transaction.currentJournal = recovery;
            } catch (Throwable persistenceFailure) {
                failure.addSuppressed(persistenceFailure);
                reported = new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.JOURNAL_PERSISTENCE_FAILED,
                        "world mutation may have occurred and RECOVERY_REQUIRED could not be persisted",
                        true,
                        failure);
            }
        }
        transaction.result.completeExceptionally(reported);
        finishAdmission(transaction);
    }

    private void complete(Transaction transaction, ApplyResultV2 value) {
        if (!transaction.finished.compareAndSet(false, true)) {
            return;
        }
        closeCursor(transaction, null);
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

    private void closeCursor(Transaction transaction, Throwable failure) {
        if (transaction.tileCursor == null) {
            return;
        }
        try {
            transaction.tileCursor.close();
        } catch (RuntimeException closeFailure) {
            if (failure != null) {
                failure.addSuppressed(closeFailure);
            }
        } finally {
            transaction.tileCursor = null;
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

    private void saveJournal(PlacementJournalV2 journal, boolean mutationMayHaveOccurred) {
        try {
            journalStore.save(journal);
        } catch (IOException | RuntimeException exception) {
            throw new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.JOURNAL_PERSISTENCE_FAILED,
                    "Release 2 placement journal persistence failed",
                    mutationMayHaveOccurred,
                    exception);
        }
    }

    private static List<PlacementJournalV2.PlacementTileEntryV2> markApplied(
            List<PlacementJournalV2.PlacementTileEntryV2> entries,
            int tileIndex
    ) {
        List<PlacementJournalV2.PlacementTileEntryV2> updated = new ArrayList<>(entries);
        PlacementJournalV2.PlacementTileEntryV2 current = updated.get(tileIndex);
        if (current.state() != PlacementTileStateV2.SNAPSHOTTED) {
            throw new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.STATE_MISMATCH,
                    "only a SNAPSHOTTED canonical tile may become APPLIED",
                    true);
        }
        updated.set(tileIndex, new PlacementJournalV2.PlacementTileEntryV2(
                current.tileId(),
                current.tileIndex(),
                PlacementTileStateV2.APPLIED,
                current.snapshotFile(),
                current.snapshotChecksum()));
        return List.copyOf(updated);
    }

    private static void cancelBeforeCommit(PlacementApplyRequestV2 request) {
        if (request.cancellation().isCancellationRequested()) {
            throw new PlacementApplyExceptionV2(
                    PlacementApplyFailureCodeV2.CANCELLED_BEFORE_COMMIT,
                    "Release 2 apply cancelled during canonical source preflight",
                    false);
        }
    }

    private static PlacementApplyExceptionV2 normalize(Throwable failure, boolean mutationMayHaveOccurred) {
        Throwable actual = unwrap(failure);
        if (actual instanceof PlacementApplyExceptionV2 known) {
            if (!mutationMayHaveOccurred || known.worldMutationMayHaveOccurred()) {
                return known;
            }
            return new PlacementApplyExceptionV2(
                    known.code(), known.getMessage(), true, known);
        }
        return new PlacementApplyExceptionV2(
                PlacementApplyFailureCodeV2.SOURCE_INVALID,
                "Release 2 apply orchestration failed",
                mutationMayHaveOccurred,
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

    public record ApplyResultV2(
            PlacementJournalV2 startedJournal,
            PlacementJournalV2 applyCompleteJournal,
            long schedulerSlices,
            long appliedMutations,
            List<String> canonicalTileChecksums
    ) {
        public ApplyResultV2 {
            Objects.requireNonNull(startedJournal, "startedJournal");
            Objects.requireNonNull(applyCompleteJournal, "applyCompleteJournal");
            if (startedJournal.state() != PlacementJournalStateV2.APPLYING
                    || applyCompleteJournal.state() != PlacementJournalStateV2.APPLYING
                    || schedulerSlices < 1 || appliedMutations < 1) {
                throw new IllegalArgumentException("invalid Release 2 apply result");
            }
            canonicalTileChecksums = List.copyOf(canonicalTileChecksums);
        }
    }

    private final class TileSliceCursor implements AutoCloseable {
        private final Transaction transaction;
        private final TilePreflight preflight;
        private final List<PassOrdinal> combinations;
        private int combinationIndex;
        private PlacementCanonicalBlockSourceV2.BlockCursorV2 sourceCursor;
        private StreamValidator validator;
        private long selectedCount;
        private boolean closed;

        private TileSliceCursor(Transaction transaction, int tileIndex) {
            this.transaction = transaction;
            this.preflight = transaction.preflight.get(tileIndex);
            this.combinations = combinations(preflight, transaction.sourceBinding.overlayOrdinals());
        }

        PlacementApplySliceV2 nextSlice() {
            requireOpen();
            while (combinationIndex < combinations.size()) {
                if (transaction.request.cancellation().isCancellationRequested()) {
                    throw new PlacementApplyExceptionV2(
                            transaction.acceptedMutation
                                    ? PlacementApplyFailureCodeV2.CANCELLED_AFTER_COMMIT
                                    : PlacementApplyFailureCodeV2.CANCELLED_BEFORE_COMMIT,
                            "Release 2 apply cancellation observed while streaming a tile",
                            transaction.acceptedMutation);
                }
                openCombinationIfNeeded();
                PassOrdinal selected = combinations.get(combinationIndex);
                List<PlacementDesiredBlockV2> mutations = new ArrayList<>(
                        limits.maximumMutationsPerSchedulerSlice());
                try {
                    while (mutations.size() < limits.maximumMutationsPerSchedulerSlice()) {
                        PlacementDesiredBlockV2 block = sourceCursor.next();
                        if (block == null) {
                            finishCombination(selected);
                            break;
                        }
                        validator.accept(block);
                        if (block.pass() == selected.pass()
                                && block.overlayOrdinal() == selected.overlayOrdinal()) {
                            mutations.add(block);
                            selectedCount++;
                        }
                    }
                } catch (IOException | RuntimeException exception) {
                    throw new PlacementApplyExceptionV2(
                            PlacementApplyFailureCodeV2.SOURCE_INVALID,
                            "canonical block source changed or failed during apply streaming",
                            transaction.submissionAttempted,
                            exception);
                }
                if (!mutations.isEmpty()) {
                    return new PlacementApplySliceV2(
                            PlacementApplySliceV2.GATEWAY_CONTRACT_VERSION,
                            transaction.request.placementPlan().placementId(),
                            transaction.request.placementPlan().operationId(),
                            transaction.request.placementPlan().target().worldId(),
                            preflight.tile().tileId(),
                            preflight.tile().tileIndex(),
                            selected.pass(),
                            selected.overlayOrdinal(),
                            transaction.nextSliceSequence++,
                            preflight.envelopeTile().mutationAabb(),
                            mutations,
                            transaction.sourceBinding.immutableFingerprint());
                }
            }
            return null;
        }

        private void openCombinationIfNeeded() {
            if (sourceCursor != null) {
                return;
            }
            if (!transaction.request.blockSource().binding().equals(transaction.sourceBinding)) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.SOURCE_INVALID,
                        "canonical block source binding changed before apply scan",
                        transaction.submissionAttempted);
            }
            try {
                sourceCursor = transaction.request.blockSource().openTile(
                        transaction.request.placementPlan(),
                        preflight.tile(),
                        preflight.envelopeTile().mutationAabb());
            } catch (IOException | RuntimeException exception) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.SOURCE_INVALID,
                        "canonical block source could not be opened for apply",
                        transaction.submissionAttempted,
                        exception);
            }
            validator = new StreamValidator(
                    transaction.sourceBinding, preflight.tile(), preflight.envelopeTile());
            selectedCount = 0L;
        }

        private void finishCombination(PassOrdinal selected) throws IOException {
            StreamEvidence evidence = validator.finish();
            sourceCursor.close();
            sourceCursor = null;
            validator = null;
            long expected = preflight.passCounts()
                    .getOrDefault(selected.pass(), Map.of())
                    .getOrDefault(selected.overlayOrdinal(), 0L);
            if (!evidence.checksum().equals(preflight.semanticChecksum())
                    || selectedCount != expected
                    || !transaction.request.blockSource().binding().equals(transaction.sourceBinding)) {
                throw new PlacementApplyExceptionV2(
                        PlacementApplyFailureCodeV2.SOURCE_INVALID,
                        "canonical block source changed between preflight and apply",
                        transaction.submissionAttempted);
            }
            combinationIndex++;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (sourceCursor != null) {
                try {
                    sourceCursor.close();
                } catch (IOException exception) {
                    throw new PlacementApplyExceptionV2(
                            PlacementApplyFailureCodeV2.SOURCE_INVALID,
                            "canonical block source cursor failed to close",
                            transaction.submissionAttempted,
                            exception);
                } finally {
                    sourceCursor = null;
                }
            }
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("tile apply cursor is closed");
            }
        }
    }

    private static List<PassOrdinal> combinations(
            TilePreflight preflight,
            List<Integer> declaredOrdinals
    ) {
        List<PassOrdinal> result = new ArrayList<>();
        for (PlacementApplyPassV2 pass : List.of(
                PlacementApplyPassV2.SOLID,
                PlacementApplyPassV2.AIR_CARVE,
                PlacementApplyPassV2.FLUID)) {
            Map<Integer, Long> counts = preflight.passCounts().getOrDefault(pass, Map.of());
            for (Integer ordinal : declaredOrdinals) {
                if (counts.getOrDefault(ordinal, 0L) > 0L) {
                    result.add(new PassOrdinal(pass, ordinal));
                }
            }
        }
        return List.copyOf(result);
    }

    private static final class StreamValidator {
        private final PlacementCanonicalBlockSourceV2.SourceBindingV2 binding;
        private final PlacementPlanV2.TileRefV2 tile;
        private final PlacementEnvelopePlanV2.TileEnvelopeV2 envelopeTile;
        private final MessageDigest digest = digest();
        private final EnumMap<PlacementApplyPassV2, Map<Integer, Long>> passCounts =
                new EnumMap<>(PlacementApplyPassV2.class);
        private int expectedX;
        private int expectedY;
        private int expectedZ;
        private long count;
        private boolean finished;

        private StreamValidator(
                PlacementCanonicalBlockSourceV2.SourceBindingV2 binding,
                PlacementPlanV2.TileRefV2 tile,
                PlacementEnvelopePlanV2.TileEnvelopeV2 envelopeTile
        ) {
            this.binding = binding;
            this.tile = tile;
            this.envelopeTile = envelopeTile;
            expectedX = envelopeTile.mutationAabb().minX();
            expectedY = envelopeTile.mutationAabb().minY();
            expectedZ = envelopeTile.mutationAabb().minZ();
            updateString(digest, STREAM_VERSION);
            updateString(digest, binding.immutableFingerprint());
            updateString(digest, tile.tileId());
            updateInt(digest, tile.tileIndex());
            updateAabb(digest, envelopeTile.mutationAabb());
        }

        private void accept(PlacementDesiredBlockV2 block) {
            if (finished || block.x() != expectedX || block.y() != expectedY || block.z() != expectedZ) {
                throw new IllegalArgumentException(
                        "canonical block source coordinate order or coverage mismatch");
            }
            if (block.ownerTileIndex() != tile.tileIndex()
                    || !binding.overlayOrdinals().contains(block.overlayOrdinal())
                    || !envelopeTile.mutationAabb().contains(block.x(), block.y(), block.z())) {
                throw new IllegalArgumentException("canonical block source ownership mismatch");
            }
            PlacementPhysicsClassV2 physicsClass = PlacementBlockPhysicsCatalogV2.toEnvelopeClass(
                    PlacementBlockPhysicsCatalogV2.require(block.blockState()));
            if (!envelopeTile.physicsClasses().contains(physicsClass)) {
                throw new IllegalArgumentException(
                        "canonical block source uses a physics class absent from its effect envelope");
            }
            updateInt(digest, block.x());
            updateInt(digest, block.y());
            updateInt(digest, block.z());
            updateString(digest, block.blockState());
            updateInt(digest, block.pass().applyOrder());
            updateInt(digest, block.overlayOrdinal());
            updateInt(digest, block.ownerTileIndex());
            passCounts.computeIfAbsent(block.pass(), ignored -> new LinkedHashMap<>())
                    .merge(block.overlayOrdinal(), 1L, Math::addExact);
            count++;
            advance();
        }

        private StreamEvidence finish() {
            if (finished || count != envelopeTile.mutationAabb().volumeBlocks()
                    || expectedY <= envelopeTile.mutationAabb().maxY()) {
                throw new IllegalArgumentException("canonical block source ended before exact AABB coverage");
            }
            finished = true;
            EnumMap<PlacementApplyPassV2, Map<Integer, Long>> immutable =
                    new EnumMap<>(PlacementApplyPassV2.class);
            for (Map.Entry<PlacementApplyPassV2, Map<Integer, Long>> entry : passCounts.entrySet()) {
                immutable.put(entry.getKey(), Map.copyOf(entry.getValue()));
            }
            return new StreamEvidence(HexFormat.of().formatHex(digest.digest()), Map.copyOf(immutable));
        }

        private void advance() {
            if (expectedX < envelopeTile.mutationAabb().maxX()) {
                expectedX++;
                return;
            }
            expectedX = envelopeTile.mutationAabb().minX();
            if (expectedZ < envelopeTile.mutationAabb().maxZ()) {
                expectedZ++;
                return;
            }
            expectedZ = envelopeTile.mutationAabb().minZ();
            expectedY++;
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void updateAabb(MessageDigest digest, com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2 aabb) {
        updateInt(digest, aabb.minX());
        updateInt(digest, aabb.minY());
        updateInt(digest, aabb.minZ());
        updateInt(digest, aabb.maxX());
        updateInt(digest, aabb.maxY());
        updateInt(digest, aabb.maxZ());
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private final class Transaction {
        private final PlacementApplyRequestV2 request;
        private final CompletableFuture<ApplyResultV2> result = new CompletableFuture<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean admissionReleased = new AtomicBoolean();
        private PlacementCanonicalBlockSourceV2.SourceBindingV2 sourceBinding;
        private List<TilePreflight> preflight;
        private PlacementJournalV2 startedJournal;
        private PlacementJournalV2 currentJournal;
        private TileSliceCursor tileCursor;
        private int tileIndex;
        private long nextSliceSequence;
        private long totalSlices;
        private long totalMutations;
        private boolean submissionAttempted;
        private boolean acceptedMutation;

        private Transaction(PlacementApplyRequestV2 request) {
            this.request = request;
        }
    }

    private record TilePreflight(
            PlacementPlanV2.TileRefV2 tile,
            PlacementEnvelopePlanV2.TileEnvelopeV2 envelopeTile,
            String semanticChecksum,
            Map<PlacementApplyPassV2, Map<Integer, Long>> passCounts
    ) {
    }

    private record StreamEvidence(
            String checksum,
            Map<PlacementApplyPassV2, Map<Integer, Long>> passCounts
    ) {
    }

    private record PassOrdinal(PlacementApplyPassV2 pass, int overlayOrdinal) {
    }

    private static final class ApplyWorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "landformcraft-v2-apply-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
