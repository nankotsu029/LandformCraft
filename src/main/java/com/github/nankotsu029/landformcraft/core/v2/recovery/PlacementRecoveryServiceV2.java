package com.github.nankotsu029.landformcraft.core.v2.recovery;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementJournalStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementConfirmationBinderV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRollbackRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRollbackServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotAllCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotFileCodecV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementExpectedBlockResolverV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyExceptionV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementConfirmationActionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryActionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryClassificationV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Release 2 startup recovery (V2-6-10). Classifies persisted journal／artifact／world evidence
 * conservatively, prepares confirmation-bound rollback／accept plans, reconciles late interrupted
 * operations deterministically, and manages dry-run-bound snapshot cleanup retention. Diagnose and
 * prepare never mutate the world or the persisted journal; execute paths consume an actor-bound
 * one-time confirmation first and never classify ambiguity as success. Distinct from the frozen
 * v1 recovery path ({@code PlacementApplicationService}).
 */
public final class PlacementRecoveryServiceV2 {
    /** Journal states that prove no world mutation has happened yet. */
    private static final Set<PlacementJournalStateV2> PRE_MUTATION_STATES = EnumSet.of(
            PlacementJournalStateV2.PLANNED,
            PlacementJournalStateV2.RELEASE_VALIDATED,
            PlacementJournalStateV2.ENVELOPE_BOUND,
            PlacementJournalStateV2.RESERVATION_BOUND,
            PlacementJournalStateV2.CONFIRMATION_ISSUED,
            PlacementJournalStateV2.SNAPSHOTTING,
            PlacementJournalStateV2.SNAPSHOT_COMPLETE);

    private static final Set<PlacementJournalStateV2> TERMINAL_STATES = EnumSet.of(
            PlacementJournalStateV2.APPLIED,
            PlacementJournalStateV2.ROLLED_BACK,
            PlacementJournalStateV2.UNDONE);

    private static final Set<PlacementJournalStateV2> MUTATED_STATES = EnumSet.of(
            PlacementJournalStateV2.APPLYING,
            PlacementJournalStateV2.SETTLING,
            PlacementJournalStateV2.VERIFYING,
            PlacementJournalStateV2.ROLLING_BACK,
            PlacementJournalStateV2.UNDOING,
            PlacementJournalStateV2.RECOVERY_REQUIRED);

    private final PlacementSnapshotAllCompilerV2 snapshotCompiler;
    private final FilePlacementSafetyStoreV2 safetyStore;
    private final PlacementWorldGatewayV2 gateway;
    private final PlacementJournalStoreV2 journalStore;
    private final PlacementRollbackServiceV2 rollbackService;
    private final Clock clock;
    private final PlacementRecoveryLimitsV2 limits;
    private final PlacementSnapshotFileCodecV2 snapshotCodec = new PlacementSnapshotFileCodecV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public PlacementRecoveryServiceV2(
            PlacementSnapshotAllCompilerV2 snapshotCompiler,
            FilePlacementSafetyStoreV2 safetyStore,
            PlacementWorldGatewayV2 gateway,
            PlacementJournalStoreV2 journalStore,
            PlacementRollbackServiceV2 rollbackService,
            Clock clock
    ) {
        this(snapshotCompiler, safetyStore, gateway, journalStore, rollbackService, clock,
                PlacementRecoveryLimitsV2.defaults());
    }

