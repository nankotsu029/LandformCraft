package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRollbackServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryAcceptRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryCleanupPlanV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryDiagnoseRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryDiagnosisV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryPrepareRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryRollbackRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryServiceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Paper-facing Release 2 recovery admin entry point (V2-6-10). Distinct from the frozen v1
 * recovery path in {@code PlacementApplicationService}. Admin handlers obtain this adapter only
 * for the explicit Release 2 path ({@link PlacementRecoveryApplicationServiceV2#isRelease2Path()}).
 * Diagnose／prepare／execute must not run on the Paper main thread; world reads go through the
 * scheduler-backed Release 2 world gateway.
 */
public final class PaperPlacementRecoveryServiceV2 {
    private final PlacementRecoveryApplicationServiceV2 recovery;

    public PaperPlacementRecoveryServiceV2(PlacementRecoveryApplicationServiceV2 recovery) {
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        if (!recovery.isRelease2Path()) {
            throw new IllegalArgumentException(
                    "Paper recovery adapter requires the Release 2 recovery path");
        }
    }

    public boolean isRelease2Path() {
        return recovery.isRelease2Path();
    }

    public PlacementRecoveryDiagnosisV2 diagnose(PlacementRecoveryDiagnoseRequestV2 request) {
        return recovery.diagnose(request);
    }

    public PlacementRecoveryServiceV2.PreparedRecoveryV2 prepare(
            PlacementRecoveryPrepareRequestV2 request
    ) {
        return recovery.prepare(request);
    }

    public CompletionStage<PlacementRollbackServiceV2.RollbackResultV2> executeRollback(
            PlacementRecoveryRollbackRequestV2 request
    ) {
        return recovery.executeRollback(request);
    }

    public PlacementRecoveryServiceV2.AcceptResultV2 executeAccept(
            PlacementRecoveryAcceptRequestV2 request
    ) {
        return recovery.executeAccept(request);
    }

    public PlacementRecoveryCleanupPlanV2 planCleanup(
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    ) {
        return recovery.planCleanup(placementPlan, journal, cancellation);
    }

    public long executeCleanup(
            PlacementRecoveryCleanupPlanV2 cleanupPlan,
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    ) {
        return recovery.executeCleanup(cleanupPlan, placementPlan, journal, cancellation);
    }
}
