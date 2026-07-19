package com.github.nankotsu029.landformcraft.core.v2.recovery;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;

import java.util.Objects;

/**
 * Immutable diagnose request for one persisted Release 2 placement journal. {@code snapshotPlan}
 * is optional for pre-mutation journals; {@code blockSource} is optional and only enables the
 * accept-eligibility comparison against the expected applied stream.
 */
public record PlacementRecoveryDiagnoseRequestV2(
        PlacementPlanV2 placementPlan,
        PlacementEnvelopePlanV2 envelopePlan,
        PlacementReservationPlanV2 reservationPlan,
        PlacementSnapshotPlanV2 snapshotPlan,
        PlacementJournalV2 journal,
        PlacementCanonicalBlockSourceV2 blockSource,
        CancellationToken cancellation
) {
    public PlacementRecoveryDiagnoseRequestV2 {
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(envelopePlan, "envelopePlan");
        Objects.requireNonNull(reservationPlan, "reservationPlan");
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
