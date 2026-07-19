package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;

import java.util.Objects;

/** All checksum-bound prerequisites for one Release 2 APPLY operation. */
public record PlacementApplyRequestV2(
        PlacementPlanV2 placementPlan,
        PlacementEnvelopePlanV2 envelopePlan,
        PlacementReservationPlanV2 reservationPlan,
        PlacementSnapshotPlanV2 snapshotPlan,
        PlacementContainmentEvidenceV2 containmentEvidence,
        PlacementJournalV2 journal,
        PlacementCanonicalBlockSourceV2 blockSource,
        CancellationToken cancellation
) {
    public PlacementApplyRequestV2 {
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(envelopePlan, "envelopePlan");
        Objects.requireNonNull(reservationPlan, "reservationPlan");
        Objects.requireNonNull(snapshotPlan, "snapshotPlan");
        Objects.requireNonNull(containmentEvidence, "containmentEvidence");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(blockSource, "blockSource");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
