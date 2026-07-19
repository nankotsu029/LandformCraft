package com.github.nankotsu029.landformcraft.core.v2.placement.undo;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementUndoPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementVerifyEvidenceV2;

import java.util.Objects;

/**
 * Immutable Undo execute request for a terminal Release 2 {@code APPLIED} placement that already
 * has a prepared {@link PlacementUndoPlanV2}.
 */
public record PlacementUndoRequestV2(
        PlacementPlanV2 placementPlan,
        PlacementEnvelopePlanV2 envelopePlan,
        PlacementReservationPlanV2 applyReservationPlan,
        PlacementReservationPlanV2 undoReservationPlan,
        PlacementSnapshotPlanV2 snapshotPlan,
        PlacementVerifyEvidenceV2 verifyEvidence,
        PlacementUndoPlanV2 undoPlan,
        PlacementJournalV2 appliedJournal,
        PlacementJournalV2 applyCompleteJournal,
        PlacementSettleVerifyPolicyV2 policy,
        String plaintextToken,
        PlacementPlanV2.PlacementActorV2 actor,
        PlacementCanonicalBlockSourceV2 blockSource,
        CancellationToken cancellation
) {
    public PlacementUndoRequestV2 {
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(envelopePlan, "envelopePlan");
        Objects.requireNonNull(applyReservationPlan, "applyReservationPlan");
        Objects.requireNonNull(undoReservationPlan, "undoReservationPlan");
        Objects.requireNonNull(snapshotPlan, "snapshotPlan");
        Objects.requireNonNull(verifyEvidence, "verifyEvidence");
        Objects.requireNonNull(undoPlan, "undoPlan");
        Objects.requireNonNull(appliedJournal, "appliedJournal");
        Objects.requireNonNull(applyCompleteJournal, "applyCompleteJournal");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(plaintextToken, "plaintextToken");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(blockSource, "blockSource");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
