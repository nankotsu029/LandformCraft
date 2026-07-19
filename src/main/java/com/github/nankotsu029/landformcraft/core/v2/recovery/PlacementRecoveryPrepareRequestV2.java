package com.github.nankotsu029.landformcraft.core.v2.recovery;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementRecoveryActionV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;

import java.util.Objects;

/**
 * Immutable prepare request that turns a fresh diagnosis into a sealed, confirmation-bound
 * recovery plan. {@code actor} defaults to the placement plan actor and {@code plaintextToken}
 * to a random one-time token when {@code null}.
 */
public record PlacementRecoveryPrepareRequestV2(
        PlacementRecoveryDiagnosisV2 diagnosis,
        PlacementRecoveryActionV2 action,
        PlacementPlanV2 placementPlan,
        PlacementEnvelopePlanV2 envelopePlan,
        PlacementReservationPlanV2 reservationPlan,
        PlacementSnapshotPlanV2 snapshotPlan,
        PlacementJournalV2 journal,
        PlacementPlanV2.PlacementActorV2 actor,
        String plaintextToken,
        CancellationToken cancellation
) {
    public PlacementRecoveryPrepareRequestV2 {
        Objects.requireNonNull(diagnosis, "diagnosis");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(envelopePlan, "envelopePlan");
        Objects.requireNonNull(reservationPlan, "reservationPlan");
        Objects.requireNonNull(snapshotPlan, "snapshotPlan");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
