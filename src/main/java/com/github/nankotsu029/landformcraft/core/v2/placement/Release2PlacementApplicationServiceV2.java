package com.github.nankotsu029.landformcraft.core.v2.placement;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.LandformErrorCode;
import com.github.nankotsu029.landformcraft.core.LandformException;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.FilePlacementJournalStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyTransactionServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementDesiredBlockV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.StrictPlacementApplyPrerequisiteVerifierV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.VerifiedReleaseCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.envelope.PlacementEnvelopeCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FileStoreDiskSpaceProbeV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationConfirmCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRollbackRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRollbackServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.safety.PlacementContainmentPreflightV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotAllCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotBaselineV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoPrepareCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoPrepareRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.undo.PlacementUndoServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementExpectedBlockResolverV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleVerifyRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleVerifyServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryAcceptRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryDiagnoseRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryDiagnosisV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryPrepareRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryRollbackRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryServiceV2;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.PlacementBlockPhysicsCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPhysicsClassV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryActionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2MeasuredDimensionGateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2PlacementDimensionPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Production Release 2 plan／confirm／execute lifecycle, separate from the frozen v1 facade. */
public final class Release2PlacementApplicationServiceV2 implements AutoCloseable {
    private static final long MAXIMUM_EFFECT_BLOCKS = 50_000_000L;
    private static final long MAXIMUM_CANONICAL_BYTES = 256L * 1024L;

    private final Path releasesRoot;
    private final Path snapshotsRoot;
    private final GenerationExecutors executors;
    private final PlacementWorldGatewayV2 gateway;
    private final Clock clock;
    private final long maximumSnapshotBytes;
    private final Release2PlacementDimensionPolicyV2 dimensionPolicy;
    private final FilePlacementSafetyStoreV2 safetyStore;
    private final FilePlacementJournalStoreV2 journalStore;
    private final Release2PlacementOperationStoreV2 operationStore;
    private final PlacementReservationConfirmCompilerV2 reservationCompiler;
    private final PlacementSnapshotAllCompilerV2 snapshotCompiler;
    private final PlacementContainmentPreflightV2 containment;
    private final PlacementApplyTransactionServiceV2 apply;
    private final PlacementSettleVerifyServiceV2 settleVerify;
    private final PlacementRollbackServiceV2 rollback;
    private final PlacementUndoPrepareCompilerV2 undoPrepare;
    private final PlacementUndoServiceV2 undo;
    private final PlacementRecoveryServiceV2 recovery;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public Release2PlacementApplicationServiceV2(
            Path releasesRoot,
            Path stateRoot,
            GenerationExecutors executors,
            PlacementWorldGatewayV2 gateway,
            Clock clock,
            long maximumSnapshotBytes
    ) throws IOException {
        this(releasesRoot, stateRoot, executors, gateway, clock,
                Release2DiskBudgetV2.legacy(maximumSnapshotBytes),
                Release2PlacementDimensionPolicyV2.unlimited(),
                Release2PlacementOperationStoreV2.WriteFaultInjectorV2.none());
    }

    public Release2PlacementApplicationServiceV2(
            Path releasesRoot,
            Path stateRoot,
            GenerationExecutors executors,
            PlacementWorldGatewayV2 gateway,
            Clock clock,
            long maximumSnapshotBytes,
            Release2MeasuredDimensionGateV2 dimensionGate
    ) throws IOException {
        this(releasesRoot, stateRoot, executors, gateway, clock,
                Release2DiskBudgetV2.legacy(maximumSnapshotBytes),
                Release2PlacementDimensionPolicyV2.of(dimensionGate),
                Release2PlacementOperationStoreV2.WriteFaultInjectorV2.none());
    }

    public Release2PlacementApplicationServiceV2(
            Path releasesRoot,
            Path stateRoot,
            GenerationExecutors executors,
            PlacementWorldGatewayV2 gateway,
            Clock clock,
            long maximumSnapshotBytes,
            Release2MeasuredDimensionGateV2 dimensionGate,
            Release2PlacementOperationStoreV2.WriteFaultInjectorV2 operationStoreFaultInjector
    ) throws IOException {
        this(releasesRoot, stateRoot, executors, gateway, clock,
                Release2DiskBudgetV2.legacy(maximumSnapshotBytes),
                Release2PlacementDimensionPolicyV2.of(dimensionGate), operationStoreFaultInjector);
    }

