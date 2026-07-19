package com.github.nankotsu029.landformcraft.core.v2.placement.undo;

import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementVerifyEvidenceV2;

import java.util.Objects;

/**
 * Immutable Undo prepare request for a terminal Release 2 {@code APPLIED} placement.
 */
public record PlacementUndoPrepareRequestV2(
        PlacementPlanV2 placementPlan,
        PlacementEnvelopePlanV2 envelopePlan,
        PlacementReservationPlanV2 applyReservationPlan,
        PlacementSnapshotPlanV2 snapshotPlan,
        PlacementVerifyEvidenceV2 verifyEvidence,
        PlacementJournalV2 appliedJournal,
        PlacementJournalV2 applyCompleteJournal,
        PlacementPlanV2.PlacementActorV2 actor,
        String plaintextToken
) {
    public PlacementUndoPrepareRequestV2 {
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(envelopePlan, "envelopePlan");
        Objects.requireNonNull(applyReservationPlan, "applyReservationPlan");
        Objects.requireNonNull(snapshotPlan, "snapshotPlan");
        Objects.requireNonNull(verifyEvidence, "verifyEvidence");
        Objects.requireNonNull(appliedJournal, "appliedJournal");
        Objects.requireNonNull(applyCompleteJournal, "applyCompleteJournal");
        // actor and plaintextToken may be null (defaults applied by the compiler).
    }
}
