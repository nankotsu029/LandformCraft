package com.github.nankotsu029.landformcraft.core.v2.placement.rollback;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;

import java.util.Objects;

/**
 * Immutable rollback request for a failed Release 2 placement. {@code failedJournal} must be the
 * persisted {@code RECOVERY_REQUIRED} journal produced by the apply (V2-6-06) or settle／verify
 * (V2-6-07) failure classification.
 */
public record PlacementRollbackRequestV2(
        PlacementPlanV2 placementPlan,
        PlacementEnvelopePlanV2 envelopePlan,
        PlacementReservationPlanV2 reservationPlan,
        PlacementSnapshotPlanV2 snapshotPlan,
        PlacementJournalV2 failedJournal,
        PlacementSettleVerifyPolicyV2 policy,
        CancellationToken cancellation
) {
    public PlacementRollbackRequestV2 {
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(envelopePlan, "envelopePlan");
        Objects.requireNonNull(reservationPlan, "reservationPlan");
        Objects.requireNonNull(snapshotPlan, "snapshotPlan");
        Objects.requireNonNull(failedJournal, "failedJournal");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