    /** Production entry point: full operator disk budget plus the V2-11-02 dimension policy. */
    public Release2PlacementApplicationServiceV2(
            Path releasesRoot,
            Path stateRoot,
            GenerationExecutors executors,
            PlacementWorldGatewayV2 gateway,
            Clock clock,
            Release2DiskBudgetV2 diskBudget,
            Release2PlacementDimensionPolicyV2 dimensionPolicy,
            Release2PlacementOperationStoreV2.WriteFaultInjectorV2 operationStoreFaultInjector
    ) throws IOException {
        Objects.requireNonNull(diskBudget, "diskBudget");
        long maximumSnapshotBytes = diskBudget.maximumSnapshotBytes();
        this.releasesRoot = Objects.requireNonNull(releasesRoot, "releasesRoot").toAbsolutePath().normalize();
        this.snapshotsRoot = Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize()
                .resolve("snapshots");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.dimensionPolicy = Objects.requireNonNull(dimensionPolicy, "dimensionPolicy");
        if (maximumSnapshotBytes < 1) throw new IllegalArgumentException("maximumSnapshotBytes must be positive");
        this.maximumSnapshotBytes = maximumSnapshotBytes;
        Files.createDirectories(this.releasesRoot);
        Path root = stateRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        this.safetyStore = new FilePlacementSafetyStoreV2(
                root.resolve("placement-safety-v2.json"), snapshotsRoot, clock,
                new FileStoreDiskSpaceProbeV2(), diskBudget.reservationFloorBytes());
        this.journalStore = new FilePlacementJournalStoreV2(root.resolve("journals"));
        this.operationStore = new Release2PlacementOperationStoreV2(
                root.resolve("operations"), operationStoreFaultInjector);
        this.reservationCompiler = new PlacementReservationConfirmCompilerV2(safetyStore, clock);
        this.snapshotCompiler = new PlacementSnapshotAllCompilerV2(safetyStore, clock);
        this.containment = new PlacementContainmentPreflightV2(clock);
        this.apply = new PlacementApplyTransactionServiceV2(
                new StrictPlacementApplyPrerequisiteVerifierV2(this.releasesRoot, safetyStore, snapshotCompiler),
                gateway, journalStore, clock);
        this.settleVerify = new PlacementSettleVerifyServiceV2(gateway, journalStore, clock);
        this.rollback = new PlacementRollbackServiceV2(
                snapshotCompiler, safetyStore, gateway, journalStore, clock);
        this.undoPrepare = new PlacementUndoPrepareCompilerV2(safetyStore, journalStore, clock);
        this.undo = new PlacementUndoServiceV2(
                snapshotCompiler, safetyStore, gateway, journalStore, clock);
        this.recovery = new PlacementRecoveryServiceV2(
                snapshotCompiler, safetyStore, gateway, journalStore, rollback, clock);
    }

    public boolean isRelease2Path() {
        return true;
    }

    public CompletionStage<PreparedPlanV2> plan(PlanRequestV2 request) {
        Objects.requireNonNull(request, "request");
        return submit(() -> prepare(request));
    }

    public CompletionStage<ConfirmedPlanV2> confirm(ConfirmRequestV2 request) {
        Objects.requireNonNull(request, "request");
        return submit(() -> confirmNow(request));
    }

    public CompletionStage<ExecutionResultV2> execute(ExecuteRequestV2 request) {
        Objects.requireNonNull(request, "request");
        CompletableFuture<ExecutionResultV2> result = new CompletableFuture<>();
        submit(() -> loadRuntime(request.placementId(), request.actor(), request.cancellation()))
                .whenComplete((runtime, loadFailure) -> {
                    if (loadFailure != null) {
                        result.completeExceptionally(unwrap(loadFailure));
                        return;
                    }
                    apply.apply(new PlacementApplyRequestV2(
                            runtime.journal().plan(), runtime.context().envelope(),
                            runtime.context().reservation(), runtime.context().snapshot(),
                            runtime.context().containment(), runtime.journal(), runtime.source(),
                            request.cancellation())).whenComplete((applied, applyFailure) -> {
                        if (applyFailure != null) {
                            finishFailure(runtime, request.cancellation(), unwrap(applyFailure), result);
                            return;
                        }
                        settleVerify.settleAndVerify(new PlacementSettleVerifyRequestV2(
                                runtime.journal().plan(), runtime.context().envelope(),
                                runtime.context().snapshot(), runtime.context().containment(),
                                applied.applyCompleteJournal(), runtime.source(), runtime.baseline(),
                                PlacementSettleVerifyPolicyV2.standard(), request.cancellation(),
                                (applyCompleteJournal, appliedJournal, evidence) ->
                                        operationStore.saveApplied(
                                                runtime.journal().plan().placementId(),
                                                applyCompleteJournal, appliedJournal, evidence)))
                                .whenComplete((verified, verifyFailure) -> {
                                    if (verifyFailure != null) {
                                        finishFailure(runtime, request.cancellation(), unwrap(verifyFailure), result);
                                    } else {
                                        close(runtime.source(), null);
                                        result.complete(new ExecutionResultV2(
                                                verified.verifiedJournal(), false, "APPLIED"));
                                    }
                                });
                    });
                });
        return result.minimalCompletionStage();
    }