    public PlacementRecoveryServiceV2(
            PlacementSnapshotAllCompilerV2 snapshotCompiler,
            FilePlacementSafetyStoreV2 safetyStore,
            PlacementWorldGatewayV2 gateway,
            PlacementJournalStoreV2 journalStore,
            PlacementRollbackServiceV2 rollbackService,
            Clock clock,
            PlacementRecoveryLimitsV2 limits
    ) {
        this.snapshotCompiler = Objects.requireNonNull(snapshotCompiler, "snapshotCompiler");
        this.safetyStore = Objects.requireNonNull(safetyStore, "safetyStore");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.journalStore = Objects.requireNonNull(journalStore, "journalStore");
        this.rollbackService = Objects.requireNonNull(rollbackService, "rollbackService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    // --- diagnose --------------------------------------------------------------------------------

    /**
     * Classifies one persisted journal without mutating anything. The result is deterministic for
     * the same journal, published artifacts, and world content.
     */
    public PlacementRecoveryDiagnosisV2 diagnose(PlacementRecoveryDiagnoseRequestV2 request) {
        Objects.requireNonNull(request, "request");
        requireNotCancelled(request.cancellation(), false);
        PlacementJournalV2 journal = request.journal();
        List<String> findings = new ArrayList<>();

        if (!journal.planChecksum().equals(request.placementPlan().canonicalChecksum())
                || !journal.plan().canonicalChecksum()
                .equals(request.placementPlan().canonicalChecksum())) {
            findings.add("journal plan binding does not match the supplied placement plan");
            return manual(journal, findings);
        }
        PlacementJournalStateV2 state = journal.state();
        if (PRE_MUTATION_STATES.contains(state)) {
            findings.add("journal proves no world mutation; leases may be released without world action");
            return new PlacementRecoveryDiagnosisV2(
                    PlacementRecoveryClassificationV2.NO_WORLD_MUTATION,
                    List.of(PlacementRecoveryActionV2.RELEASE_LEASES),
                    findings,
                    state,
                    journal.journalChecksum(),
                    "", "", "", 0L);
        }
        if (TERMINAL_STATES.contains(state)) {
            boolean cleanupEligible = state != PlacementJournalStateV2.APPLIED;
            findings.add(cleanupEligible
                    ? "journal is terminal; retained snapshots are eligible for confirmed cleanup"
                    : "journal is terminal APPLIED; snapshots are retained for a future Undo");
            return new PlacementRecoveryDiagnosisV2(
                    PlacementRecoveryClassificationV2.ALREADY_TERMINAL,
                    List.of(cleanupEligible
                            ? PlacementRecoveryActionV2.CLEANUP_SNAPSHOTS
                            : PlacementRecoveryActionV2.NONE),
                    findings,
                    state,
                    journal.journalChecksum(),
                    "", "", "", 0L);
        }
        if (!MUTATED_STATES.contains(state)) {
            findings.add("journal state has no recovery classification: " + state);
            return manual(journal, findings);
        }

        // World may have been mutated: every world action needs verified snapshot evidence,
        // a durable owned lease, and a bounded exact world scan.
        PlacementSnapshotPlanV2 snapshot = request.snapshotPlan();
        if (snapshot == null) {
            findings.add("published snapshot plan evidence is missing for a mutated journal state");
            return manual(journal, findings);
        }
        try {
            snapshot.requireBindings(
                    request.placementPlan(), request.envelopePlan(), request.reservationPlan());
        } catch (IllegalArgumentException mismatch) {
            findings.add("snapshot plan binding mismatch: " + mismatch.getMessage());
            return manual(journal, findings);
        }
        try {
            safetyStore.assertOwned(
                    request.placementPlan().placementId(), request.placementPlan().actor());
        } catch (PlacementReservationExceptionV2 | IOException missing) {
            findings.add("durable reservation lease is missing or foreign: " + missing.getMessage());
            return manual(journal, findings);
        }
        admitScan(request.envelopePlan().unionEffectEnvelope());

        Baseline baseline;
        try {
            baseline = buildBaseline(
                    request.placementPlan(),
                    request.envelopePlan(),
                    request.reservationPlan(),
                    snapshot,
                    request.cancellation());
        } catch (PlacementRecoveryExceptionV2 failure) {
            if (failure.code() == PlacementRecoveryFailureCodeV2.CANCELLED) {
                throw failure;
            }
            findings.add("published snapshot evidence failed strict re-verification ("
                    + failure.code() + "): " + failure.getMessage());
            return manual(journal, findings);
        }

        WorldScan scan = scanWorld(
                request.placementPlan(),
                request.envelopePlan().unionEffectEnvelope(),
                request.cancellation());

        String expectedApplied = "";
        if (request.blockSource() != null) {
            expectedApplied = expectedAppliedChecksum(request, baseline, findings);
        } else {
            findings.add("no canonical block source supplied; accept eligibility was not evaluated");
        }

        if (scan.checksum().equals(baseline.checksum())) {
            findings.add("current world already matches the snapshot baseline over the full envelope");
        }
        if (!expectedApplied.isEmpty() && scan.checksum().equals(expectedApplied)) {
            findings.add("current world exactly matches the expected applied stream over the full envelope");
            return new PlacementRecoveryDiagnosisV2(
                    PlacementRecoveryClassificationV2.SAFE_TO_ACCEPT,
                    List.of(PlacementRecoveryActionV2.ACCEPT, PlacementRecoveryActionV2.ROLLBACK),
                    findings,
                    state,
                    journal.journalChecksum(),
                    baseline.checksum(),
                    scan.checksum(),
                    expectedApplied,
                    scan.scannedBlocks());
        }
        findings.add("snapshot evidence verified; reverse-order restore of the effect envelope is safe");
        return new PlacementRecoveryDiagnosisV2(
                PlacementRecoveryClassificationV2.SAFE_TO_ROLLBACK,
                List.of(PlacementRecoveryActionV2.ROLLBACK),
                findings,
                state,
                journal.journalChecksum(),
                baseline.checksum(),
                scan.checksum(),
                expectedApplied,
                scan.scannedBlocks());
    }

    // --- prepare ---------------------------------------------------------------------------------

    /**
     * Issues an actor-bound one-time RECOVERY_ROLLBACK／RECOVERY_ACCEPT confirmation into a sealed
     * {@link PlacementRecoveryPlanV2}. Never mutates the world or the persisted journal.
     */
    public PreparedRecoveryV2 prepare(PlacementRecoveryPrepareRequestV2 request) {
        Objects.requireNonNull(request, "request");
        requireNotCancelled(request.cancellation(), false);
        PlacementRecoveryDiagnosisV2 diagnosis = request.diagnosis();
        PlacementJournalV2 journal = request.journal();
        if (!diagnosis.journalChecksum().equals(journal.journalChecksum())
                || diagnosis.journalState() != journal.state()) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.STATE_MISMATCH,
                    "diagnosis is stale: the persisted journal has changed since it was taken",
                    false);
        }
        if (request.action() != PlacementRecoveryActionV2.ROLLBACK
                && request.action() != PlacementRecoveryActionV2.ACCEPT) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CLASSIFICATION_MISMATCH,
                    "only ROLLBACK and ACCEPT can be prepared as confirmation-bound recovery",
                    false);
        }
        if (!diagnosis.permits(request.action())) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CLASSIFICATION_MISMATCH,
                    "diagnosis " + diagnosis.classification() + " does not permit " + request.action(),
                    false);
        }
        try {
            request.snapshotPlan().requireBindings(
                    request.placementPlan(), request.envelopePlan(), request.reservationPlan());
        } catch (IllegalArgumentException mismatch) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.BINDING_MISMATCH,
                    mismatch.getMessage(),
                    false,
                    mismatch);
        }
        PlacementPlanV2.PlacementActorV2 actor = request.actor() == null
                ? request.placementPlan().actor()
                : request.actor();
        String plaintextToken = request.plaintextToken() == null
                ? PlacementConfirmationBinderV2.newPlaintextToken()
                : request.plaintextToken();
        PlacementConfirmationActionV2 confirmationAction =
                request.action() == PlacementRecoveryActionV2.ACCEPT
                        ? PlacementConfirmationActionV2.RECOVERY_ACCEPT
                        : PlacementConfirmationActionV2.RECOVERY_ROLLBACK;
        Instant now = clock.instant();
        Instant expiresAt = now.plus(PlacementConfirmationBinderV2.CONFIRMATION_TTL);
        String confirmationHash = PlacementConfirmationBinderV2.confirmationHash(
                confirmationAction,
                request.placementPlan(),
                request.envelopePlan(),
                request.reservationPlan(),
                actor,
                now,
                expiresAt,
                plaintextToken);
        PlacementRecoveryPlanV2 plan = codec.sealPlacementRecoveryPlan(new PlacementRecoveryPlanV2(
                PlacementRecoveryPlanV2.VERSION,
                PlacementRecoveryPlanV2.RECOVERY_CONTRACT_VERSION,
                request.placementPlan().placementId(),
                request.placementPlan().operationId(),
                request.placementPlan().target().worldId(),
                actor,
                diagnosis.classification(),
                request.placementPlan().canonicalChecksum(),
                request.envelopePlan().canonicalChecksum(),
                request.reservationPlan().canonicalChecksum(),
                request.snapshotPlan().canonicalChecksum(),
                journal.journalChecksum(),
                journal.state(),
                diagnosis.baselineChecksum(),
                diagnosis.observedWorldChecksum(),
                diagnosis.expectedAppliedStreamChecksum(),
                confirmationAction,
                confirmationHash,
                now.toString(),
                expiresAt.toString(),
                PlacementRecoveryPlanV2.RetentionTransitionV2.KEEP_SNAPSHOTS_FOR_CLEANUP,
                now.toString(),
                PlacementPlanV2.UNBOUND_CHECKSUM));
        plan.requireBindings(
                request.placementPlan(),
                request.envelopePlan(),
                request.reservationPlan(),
                request.snapshotPlan(),
                journal);
        return new PreparedRecoveryV2(plan, plaintextToken);
    }

    // --- execute: rollback -----------------------------------------------------------------------

    /**
     * Consumes the RECOVERY_ROLLBACK confirmation, reconciles a late interrupted journal into
     * {@code RECOVERY_REQUIRED} deterministically, and delegates the restore to the V2-6-08
     * rollback transaction (reverse canonical order, bounded settle, full baseline verify).
     */
    public CompletionStage<PlacementRollbackServiceV2.RollbackResultV2> executeRollback(
            PlacementRecoveryRollbackRequestV2 request
    ) {
        Objects.requireNonNull(request, "request");
        requireNotCancelled(request.cancellation(), false);
        verifyAndConsumeConfirmation(
                request.recoveryPlan(),
                PlacementConfirmationActionV2.RECOVERY_ROLLBACK,
                request.placementPlan(),
                request.envelopePlan(),
                request.reservationPlan(),
                request.snapshotPlan(),
                request.journal(),
                request.actor(),
                request.plaintextToken());
        PlacementJournalV2 reconciled = reconcileForRollback(request.journal());
        return rollbackService.rollback(new PlacementRollbackRequestV2(
                request.placementPlan(),
                request.envelopePlan(),
                request.reservationPlan(),
                request.snapshotPlan(),
                reconciled,
                request.policy(),
                request.cancellation()));
    }

    /**
     * Deterministic late-operation reconciliation: an interrupted APPLYING／SETTLING／VERIFYING／
     * ROLLING_BACK／UNDOING journal (or a RECOVERY_REQUIRED journal carrying restore progress) is
     * resealed as {@code RECOVERY_REQUIRED} with every tile mapped onto the snapshot-backed
     * restore states — VERIFIED→APPLIED and RESTORED→SNAPSHOTTED — so the whole effect envelope
     * is restored again from the verified snapshots. Tiles without snapshot evidence stop recovery.
     */
    private PlacementJournalV2 reconcileForRollback(PlacementJournalV2 journal) {
        boolean needsReseal = journal.state() != PlacementJournalStateV2.RECOVERY_REQUIRED;
        List<PlacementJournalV2.PlacementTileEntryV2> entries = new ArrayList<>(journal.tiles().size());
        for (PlacementJournalV2.PlacementTileEntryV2 tile : journal.tiles()) {
            PlacementTileStateV2 mapped = switch (tile.state()) {
                case SNAPSHOTTED, APPLIED -> tile.state();
                case VERIFIED -> PlacementTileStateV2.APPLIED;
                case RESTORED -> PlacementTileStateV2.SNAPSHOTTED;
                case PENDING -> throw new PlacementRecoveryExceptionV2(
                        PlacementRecoveryFailureCodeV2.STATE_MISMATCH,
                        "tile " + tile.tileId() + " has no snapshot evidence and cannot be restored",
                        false);
            };
            if (mapped != tile.state()) {
                needsReseal = true;
            }
            entries.add(new PlacementJournalV2.PlacementTileEntryV2(
                    tile.tileId(), tile.tileIndex(), mapped, tile.snapshotFile(), tile.snapshotChecksum()));
        }
        if (!needsReseal) {
            return journal;
        }
        PlacementJournalV2 reconciled = codec.sealPlacementJournal(new PlacementJournalV2(
                PlacementJournalV2.VERSION,
                PlacementJournalV2.JOURNAL_CONTRACT_VERSION,
                journal.plan(),
                journal.planChecksum(),
                PlacementJournalStateV2.RECOVERY_REQUIRED,
                entries,
                journal.reservedBytes(),
                journal.snapshotBytesUsed(),
                clock.instant().toString(),
                "Release 2 recovery reconciled the interrupted " + journal.state()
                        + " operation for reverse-order rollback",
                PlacementPlanV2.UNBOUND_CHECKSUM));
        saveJournal(reconciled);
        return reconciled;
    }

    // --- execute: accept -------------------------------------------------------------------------

    /**
     * Consumes the RECOVERY_ACCEPT confirmation and accepts the placement as applied only after a
     * fresh full exact world scan again matches the expected applied stream rebuilt from the
     * verified snapshots and the canonical Release block source. Never accepts on drift and never
     * mutates the world; snapshots stay retained for Undo／cleanup.
     */
    public AcceptResultV2 executeAccept(PlacementRecoveryAcceptRequestV2 request) {
        Objects.requireNonNull(request, "request");
        requireNotCancelled(request.cancellation(), false);
        verifyAndConsumeConfirmation(
                request.recoveryPlan(),
                PlacementConfirmationActionV2.RECOVERY_ACCEPT,
                request.placementPlan(),
                request.envelopePlan(),
                request.reservationPlan(),
                request.snapshotPlan(),
                request.journal(),
                request.actor(),
                request.plaintextToken());
        admitScan(request.envelopePlan().unionEffectEnvelope());
        Baseline baseline = buildBaseline(
                request.placementPlan(),
                request.envelopePlan(),
                request.reservationPlan(),
                request.snapshotPlan(),
                request.cancellation());
        if (!baseline.checksum().equals(request.recoveryPlan().baselineChecksum())) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.SNAPSHOT_TAMPERED,
                    "snapshot baseline differs from the sealed recovery plan evidence",
                    false);
        }
        String expectedApplied;
        try {
            expectedApplied = new PlacementExpectedBlockResolverV2(
                    request.placementPlan(),
                    request.envelopePlan(),
                    request.blockSource(),
                    baseline::stateAt)
                    .streamChecksum();
        } catch (PlacementVerifyExceptionV2 failure) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.BINDING_MISMATCH,
                    "unable to rebuild the expected applied stream: " + failure.getMessage(),
                    false,
                    failure);
        }
        if (!expectedApplied.equals(request.recoveryPlan().expectedAppliedStreamChecksum())) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.BINDING_MISMATCH,
                    "expected applied stream differs from the sealed recovery plan evidence",
                    false);
        }
        WorldScan scan = scanWorld(
                request.placementPlan(),
                request.envelopePlan().unionEffectEnvelope(),
                request.cancellation());
        if (!scan.checksum().equals(expectedApplied)
                || scan.scannedBlocks()
                != request.envelopePlan().unionEffectEnvelope().volumeBlocks()) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.WORLD_DRIFT,
                    "current world no longer matches the expected applied stream; accept refused",
                    false);
        }
        List<PlacementJournalV2.PlacementTileEntryV2> verified =
                new ArrayList<>(request.journal().tiles().size());
        for (PlacementJournalV2.PlacementTileEntryV2 tile : request.journal().tiles()) {
            verified.add(new PlacementJournalV2.PlacementTileEntryV2(
                    tile.tileId(),
                    tile.tileIndex(),
                    PlacementTileStateV2.VERIFIED,
                    tile.snapshotFile(),
                    tile.snapshotChecksum()));
        }
        PlacementJournalV2 accepted = codec.sealPlacementJournal(new PlacementJournalV2(
                PlacementJournalV2.VERSION,
                PlacementJournalV2.JOURNAL_CONTRACT_VERSION,
                request.journal().plan(),
                request.journal().planChecksum(),
                PlacementJournalStateV2.APPLIED,
                verified,
                request.journal().reservedBytes(),
                request.journal().snapshotBytesUsed(),
                clock.instant().toString(),
                "Release 2 recovery accepted the placement: a full exact world scan matched the"
                        + " expected applied stream; snapshots retained for Undo／cleanup",
                PlacementPlanV2.UNBOUND_CHECKSUM));
        saveJournal(accepted);
        try {
            safetyStore.release(request.placementPlan().placementId());
        } catch (IOException | RuntimeException releaseFailure) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.RESERVATION_RELEASE_FAILED,
                    "recovery accept sealed APPLIED but the reservation lease release failed",
                    false,
                    releaseFailure);
        }
        return new AcceptResultV2(
                accepted,
                scan.scannedBlocks(),
                baseline.checksum(),
                expectedApplied,
                scan.checksum());
    }

    // --- cleanup retention -----------------------------------------------------------------------

    /**
     * Dry-run: lists the retained snapshot files of one terminal ({@code ROLLED_BACK}／
     * {@code UNDONE}) placement within the retention budget. Deletes nothing.
     */
    public PlacementRecoveryCleanupPlanV2 planCleanup(
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    ) {
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(cancellation, "cancellation");
        requireNotCancelled(cancellation, false);
        requireCleanupEligible(placementPlan, journal);
        List<PlacementRecoveryCleanupPlanV2.SnapshotFileEntryV2> files =
                listSnapshotFiles(placementPlan);
        long totalBytes = 0L;
        for (PlacementRecoveryCleanupPlanV2.SnapshotFileEntryV2 file : files) {
            totalBytes = Math.addExact(totalBytes, file.sizeBytes());
        }
        if (files.size() > limits.maximumRetentionFiles()
                || totalBytes > limits.maximumRetentionBytes()) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.RESOURCE_BUDGET_EXCEEDED,
                    "snapshot cleanup exceeds the retention budget",
                    false);
        }
        return PlacementRecoveryCleanupPlanV2.sealed(
                placementPlan.placementId(), journal.journalChecksum(), files, totalBytes);
    }

    /**
     * Deletes exactly the snapshot files listed in a still-current dry-run cleanup plan. Refuses
     * when the journal or the on-disk file set changed since the dry run.
     */
    public long executeCleanup(
            PlacementRecoveryCleanupPlanV2 cleanupPlan,
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    ) {
        Objects.requireNonNull(cleanupPlan, "cleanupPlan");
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(cancellation, "cancellation");
        requireNotCancelled(cancellation, false);
        requireCleanupEligible(placementPlan, journal);
        if (!cleanupPlan.placementId().equals(placementPlan.placementId())
                || !cleanupPlan.journalChecksum().equals(journal.journalChecksum())) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CLEANUP_PLAN_STALE,
                    "cleanup plan does not bind the supplied placement journal",
                    false);
        }
        List<PlacementRecoveryCleanupPlanV2.SnapshotFileEntryV2> current =
                listSnapshotFiles(placementPlan);
        if (!current.equals(cleanupPlan.files())) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CLEANUP_PLAN_STALE,
                    "retained snapshot files changed since the dry-run cleanup plan",
                    false);
        }
        Path directory = snapshotDirectory(placementPlan);
        long freedBytes = 0L;
        try {
            for (PlacementRecoveryCleanupPlanV2.SnapshotFileEntryV2 file : cleanupPlan.files()) {
                requireNotCancelled(cancellation, false);
                Files.delete(directory.resolve(file.fileName()));
                freedBytes = Math.addExact(freedBytes, file.sizeBytes());
            }
            if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                try (DirectoryStream<Path> remaining = Files.newDirectoryStream(directory)) {
                    if (!remaining.iterator().hasNext()) {
                        Files.delete(directory);
                    }
                }
            }
        } catch (IOException failure) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CLEANUP_PLAN_STALE,
                    "snapshot cleanup failed while deleting planned files: " + failure.getMessage(),
                    false,
                    failure);
        }
        return freedBytes;
    }

    private void requireCleanupEligible(PlacementPlanV2 placementPlan, PlacementJournalV2 journal) {
        if (!journal.planChecksum().equals(placementPlan.canonicalChecksum())) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.BINDING_MISMATCH,
                    "cleanup journal does not bind the supplied placement plan",
                    false);
        }
        if (journal.state() != PlacementJournalStateV2.ROLLED_BACK
                && journal.state() != PlacementJournalStateV2.UNDONE) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CLEANUP_NOT_ELIGIBLE,
                    "snapshot cleanup requires a terminal ROLLED_BACK or UNDONE journal, found "
                            + journal.state(),
                    false);
        }
    }

    private List<PlacementRecoveryCleanupPlanV2.SnapshotFileEntryV2> listSnapshotFiles(
            PlacementPlanV2 placementPlan
    ) {
        Path directory = snapshotDirectory(placementPlan);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<PlacementRecoveryCleanupPlanV2.SnapshotFileEntryV2> files = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(entry)) {
                    throw new PlacementRecoveryExceptionV2(
                            PlacementRecoveryFailureCodeV2.CLEANUP_NOT_ELIGIBLE,
                            "snapshot directory contains a non-regular entry: "
                                    + entry.getFileName(),
                            false);
                }
                if (files.size() >= limits.maximumRetentionFiles()) {
                    throw new PlacementRecoveryExceptionV2(
                            PlacementRecoveryFailureCodeV2.RESOURCE_BUDGET_EXCEEDED,
                            "snapshot cleanup exceeds the retention file budget",
                            false);
                }
                files.add(new PlacementRecoveryCleanupPlanV2.SnapshotFileEntryV2(
                        entry.getFileName().toString(), Files.size(entry)));
            }
        } catch (IOException failure) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CLEANUP_NOT_ELIGIBLE,
                    "unable to list retained snapshot files: " + failure.getMessage(),
                    false,
                    failure);
        }
        files.sort(Comparator.comparing(PlacementRecoveryCleanupPlanV2.SnapshotFileEntryV2::fileName));
        return List.copyOf(files);
    }

    private Path snapshotDirectory(PlacementPlanV2 placementPlan) {
        return safetyStore.snapshotsRoot().resolve(placementPlan.placementId().toString());
    }

    // --- shared evidence helpers -----------------------------------------------------------------

    private void verifyAndConsumeConfirmation(
            PlacementRecoveryPlanV2 recoveryPlan,
            PlacementConfirmationActionV2 requiredAction,
            PlacementPlanV2 placementPlan,
            PlacementEnvelopePlanV2 envelopePlan,
            PlacementReservationPlanV2 reservationPlan,
            PlacementSnapshotPlanV2 snapshotPlan,
            PlacementJournalV2 journal,
            PlacementPlanV2.PlacementActorV2 actor,
            String plaintextToken
    ) {
        try {
            recoveryPlan.requireBindings(placementPlan, envelopePlan, reservationPlan, snapshotPlan, journal);
        } catch (IllegalArgumentException mismatch) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.BINDING_MISMATCH,
                    mismatch.getMessage(),
                    false,
                    mismatch);
        }
        if (recoveryPlan.confirmationAction() != requiredAction) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CLASSIFICATION_MISMATCH,
                    "recovery plan was prepared for " + recoveryPlan.confirmationAction()
                            + ", not " + requiredAction,
                    false);
        }
        if (!recoveryPlan.actor().equals(actor)) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.ACTOR_MISMATCH,
                    "recovery confirmation belongs to a different actor",
                    false);
        }
        Instant createdAt = Instant.parse(recoveryPlan.confirmationCreatedAt());
        Instant expiresAt = Instant.parse(recoveryPlan.confirmationExpiresAt());
        if (!clock.instant().isBefore(expiresAt)) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CONFIRMATION_EXPIRED,
                    "recovery confirmation token has expired",
                    false);
        }
        String expected = PlacementConfirmationBinderV2.confirmationHash(
                recoveryPlan.confirmationAction(),
                placementPlan,
                envelopePlan,
                reservationPlan,
                actor,
                createdAt,
                expiresAt,
                plaintextToken);
        if (!MessageDigest.isEqual(
                recoveryPlan.confirmationHash().getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII))) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CONFIRMATION_INVALID,
                    "recovery confirmation token is invalid",
                    false);
        }
        try {
            safetyStore.markConfirmationConsumed(recoveryPlan.confirmationHash());
        } catch (PlacementReservationExceptionV2 consumed) {
            PlacementRecoveryFailureCodeV2 code = switch (consumed.failureCode()) {
                case CONFIRMATION_REPLAY -> PlacementRecoveryFailureCodeV2.CONFIRMATION_REPLAY;
                case ENTRY_BUDGET_EXCEEDED -> PlacementRecoveryFailureCodeV2.RESOURCE_BUDGET_EXCEEDED;
                default -> PlacementRecoveryFailureCodeV2.CONFIRMATION_INVALID;
            };
            throw new PlacementRecoveryExceptionV2(code, consumed.getMessage(), false, consumed);
        } catch (IOException io) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CONFIRMATION_INVALID,
                    "unable to consume the recovery confirmation: " + io.getMessage(),
                    false,
                    io);
        }
        try {
            safetyStore.assertOwned(placementPlan.placementId(), placementPlan.actor());
        } catch (PlacementReservationExceptionV2 | IOException missing) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.RESERVATION_MISSING,
                    "durable reservation lease is missing or foreign: " + missing.getMessage(),
                    false,
                    missing);
        }
    }

    private void admitScan(WorldAabbV2 envelope) {
        if (envelope.volumeBlocks() > limits.maximumScannedBlocks()) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.RESOURCE_BUDGET_EXCEEDED,
                    "union effect envelope exceeds the recovery scan budget",
                    false);
        }
    }

    /**
     * Strictly re-verifies the published snapshot index and every snapshot file, then materializes
     * the baseline for the union effect envelope, rejecting overlap disagreement and coverage gaps.
     */
    private Baseline buildBaseline(
            PlacementPlanV2 placementPlan,
            PlacementEnvelopePlanV2 envelopePlan,
            PlacementReservationPlanV2 reservationPlan,
            PlacementSnapshotPlanV2 snapshotPlan,
            CancellationToken cancellation
    ) {
        PlacementSnapshotPlanV2 published;
        try {
            published = snapshotCompiler.loadPublished(
                    placementPlan, envelopePlan, reservationPlan, cancellation);
        } catch (PlacementSnapshotExceptionV2 snapshotFailure) {
            throw new PlacementRecoveryExceptionV2(
                    classifySnapshotFailure(snapshotFailure),
                    "published snapshot failed strict re-verification: " + snapshotFailure.getMessage(),
                    false,
                    snapshotFailure);
        }
        if (!published.canonicalChecksum().equals(snapshotPlan.canonicalChecksum())) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.BINDING_MISMATCH,
                    "published snapshot index differs from the supplied snapshot plan",
                    false);
        }
        Path publishedDirectory = snapshotDirectory(placementPlan);
        Map<Coord, String> baseline = new HashMap<>();
        for (PlacementSnapshotPlanV2.TileSnapshotV2 tile : published.tiles()) {
            WorldAabbV2 region = tile.effectAabb();
            PlacementSnapshotFileCodecV2.WrittenTileSnapshotV2 read;
            try {
                read = snapshotCodec.readStrict(
                        publishedDirectory.resolve(tile.snapshotFile()),
                        placementPlan.target().worldId(),
                        tile.tileId(),
                        region,
                        PlacementSnapshotPlanV2.MAXIMUM_PALETTE_ENTRIES_PER_TILE,
                        cancellation,
                        (index, state) -> {
                            int[] xyz = decodeCanonicalIndex(region, index);
                            Coord coord = new Coord(xyz[0], xyz[1], xyz[2]);
                            String existing = baseline.putIfAbsent(coord, state);
                            if (existing != null && !existing.equals(state)) {
                                throw new PlacementRecoveryExceptionV2(
                                        PlacementRecoveryFailureCodeV2.SNAPSHOT_TAMPERED,
                                        "overlapping snapshot tiles disagree at ("
                                                + xyz[0] + "," + xyz[1] + "," + xyz[2] + ")",
                                        false);
                            }
                        });
            } catch (PlacementSnapshotExceptionV2 snapshotFailure) {
                throw new PlacementRecoveryExceptionV2(
                        classifySnapshotFailure(snapshotFailure),
                        "snapshot file failed strict decode for " + tile.tileId() + ": "
                                + snapshotFailure.getMessage(),
                        false,
                        snapshotFailure);
            } catch (IOException io) {
                throw new PlacementRecoveryExceptionV2(
                        PlacementRecoveryFailureCodeV2.SNAPSHOT_TAMPERED,
                        "snapshot file unreadable for " + tile.tileId(),
                        false,
                        io);
            }
            if (!read.artifactChecksum().equals(tile.artifactChecksum())
                    || !read.blockStateStreamChecksum().equals(tile.blockStateStreamChecksum())) {
                throw new PlacementRecoveryExceptionV2(
                        PlacementRecoveryFailureCodeV2.SNAPSHOT_TAMPERED,
                        "snapshot checksum mismatch against the sealed index for " + tile.tileId(),
                        false);
            }
        }
        WorldAabbV2 union = envelopePlan.unionEffectEnvelope();
        MessageDigest digest = sha256();
        for (int y = union.minY(); y <= union.maxY(); y++) {
            for (int z = union.minZ(); z <= union.maxZ(); z++) {
                for (int x = union.minX(); x <= union.maxX(); x++) {
                    String state = baseline.get(new Coord(x, y, z));
                    if (state == null) {
                        throw new PlacementRecoveryExceptionV2(
                                PlacementRecoveryFailureCodeV2.SNAPSHOT_COVERAGE_GAP,
                                "snapshot tiles do not cover the union effect envelope at ("
                                        + x + "," + y + "," + z + ")",
                                false);
                    }
                    digest.update(state.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
            }
        }
        return new Baseline(Map.copyOf(baseline), HexFormat.of().formatHex(digest.digest()));
    }

    /** Reads the current world once over the union effect envelope in canonical X→Z→Y order. */
    private WorldScan scanWorld(
            PlacementPlanV2 placementPlan,
            WorldAabbV2 envelope,
            CancellationToken cancellation
    ) {
        MessageDigest digest = sha256();
        long[] scanned = {0L};
        try {
            gateway.streamRegionBlockStates(
                    placementPlan.target().worldId(),
                    envelope,
                    (x, y, z, state) -> {
                        requireNotCancelled(cancellation, false);
                        if (scanned[0] >= limits.maximumScannedBlocks()) {
                            throw new PlacementRecoveryExceptionV2(
                                    PlacementRecoveryFailureCodeV2.RESOURCE_BUDGET_EXCEEDED,
                                    "world evidence scan exceeded the recovery scan budget",
                                    false);
                        }
                        digest.update(state.getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) '\n');
                        scanned[0]++;
                    });
        } catch (IOException failure) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.GATEWAY_FAILURE,
                    "world gateway failed during the recovery evidence scan",
                    false,
                    failure);
        }
        if (scanned[0] != envelope.volumeBlocks()) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.GATEWAY_FAILURE,
                    "world gateway did not stream the exact union effect envelope",
                    false);
        }
        return new WorldScan(HexFormat.of().formatHex(digest.digest()), scanned[0]);
    }

    /**
     * Rebuilds the expected applied stream from the verified baseline and the canonical Release
     * block source after matching the source binding against the placement plan's manifest
     * checksum, canonical capability set, and overlay ordinals. On any mismatch a finding is
     * recorded and accept eligibility is withheld.
     */
    private String expectedAppliedChecksum(
            PlacementRecoveryDiagnoseRequestV2 request,
            Baseline baseline,
            List<String> findings
    ) {
        var binding = request.blockSource().binding();
        PlacementPlanV2 plan = request.placementPlan();
        if (!binding.releaseManifestChecksum().equals(plan.releaseBinding().manifestChecksum())) {
            findings.add("block source manifest checksum does not match the placement plan release binding");
            return "";
        }
        if (!binding.requiredCapabilities().equals(plan.requiredCapabilities())) {
            findings.add("block source capability set does not match the placement plan capabilities");
            return "";
        }
        try {
            return new PlacementExpectedBlockResolverV2(
                    plan, request.envelopePlan(), request.blockSource(), baseline::stateAt)
                    .streamChecksum();
        } catch (PlacementVerifyExceptionV2 failure) {
            findings.add("expected applied stream could not be rebuilt from the block source: "
                    + failure.getMessage());
            return "";
        }
    }

    private PlacementRecoveryDiagnosisV2 manual(PlacementJournalV2 journal, List<String> findings) {
        if (findings.size() > limits.maximumFindings()) {
            findings = findings.subList(0, limits.maximumFindings());
        }
        return new PlacementRecoveryDiagnosisV2(
                PlacementRecoveryClassificationV2.MANUAL_INTERVENTION_REQUIRED,
                List.of(),
                findings,
                journal.state(),
                journal.journalChecksum(),
                "", "", "", 0L);
    }

    private void saveJournal(PlacementJournalV2 journal) {
        try {
            journalStore.save(journal);
        } catch (IOException | RuntimeException exception) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.JOURNAL_PERSISTENCE_FAILED,
                    "Release 2 placement journal persistence failed during recovery",
                    false,
                    exception);
        }
    }

    private static void requireNotCancelled(CancellationToken cancellation, boolean mutated) {
        if (cancellation.isCancellationRequested()) {
            throw new PlacementRecoveryExceptionV2(
                    PlacementRecoveryFailureCodeV2.CANCELLED,
                    "recovery was cancelled",
                    mutated);
        }
    }

    private static PlacementRecoveryFailureCodeV2 classifySnapshotFailure(
            PlacementSnapshotExceptionV2 failure
    ) {
        return switch (failure.failureCode()) {
            case STATE_MISMATCH -> PlacementRecoveryFailureCodeV2.SNAPSHOT_MISSING;
            case BINDING_MISMATCH -> PlacementRecoveryFailureCodeV2.BINDING_MISMATCH;
            case CANCELLED -> PlacementRecoveryFailureCodeV2.CANCELLED;
            default -> PlacementRecoveryFailureCodeV2.SNAPSHOT_TAMPERED;
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

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** Sealed recovery plan plus the one-time plaintext token (never persisted). */
    public record PreparedRecoveryV2(PlacementRecoveryPlanV2 recoveryPlan, String plaintextToken) {
        public PreparedRecoveryV2 {
            Objects.requireNonNull(recoveryPlan, "recoveryPlan");
            Objects.requireNonNull(plaintextToken, "plaintextToken");
        }
    }

    /** Terminal accept evidence returned only after APPLIED was sealed and the lease freed. */
    public record AcceptResultV2(
            PlacementJournalV2 acceptedJournal,
            long scannedBlocks,
            String baselineChecksum,
            String expectedAppliedStreamChecksum,
            String observedWorldChecksum
    ) {
        public AcceptResultV2 {
            Objects.requireNonNull(acceptedJournal, "acceptedJournal");
            Objects.requireNonNull(baselineChecksum, "baselineChecksum");
            Objects.requireNonNull(expectedAppliedStreamChecksum, "expectedAppliedStreamChecksum");
            Objects.requireNonNull(observedWorldChecksum, "observedWorldChecksum");
            if (scannedBlocks < 1) {
                throw new IllegalArgumentException("accept result requires a full scan");
            }
        }
    }

    private record Coord(int x, int y, int z) {
    }

    private record Baseline(Map<Coord, String> states, String checksum) {
        String stateAt(int x, int y, int z) {
            return states.get(new Coord(x, y, z));
        }
    }

    private record WorldScan(String checksum, long scannedBlocks) {
    }
}
