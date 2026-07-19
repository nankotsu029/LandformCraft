package com.github.nankotsu029.landformcraft.core.v2.operations;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryCleanupPlanV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryServiceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;

/**
 * Narrow cleanup port used by actor-bound retention (V2-6-13). Production wires
 * {@link PlacementRecoveryServiceV2}.
 */
public interface RetentionCleanupPortV2 {
    PlacementRecoveryCleanupPlanV2 planCleanup(
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    );

    long executeCleanup(
            PlacementRecoveryCleanupPlanV2 cleanupPlan,
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    );

    static RetentionCleanupPortV2 from(PlacementRecoveryServiceV2 recovery) {
        return new RetentionCleanupPortV2() {
            @Override
            public PlacementRecoveryCleanupPlanV2 planCleanup(
                    PlacementPlanV2 placementPlan,
                    PlacementJournalV2 journal,
                    CancellationToken cancellation
            ) {
                return recovery.planCleanup(placementPlan, journal, cancellation);
            }

            @Override
            public long executeCleanup(
                    PlacementRecoveryCleanupPlanV2 cleanupPlan,
                    PlacementPlanV2 placementPlan,
                    PlacementJournalV2 journal,
                    CancellationToken cancellation
            ) {
                return recovery.executeCleanup(cleanupPlan, placementPlan, journal, cancellation);
            }
        };
    }
}