    public CompletionStage<PlacementJournalV2> status(UUID placementId) {
        return submit(() -> journalStore.load(placementId));
    }

    public CompletionStage<PlacementUndoPrepareCompilerV2.PreparedUndoV2> prepareUndo(
            UUID placementId,
            PlacementPlanV2.PlacementActorV2 actor
    ) {
        return submit(() -> {
            PlacementJournalV2 appliedJournal = journalStore.load(placementId);
            var context = operationStore.loadApplied(placementId);
            var prepared = undoPrepare.prepare(new PlacementUndoPrepareRequestV2(
                    appliedJournal.plan(), context.envelope(), context.reservation(), context.snapshot(),
                    context.verifyEvidence(), appliedJournal, context.applyCompleteJournal(), actor, null));
            try {
                operationStore.saveUndo(placementId, prepared.undoPlan(), prepared.undoReservation());
            } catch (Throwable failure) {
                try {
                    safetyStore.reserve(
                            context.reservation(),
                            context.reservation().leaseState(),
                            safetyStore.reservationFloorBytes());
                } catch (IOException | RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                try {
                    saveJournalCommitted(appliedJournal);
                } catch (IOException | RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
            return prepared;
        });
    }

    public CompletionStage<PlacementUndoServiceV2.UndoResultV2> executeUndo(
            UUID placementId,
            String token,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) {
        CompletableFuture<PlacementUndoServiceV2.UndoResultV2> result = new CompletableFuture<>();
        submit(() -> {
            PlacementJournalV2 appliedJournal = journalStore.load(placementId);
            var context = operationStore.loadUndo(placementId);
            var source = VerifiedReleaseCanonicalBlockSourceV2.open(
                    resolveRelease(appliedJournal.plan().releaseBinding().releaseDirectory()), cancellation);
            return new UndoRuntime(appliedJournal, context, source);
        }).whenComplete((runtime, loadFailure) -> {
            if (loadFailure != null) {
                result.completeExceptionally(unwrap(loadFailure));
                return;
            }
            var applied = runtime.context().applied();
            undo.undo(new PlacementUndoRequestV2(
                    runtime.journal().plan(), applied.envelope(), applied.reservation(),
                    runtime.context().undoReservation(), applied.snapshot(), applied.verifyEvidence(),
                    runtime.context().undoPlan(), runtime.journal(), applied.applyCompleteJournal(),
                    PlacementSettleVerifyPolicyV2.standard(), token, actor, runtime.source(), cancellation))
                    .whenComplete((value, failure) -> {
                        close(runtime.source(), failure == null ? null : unwrap(failure));
                        if (failure != null) result.completeExceptionally(unwrap(failure));
                        else result.complete(value);
                    });
        });
        return result.minimalCompletionStage();
    }

    public CompletionStage<PlacementRecoveryDiagnosisV2> diagnoseRecovery(
            UUID placementId,
            CancellationToken cancellation
    ) {
        return submit(() -> {
            PlacementJournalV2 journal = journalStore.load(placementId);
            var prepared = operationStore.loadPrepared(placementId);
            PlacementSnapshotPlanV2 snapshot = null;
            try {
                snapshot = operationStore.loadConfirmed(placementId).snapshot();
            } catch (IOException missingConfirmedContext) {
                if (journal.state() == PlacementJournalStateV2.SNAPSHOT_COMPLETE
                        || journal.state() == PlacementJournalStateV2.APPLYING
                        || journal.state() == PlacementJournalStateV2.SETTLING
                        || journal.state() == PlacementJournalStateV2.VERIFYING
                        || journal.state() == PlacementJournalStateV2.RECOVERY_REQUIRED
                        || journal.state() == PlacementJournalStateV2.APPLIED) {
                    throw missingConfirmedContext;
                }
            }
            try (VerifiedReleaseCanonicalBlockSourceV2 source =
                         VerifiedReleaseCanonicalBlockSourceV2.open(
                                 resolveRelease(journal.plan().releaseBinding().releaseDirectory()),
                                 cancellation)) {
                return recovery.diagnose(new PlacementRecoveryDiagnoseRequestV2(
                        journal.plan(), prepared.envelope(), prepared.reservation(), snapshot,
                        journal, source, cancellation));
            }
        });
    }

    public CompletionStage<PlacementRecoveryServiceV2.PreparedRecoveryV2> prepareRecovery(
            UUID placementId,
            PlacementRecoveryActionV2 action,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) {
        return submit(() -> {
            PlacementJournalV2 journal = journalStore.load(placementId);
            var context = operationStore.loadConfirmed(placementId);
            PlacementRecoveryDiagnosisV2 diagnosis;
            try (VerifiedReleaseCanonicalBlockSourceV2 source =
                         VerifiedReleaseCanonicalBlockSourceV2.open(
                                 resolveRelease(journal.plan().releaseBinding().releaseDirectory()),
                                 cancellation)) {
                diagnosis = recovery.diagnose(new PlacementRecoveryDiagnoseRequestV2(
                        journal.plan(), context.envelope(), context.reservation(), context.snapshot(),
                        journal, source, cancellation));
            }
            var prepared = recovery.prepare(new PlacementRecoveryPrepareRequestV2(
                    diagnosis, action, journal.plan(), context.envelope(), context.reservation(),
                    context.snapshot(), journal, actor, null, cancellation));
            operationStore.saveRecovery(placementId, prepared.recoveryPlan());
            return prepared;
        });
    }

    public CompletionStage<PlacementRollbackServiceV2.RollbackResultV2> executeRecoveryRollback(
            UUID placementId,
            String token,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) {
        CompletableFuture<PlacementRollbackServiceV2.RollbackResultV2> result = new CompletableFuture<>();
        submit(() -> {
            PlacementJournalV2 journal = journalStore.load(placementId);
            var context = operationStore.loadConfirmed(placementId);
            return new RecoveryRuntime(journal, context, operationStore.loadRecovery(placementId));
        }).whenComplete((runtime, failure) -> {
            if (failure != null) {
                result.completeExceptionally(unwrap(failure));
                return;
            }
            recovery.executeRollback(new PlacementRecoveryRollbackRequestV2(
                    runtime.recoveryPlan(), runtime.journal().plan(), runtime.context().envelope(),
                    runtime.context().reservation(), runtime.context().snapshot(), runtime.journal(), actor,
                    token, PlacementSettleVerifyPolicyV2.standard(), cancellation))
                    .whenComplete((value, rollbackFailure) -> {
                        if (rollbackFailure != null) result.completeExceptionally(unwrap(rollbackFailure));
                        else result.complete(value);
                    });
        });
        return result.minimalCompletionStage();
    }

    public CompletionStage<PlacementRecoveryServiceV2.AcceptResultV2> executeRecoveryAccept(
            UUID placementId,
            String token,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) {
        return submit(() -> {
            PlacementJournalV2 journal = journalStore.load(placementId);
            var context = operationStore.loadConfirmed(placementId);
            try (VerifiedReleaseCanonicalBlockSourceV2 source =
                         VerifiedReleaseCanonicalBlockSourceV2.open(
                                 resolveRelease(journal.plan().releaseBinding().releaseDirectory()),
                                 cancellation)) {
                return recovery.executeAccept(new PlacementRecoveryAcceptRequestV2(
                        operationStore.loadRecovery(placementId), journal.plan(), context.envelope(),
                        context.reservation(), context.snapshot(), journal, source, actor, token, cancellation));
            }
        });
    }

    public CompletionStage<List<PlacementJournalV2>> inspectRestartState() {
        return submit(() -> {
            List<PlacementJournalV2> ambiguous = new ArrayList<>();
            Path journals = journalRoot();
            if (!Files.exists(journals)) return List.of();
            try (var entries = Files.newDirectoryStream(journals, "*.json")) {
                for (Path path : entries) {
                    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("unsafe Release 2 restart journal entry");
                    }
                    String name = path.getFileName().toString();
                    PlacementJournalV2 journal = journalStore.load(UUID.fromString(
                            name.substring(0, name.length() - 5)));
                    boolean inconsistentApplied = false;
                    if (journal.state() == PlacementJournalStateV2.APPLIED) {
                        try {
                            inconsistentApplied = !operationStore.loadApplied(
                                    journal.plan().placementId()).appliedJournal().equals(journal);
                        } catch (IOException | RuntimeException missingEvidence) {
                            inconsistentApplied = true;
                        }
                    }
                    if (!isTerminal(journal.state()) || inconsistentApplied) ambiguous.add(journal);
                }
            }
            ambiguous.sort(java.util.Comparator.comparing(value -> value.plan().placementId()));
            return List.copyOf(ambiguous);
        });
    }

    /**
     * Rebuilds the region／disk reservation ledger from sealed effect envelopes instead of a
     * ledger file (V2-11-02 fixes the rebuild basis; see
     * {@link FilePlacementSafetyStoreV2#rebuild}). Restart inspection deliberately does not call
     * this: an {@code APPLIED} placement keeps its lease until Undo, while the rebuild filter
     * keeps only planned／active states, so rebuilding on every restart would drop those leases.
     * Changing which journal states hold a lease is a separate Task; until then the durable
     * ledger stays the restart source of truth and this entry point is for explicit repair.
     */
    public void rebuildReservationsFromEnvelopes(List<PlacementJournalV2> journals)
            throws IOException {
        Objects.requireNonNull(journals, "journals");
        List<FilePlacementSafetyStoreV2.RebuildEntryV2> entries = new ArrayList<>();
        for (PlacementJournalV2 journal : journals) {
            PlacementEnvelopePlanV2 envelope;
            try {
                envelope = operationStore.loadPrepared(journal.plan().placementId()).envelope();
            } catch (IOException | RuntimeException missingEnvelope) {
                throw new IOException(
                        "Release 2 reservation rebuild requires the sealed effect envelope for "
                                + journal.plan().placementId(), missingEnvelope);
            }
            entries.add(new FilePlacementSafetyStoreV2.RebuildEntryV2(journal, envelope));
        }
        safetyStore.rebuild(List.copyOf(entries));
    }

    private PreparedPlanV2 prepare(PlanRequestV2 request) throws Exception {
        Path release = resolveRelease(request.releasePath());
        try (VerifiedReleaseCanonicalBlockSourceV2 source =
                     VerifiedReleaseCanonicalBlockSourceV2.open(release, request.cancellation())) {
            var layout = source.layout();
            dimensionPolicy.requireAdmitted(
                    layout.width(), layout.length(), request.worldName(), request.actor().kind());
            ScaleClassV2 scale = ScaleClassV2.forDimensions(layout.width(), layout.length());
            if (scale == ScaleClassV2.LARGE) {
                throw new IllegalArgumentException("LARGE Release 2 Paper placement is not supported");
            }
            TilePlanV2 tilePlan = TilePlanV2.of(layout.width(), layout.length(), ScaleProfileV2.defaults(scale));
            UUID placementId = UUID.randomUUID();
            PlacementPlanV2.PlacementTargetV2 target = new PlacementPlanV2.PlacementTargetV2(
                    request.worldId(), request.worldName(), PlacementPlanV2.AnchorKind.MINIMUM_CORNER,
                    request.minimumX(), request.minimumY(), request.minimumZ(),
                    request.minimumX(), request.minimumY(), request.minimumZ(),
                    Math.addExact(request.minimumX(), layout.width() - 1),
                    Math.addExact(request.minimumY(), layout.maxY() - layout.minY()),
                    Math.addExact(request.minimumZ(), layout.length() - 1));
            var compiled = new PlacementPlanCompilerV2().compile(
                    new PlacementPlanCompilerV2.PlacementCompileRequestV2(
                            placementId, UUID.randomUUID(), "release2-paper", request.actor(), target,
                            new PlacementPlanV2.ReleaseBindingV2(
                                    PlacementPlanV2.ReleaseBindingV2.VERSION, 2, request.releasePath(),
                                    source.binding().releaseManifestChecksum(),
                                    PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION),
                            source.binding().requiredCapabilities(), tilePlan));
            List<PlacementEnvelopeCompilerV2.TileMutationContentV2> contents =
                    mutationContents(compiled.plan(), source, layout);
            var envelope = new PlacementEnvelopeCompilerV2().compile(
                    new PlacementEnvelopeCompilerV2.EnvelopeCompileRequestV2(
                            compiled.plan(), request.allowedWorldBounds(),
                            PlacementEnvelopePlanV2.PhysicsPolicy.standard(),
                            new PlacementEnvelopePlanV2.ResourceBudget(
                                    PlacementEnvelopePlanV2.ResourceBudget.VERSION,
                                    tilePlan.tileCount(), MAXIMUM_EFFECT_BLOCKS, maximumSnapshotBytes,
                                    8_192L, PlacementEnvelopePlanV2.MAX_CANONICAL_BYTES), contents));
            var prepared = reservationCompiler.prepare(
                    new PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2(
                            envelope.boundPlacementPlan(), envelope.envelopePlan(),
                            reservationBudget(tilePlan.tileCount()), request.actor(), null));
            try {
                operationStore.savePrepared(
                        placementId, envelope.envelopePlan(), prepared.reservationPlan(), prepared.journal());
                saveJournalCommitted(prepared.journal());
            } catch (Throwable failure) {
                try {
                    safetyStore.release(placementId);
                } catch (IOException | RuntimeException releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
                throw failure;
            }
            return new PreparedPlanV2(prepared.plan(), envelope.envelopePlan(), prepared.journal(),
                    prepared.plaintextToken());
        }
    }

    private ConfirmedPlanV2 confirmNow(ConfirmRequestV2 request) throws Exception {
        PlacementJournalV2 journal = journalStore.load(request.placementId());
        var context = operationStore.loadPrepared(request.placementId());
        if (!context.preparedJournal().equals(journal)) {
            throw new IOException("Release 2 prepared context does not match the durable journal");
        }
        if (!journal.plan().actor().equals(request.actor())) {
            throw actorMismatch("confirm");
        }
        reservationCompiler.verifyAndConsume(journal.plan(), context.envelope(), context.reservation(),
                request.actor(), request.token());
        var snapshot = snapshotCompiler.snapshotAll(
                new PlacementSnapshotAllCompilerV2.SnapshotAllRequestV2(
                        journal.plan(), context.envelope(), context.reservation(), journal, gateway,
                        snapshotBudget(journal.plan().tileOrder().tiles().size()), request.cancellation(),
                        value -> {
                            if (value.state() != PlacementJournalStateV2.SNAPSHOT_COMPLETE) {
                                saveJournal(value);
                            }
                        }));
        try (VerifiedReleaseCanonicalBlockSourceV2 source = VerifiedReleaseCanonicalBlockSourceV2.open(
                resolveRelease(journal.plan().releaseBinding().releaseDirectory()), request.cancellation())) {
            var baseline = PlacementSnapshotBaselineV2.load(
                    snapshotsRoot, journal.plan(), snapshot.snapshotPlan(), MAXIMUM_EFFECT_BLOCKS,
                    request.cancellation());
            var expected = new PlacementExpectedBlockResolverV2(
                    journal.plan(), context.envelope(), source, baseline);
            var evidence = containment.preflight(new PlacementContainmentPreflightV2.ContainmentRequestV2(
                    journal.plan(), context.envelope(), snapshot.snapshotPlan(), context.reservation(),
                    snapshot.snapshotCompleteJournal(), PlacementContainmentPolicyV2.standard(),
                    expected::expectedAt));
            operationStore.saveConfirmed(
                    request.placementId(), snapshot.snapshotPlan(), evidence,
                    snapshot.snapshotCompleteJournal());
            saveJournalCommitted(snapshot.snapshotCompleteJournal());
            return new ConfirmedPlanV2(snapshot.snapshotCompleteJournal(), snapshot.snapshotPlan(), evidence);
        }
    }

    private RuntimeContext loadRuntime(
            UUID placementId,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) throws Exception {
        PlacementJournalV2 journal = journalStore.load(placementId);
        if (!journal.plan().actor().equals(actor)) {
            throw actorMismatch("execute");
        }
        if (journal.state() != PlacementJournalStateV2.SNAPSHOT_COMPLETE) {
            throw new IllegalArgumentException("Release 2 execute requires SNAPSHOT_COMPLETE");
        }
        var context = operationStore.loadConfirmed(placementId);
        if (!context.confirmedJournal().equals(journal)) {
            throw new IOException("Release 2 confirmed context does not match the durable journal");
        }
        var source = VerifiedReleaseCanonicalBlockSourceV2.open(
                resolveRelease(journal.plan().releaseBinding().releaseDirectory()), cancellation);
        try {
            var baseline = PlacementSnapshotBaselineV2.load(
                    snapshotsRoot, journal.plan(), context.snapshot(), MAXIMUM_EFFECT_BLOCKS, cancellation);
            return new RuntimeContext(journal, context, source, baseline);
        } catch (Throwable failure) {
            close(source, failure);
            throw failure;
        }
    }

    private void finishFailure(
            RuntimeContext runtime,
            CancellationToken cancellation,
            Throwable original,
            CompletableFuture<ExecutionResultV2> result
    ) {
        submit(() -> journalStore.load(runtime.journal().plan().placementId()))
                .whenComplete((failedJournal, loadFailure) -> {
                    if (loadFailure != null || failedJournal.state() != PlacementJournalStateV2.RECOVERY_REQUIRED) {
                        close(runtime.source(), original);
                        result.completeExceptionally(original);
                        return;
                    }
                    rollback.rollback(new PlacementRollbackRequestV2(
                            runtime.journal().plan(), runtime.context().envelope(),
                            runtime.context().reservation(), runtime.context().snapshot(), failedJournal,
                            PlacementSettleVerifyPolicyV2.standard(), () -> false))
                            .whenComplete((rolledBack, rollbackFailure) -> {
                                close(runtime.source(), original);
                                if (rollbackFailure != null) {
                                    original.addSuppressed(unwrap(rollbackFailure));
                                    result.completeExceptionally(original);
                                } else {
                                    result.complete(new ExecutionResultV2(
                                            rolledBack.rolledBackJournal(), true,
                                            "ROLLED_BACK_AFTER_FAILURE:" + original.getClass().getSimpleName()
                                                    + ":" + String.valueOf(original.getMessage())));
                                }
                            });
                });
    }

    private List<PlacementEnvelopeCompilerV2.TileMutationContentV2> mutationContents(
            PlacementPlanV2 plan,
            PlacementCanonicalBlockSourceV2 source,
            VerifiedReleaseCanonicalBlockSourceV2.PlacementLayoutV2 layout
    ) throws IOException {
        List<PlacementEnvelopeCompilerV2.TileMutationContentV2> contents = new ArrayList<>();
        for (PlacementPlanV2.TileRefV2 tile : plan.tileOrder().tiles()) {
            var local = layout.tiles().stream()
                    .filter(value -> value.originX() == tile.coreMinX()
                            && value.originZ() == tile.coreMinZ()
                            && value.width() == tile.coreWidth()
                            && value.length() == tile.coreLength())
                    .findFirst().orElseThrow(() -> new IOException(
                            "verified Release tile layout differs from the canonical placement tile plan"));
            WorldAabbV2 mutation = new WorldAabbV2(
                    Math.addExact(plan.target().minimumX(), local.originX()), plan.target().minimumY(),
                    Math.addExact(plan.target().minimumZ(), local.originZ()),
                    Math.addExact(plan.target().minimumX(), local.originX() + local.width() - 1),
                    Math.addExact(plan.target().minimumY(), local.maxY() - local.minY()),
                    Math.addExact(plan.target().minimumZ(), local.originZ() + local.length() - 1));
            EnumSet<PlacementPhysicsClassV2> physics = EnumSet.noneOf(PlacementPhysicsClassV2.class);
            try (var cursor = source.openTile(plan, tile, mutation)) {
                PlacementDesiredBlockV2 block;
                while ((block = cursor.next()) != null) {
                    var kind = PlacementBlockPhysicsCatalogV2.require(block.blockState());
                    physics.add(PlacementBlockPhysicsCatalogV2.toEnvelopeClass(kind));
                }
            }
            contents.add(new PlacementEnvelopeCompilerV2.TileMutationContentV2(
                    tile.tileId(), tile.tileIndex(), mutation, List.copyOf(physics)));
        }
        if (contents.size() != layout.tiles().size()) {
            throw new IOException("verified Release tile set is not an exact placement tile plan");
        }
        return List.copyOf(contents);
    }

    private PlacementReservationPlanV2.ResourceBudget reservationBudget(int tiles) {
        return new PlacementReservationPlanV2.ResourceBudget(
                PlacementReservationPlanV2.ResourceBudget.VERSION, tiles, 4_096,
                maximumSnapshotBytes, 8_192L, PlacementReservationPlanV2.MAX_CANONICAL_BYTES);
    }

    private PlacementSnapshotPlanV2.ResourceBudget snapshotBudget(int tiles) {
        return new PlacementSnapshotPlanV2.ResourceBudget(
                PlacementSnapshotPlanV2.ResourceBudget.VERSION, tiles, maximumSnapshotBytes,
                PlacementSnapshotPlanV2.MAXIMUM_PALETTE_ENTRIES_PER_TILE, 8_192L,
                PlacementSnapshotPlanV2.MAX_CANONICAL_BYTES);
    }

    private Path resolveRelease(String relative) throws IOException {
        Path release = releasesRoot.resolve(relative).normalize();
        if (!release.startsWith(releasesRoot) || Files.isSymbolicLink(release)
                || (!Files.isDirectory(release, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(release, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Release 2 container is missing, unsafe, or outside its root");
        }
        return release;
    }

    private Path journalRoot() {
        return snapshotsRoot.getParent().resolve("journals");
    }

    private void saveJournal(PlacementJournalV2 journal) {
        try {
            saveJournalCommitted(journal);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to persist Release 2 journal", exception);
        }
    }

    private void saveJournalCommitted(PlacementJournalV2 journal) throws IOException {
        try {
            journalStore.save(journal);
        } catch (IOException failure) {
            try {
                if (journalStore.exists(journal.plan().placementId())
                        && journalStore.load(journal.plan().placementId()).equals(journal)) {
                    return;
                }
            } catch (IOException | RuntimeException inspectionFailure) {
                failure.addSuppressed(inspectionFailure);
            }
            throw failure;
        }
    }

    private <T> CompletionStage<T> submit(CheckedSupplier<T> operation) {
        if (!accepting.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Release 2 service is closed"));
        }
        return executors.supplyIo(() -> {
            try {
                return operation.get();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).minimalCompletionStage();
    }

    @Override
    public void close() {
        if (accepting.compareAndSet(true, false)) {
            apply.close();
            settleVerify.close();
            rollback.close();
            undo.close();
        }
    }

    private static boolean isTerminal(PlacementJournalStateV2 state) {
        return state == PlacementJournalStateV2.APPLIED
                || state == PlacementJournalStateV2.ROLLED_BACK
                || state == PlacementJournalStateV2.UNDONE;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static LandformException actorMismatch(String stage) {
        return new LandformException(
                LandformErrorCode.CONFIRM_ACTOR_MISMATCH,
                "Release 2 placement actor does not match the planned operation.",
                "release2-placement",
                "",
                stage,
                "Use the same operator identity that created the Release 2 placement plan.");
    }

    private static void close(AutoCloseable closeable, Throwable failure) {
        try {
            closeable.close();
        } catch (Exception closeFailure) {
            if (failure != null) failure.addSuppressed(closeFailure);
        }
    }

    @FunctionalInterface private interface CheckedSupplier<T> { T get() throws Exception; }

    public record PlanRequestV2(
            String releasePath,
            UUID worldId,
            String worldName,
            int minimumX,
            int minimumY,
            int minimumZ,
            WorldAabbV2 allowedWorldBounds,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) {
        public PlanRequestV2 {
            Objects.requireNonNull(releasePath, "releasePath");
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(worldName, "worldName");
            Objects.requireNonNull(allowedWorldBounds, "allowedWorldBounds");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(cancellation, "cancellation");
        }
    }

    public record ConfirmRequestV2(
            UUID placementId,
            String token,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) { }

    public record ExecuteRequestV2(
            UUID placementId,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) {
        public ExecuteRequestV2 {
            Objects.requireNonNull(placementId, "placementId");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(cancellation, "cancellation");
        }
    }

    public record PreparedPlanV2(
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementJournalV2 journal,
            String confirmationToken
    ) { }

    public record ConfirmedPlanV2(
            PlacementJournalV2 journal,
            PlacementSnapshotPlanV2 snapshot,
            com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentEvidenceV2 containment
    ) { }

    public record ExecutionResultV2(PlacementJournalV2 journal, boolean rolledBack, String outcome) { }

    private record RuntimeContext(
            PlacementJournalV2 journal,
            Release2PlacementOperationStoreV2.ConfirmedContextV2 context,
            VerifiedReleaseCanonicalBlockSourceV2 source,
            PlacementExpectedBlockResolverV2.SnapshotBaselineV2 baseline
    ) { }

    private record UndoRuntime(
            PlacementJournalV2 journal,
            Release2PlacementOperationStoreV2.UndoContextV2 context,
            VerifiedReleaseCanonicalBlockSourceV2 source
    ) { }

    private record RecoveryRuntime(
            PlacementJournalV2 journal,
            Release2PlacementOperationStoreV2.ConfirmedContextV2 context,
            com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryPlanV2 recoveryPlan
    ) { }
}
