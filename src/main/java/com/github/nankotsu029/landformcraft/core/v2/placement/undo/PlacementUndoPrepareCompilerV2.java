package com.github.nankotsu029.landformcraft.core.v2.placement.undo;

import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementJournalStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementConfirmationBinderV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationFailureCodeV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementConfirmationActionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationLeaseStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationOperationV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementUndoPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementVerifyEvidenceV2;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Prepares an operation-bound Release 2 Undo (V2-6-09): reserves UNDO regions／disk, issues an
 * actor-bound UNDO confirmation into a sealed {@link PlacementUndoPlanV2}, and leaves the journal
 * in terminal {@code APPLIED} until execute. Never mutates the world and never rewrites the sealed
 * apply plan checksum binding.
 */
public final class PlacementUndoPrepareCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PlacementConfirmationBinderV2 binder = new PlacementConfirmationBinderV2();
    private final FilePlacementSafetyStoreV2 safetyStore;
    private final PlacementJournalStoreV2 journalStore;
    private final Clock clock;

    public PlacementUndoPrepareCompilerV2(
            FilePlacementSafetyStoreV2 safetyStore,
            PlacementJournalStoreV2 journalStore,
            Clock clock
    ) {
        this.safetyStore = Objects.requireNonNull(safetyStore, "safetyStore");
        this.journalStore = Objects.requireNonNull(journalStore, "journalStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PreparedUndoV2 prepare(PlacementUndoPrepareRequestV2 request) {
        Objects.requireNonNull(request, "request");
        PlacementJournalV2 applied = request.appliedJournal();
        if (applied.state() != PlacementJournalStateV2.APPLIED) {
            throw new PlacementUndoExceptionV2(
                    PlacementUndoFailureCodeV2.STATE_MISMATCH,
                    "undo prepare requires a terminal APPLIED journal",
                    false);
        }
        if (applied.tiles().stream().anyMatch(tile -> tile.state() != PlacementTileStateV2.VERIFIED)) {
            throw new PlacementUndoExceptionV2(
                    PlacementUndoFailureCodeV2.STATE_MISMATCH,
                    "undo prepare requires every tile VERIFIED",
                    false);
        }
        PlacementPlanV2 plan = request.placementPlan();
        PlacementEnvelopePlanV2 envelope = request.envelopePlan();
        PlacementReservationPlanV2 applyReservation = request.applyReservationPlan();
        PlacementSnapshotPlanV2 snapshot = request.snapshotPlan();
        PlacementVerifyEvidenceV2 evidence = request.verifyEvidence();
        PlacementJournalV2 applyComplete = request.applyCompleteJournal();
        if (!applied.planChecksum().equals(plan.canonicalChecksum())
                || !applied.plan().canonicalChecksum().equals(plan.canonicalChecksum())) {
            throw new PlacementUndoExceptionV2(
                    PlacementUndoFailureCodeV2.BINDING_MISMATCH,
                    "applied journal plan binding mismatch",
                    false);
        }
        try {
            snapshot.requireBindings(plan, envelope, applyReservation);
            evidence.requireBindings(plan, envelope, snapshot, applyComplete);
        } catch (IllegalArgumentException mismatch) {
            throw new PlacementUndoExceptionV2(
                    PlacementUndoFailureCodeV2.BINDING_MISMATCH,
                    mismatch.getMessage(),
                    false,
                    mismatch);
        }
        PlacementPlanV2.PlacementActorV2 actor = request.actor() == null
                ? plan.actor()
                : request.actor();

        Instant now = clock.instant();
        Instant expiresAt = now.plus(PlacementConfirmationBinderV2.CONFIRMATION_TTL);
        String storeKey;
        try {
            storeKey = safetyStore.fileStoreKey();
        } catch (IOException exception) {
            throw new PlacementUndoExceptionV2(
                    PlacementUndoFailureCodeV2.DISK_SHORTAGE,
                    "unable to probe snapshot FileStore: " + exception.getMessage(),
                    false,
                    exception);
        }

        List<PlacementReservationPlanV2.RegionLeaseV2> regions = new ArrayList<>(envelope.tiles().size());
        for (PlacementEnvelopePlanV2.TileEnvelopeV2 tile : envelope.tiles()) {
            regions.add(new PlacementReservationPlanV2.RegionLeaseV2(
                    tile.tileId(), tile.tileIndex(), tile.effectAabb()));
        }
        PlacementReservationPlanV2 undoDraft = new PlacementReservationPlanV2(
                PlacementReservationPlanV2.VERSION,
                PlacementReservationPlanV2.RESERVATION_CONTRACT_VERSION,
                plan.placementId(),
                plan.operationId(),
                plan.target().worldId(),
                PlacementReservationOperationV2.UNDO,
                actor,
                new PlacementReservationPlanV2.PlacementPlanBinding(
                        PlacementReservationPlanV2.PlacementPlanBinding.VERSION,
                        plan.canonicalChecksum(),
                        PlacementReservationPlanV2.PlacementPlanBinding.CONTRACT_VERSION),
                new PlacementReservationPlanV2.EnvelopeBinding(
                        PlacementReservationPlanV2.EnvelopeBinding.VERSION,
                        envelope.canonicalChecksum(),
                        envelope.mutationEnvelopeChecksum(),
                        PlacementReservationPlanV2.EnvelopeBinding.CONTRACT_VERSION),
                regions,
                new PlacementReservationPlanV2.DiskLeaseV2(
                        plan.placementId(),
                        storeKey,
                        applyReservation.diskLease().reservedBytes()),
                PlacementReservationLeaseStateV2.PLANNED,
                now.toString(),
                expiresAt.toString(),
                applyReservation.budget(),
                PlacementPlanV2.UNBOUND_CHECKSUM);
        PlacementReservationPlanV2 undoReservation = codec.sealPlacementReservationPlan(undoDraft);

        boolean reserved = false;
        try {
            safetyStore.reserve(
                    undoReservation,
                    PlacementReservationLeaseStateV2.PLANNED,
                    safetyStore.reservationFloorBytes());
            reserved = true;

            PlacementConfirmationBinderV2.IssuedConfirmationV2 issued = binder.issueUndo(
                    plan,
                    envelope,
                    undoReservation,
                    actor,
                    now,
                    request.plaintextToken() == null
                            ? PlacementConfirmationBinderV2.newPlaintextToken()
                            : request.plaintextToken());

            PlacementJournalV2 preparedJournal = codec.sealPlacementJournal(new PlacementJournalV2(
                    PlacementJournalV2.VERSION,
                    PlacementJournalV2.JOURNAL_CONTRACT_VERSION,
                    plan,
                    plan.canonicalChecksum(),
                    PlacementJournalStateV2.APPLIED,
                    applied.tiles(),
                    Math.max(applied.reservedBytes(), undoReservation.diskLease().reservedBytes()),
                    applied.snapshotBytesUsed(),
                    now.toString(),
                    "Release 2 Undo confirmation prepared; awaiting execute",
                    PlacementPlanV2.UNBOUND_CHECKSUM));

            PlacementUndoPlanV2 undoPlan = codec.sealPlacementUndoPlan(new PlacementUndoPlanV2(
                    PlacementUndoPlanV2.VERSION,
                    PlacementUndoPlanV2.UNDO_CONTRACT_VERSION,
                    plan.placementId(),
                    plan.operationId(),
                    plan.target().worldId(),
                    actor,
                    plan.canonicalChecksum(),
                    envelope.canonicalChecksum(),
                    applyReservation.canonicalChecksum(),
                    undoReservation.canonicalChecksum(),
                    snapshot.canonicalChecksum(),
                    evidence.canonicalChecksum(),
                    preparedJournal.journalChecksum(),
                    applyComplete.journalChecksum(),
                    evidence.expectedStreamChecksum(),
                    PlacementConfirmationActionV2.UNDO,
                    issued.confirmationHash(),
                    issued.createdAt(),
                    issued.expiresAt(),
                    PlacementUndoPlanV2.RetentionTransitionV2.KEEP_SNAPSHOTS_FOR_CLEANUP,
                    now.toString(),
                    PlacementPlanV2.UNBOUND_CHECKSUM));
            undoPlan.requireBindings(
                    plan,
                    envelope,
                    applyReservation,
                    undoReservation,
                    snapshot,
                    evidence,
                    preparedJournal,
                    applyComplete);
            journalStore.save(preparedJournal);

            return new PreparedUndoV2(
                    undoPlan,
                    undoReservation,
                    preparedJournal,
                    issued.plaintextToken());
        } catch (PlacementReservationExceptionV2 | PlacementUndoExceptionV2 exception) {
            if (reserved) {
                releaseQuietly(plan.placementId());
            }
            if (exception instanceof PlacementUndoExceptionV2 undoException) {
                throw undoException;
            }
            throw mapReservationFailure((PlacementReservationExceptionV2) exception);
        } catch (RuntimeException | IOException exception) {
            if (reserved) {
                releaseQuietly(plan.placementId());
            }
            throw new PlacementUndoExceptionV2(
                    PlacementUndoFailureCodeV2.RESERVATION_FAILED,
                    "undo prepare failed and leases were released: " + exception.getMessage(),
                    false,
                    exception);
        }
    }

    private void releaseQuietly(UUID placementId) {
        try {
            safetyStore.release(placementId);
        } catch (IOException | RuntimeException ignored) {
            // Best-effort rollback of the UNDO lease after prepare failure.
        }
    }

    private static PlacementUndoExceptionV2 mapReservationFailure(
            PlacementReservationExceptionV2 exception
    ) {
        PlacementUndoFailureCodeV2 code = switch (exception.failureCode()) {
            case REGION_OVERLAP, STATE_MISMATCH, TARGET_MISMATCH, CHECKSUM_MISMATCH ->
                    PlacementUndoFailureCodeV2.RESERVATION_FAILED;
            case DISK_SHORTAGE -> PlacementUndoFailureCodeV2.DISK_SHORTAGE;
            case ACTOR_MISMATCH -> PlacementUndoFailureCodeV2.ACTOR_MISMATCH;
            case CONFIRMATION_INVALID -> PlacementUndoFailureCodeV2.CONFIRMATION_INVALID;
            case CONFIRMATION_EXPIRED -> PlacementUndoFailureCodeV2.CONFIRMATION_EXPIRED;
            case CONFIRMATION_REPLAY -> PlacementUndoFailureCodeV2.CONFIRMATION_REPLAY;
            case ENTRY_BUDGET_EXCEEDED, PARTIAL_RESERVATION_ROLLED_BACK ->
                    PlacementUndoFailureCodeV2.RESOURCE_BUDGET_EXCEEDED;
        };
        return new PlacementUndoExceptionV2(code, exception.getMessage(), false, exception);
    }

    public record PreparedUndoV2(
            PlacementUndoPlanV2 undoPlan,
            PlacementReservationPlanV2 undoReservation,
            PlacementJournalV2 preparedJournal,
            String plaintextToken
    ) {
        public PreparedUndoV2 {
            Objects.requireNonNull(undoPlan, "undoPlan");
            Objects.requireNonNull(undoReservation, "undoReservation");
            Objects.requireNonNull(preparedJournal, "preparedJournal");
            Objects.requireNonNull(plaintextToken, "plaintextToken");
        }
    }
}
