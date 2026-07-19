package com.github.nankotsu029.landformcraft.core.v2.recovery;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;

import java.util.Objects;

/** Immutable confirmation-bound recovery rollback execute request. */
public record PlacementRecoveryRollbackRequestV2(
        PlacementRecoveryPlanV2 recoveryPlan,
        PlacementPlanV2 placementPlan,
        PlacementEnvelopePlanV2 envelopePlan,
        PlacementReservationPlanV2 reservationPlan,
        PlacementSnapshotPlanV2 snapshotPlan,
        PlacementJournalV2 journal,
        PlacementPlanV2.PlacementActorV2 actor,
        String plaintextToken,
        PlacementSettleVerifyPolicyV2 policy,
        CancellationToken cancellation
) {
    public PlacementRecoveryRollbackRequestV2 {
        Objects.requireNonNull(recoveryPlan, "recoveryPlan");
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(envelopePlan, "envelopePlan");
        Objects.requireNonNull(reservationPlan, "reservationPlan");
        Objects.requireNonNull(snapshotPlan, "snapshotPlan");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(plaintextToken, "plaintextToken");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
