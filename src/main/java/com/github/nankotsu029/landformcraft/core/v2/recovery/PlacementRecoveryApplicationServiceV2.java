package com.github.nankotsu029.landformcraft.core.v2.recovery;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRollbackServiceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Explicit Release 2 recovery path for Paper admin dispatch. Wraps diagnose + prepare + execute +
 * cleanup so callers can discriminate from the frozen v1 recovery path
 * ({@code PlacementApplicationService.diagnoseRecovery／recoverRollback／recoverAccept}) via
 * {@link #isRelease2Path()}.
 */
public final class PlacementRecoveryApplicationServiceV2 {
    private final PlacementRecoveryServiceV2 recoveryService;

    public PlacementRecoveryApplicationServiceV2(PlacementRecoveryServiceV2 recoveryService) {
        this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService");
    }

    public PlacementRecoveryDiagnosisV2 diagnose(PlacementRecoveryDiagnoseRequestV2 request) {
        return recoveryService.diagnose(request);
    }

    public PlacementRecoveryServiceV2.PreparedRecoveryV2 prepare(
            PlacementRecoveryPrepareRequestV2 request
    ) {
        return recoveryService.prepare(request);
    }

    public CompletionStage<PlacementRollbackServiceV2.RollbackResultV2> executeRollback(
            PlacementRecoveryRollbackRequestV2 request
    ) {
        return recoveryService.executeRollback(request);
    }

    public PlacementRecoveryServiceV2.AcceptResultV2 executeAccept(
            PlacementRecoveryAcceptRequestV2 request
    ) {
        return recoveryService.executeAccept(request);
    }

    public PlacementRecoveryCleanupPlanV2 planCleanup(
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    ) {
        return recoveryService.planCleanup(placementPlan, journal, cancellation);
    }

    public long executeCleanup(
            PlacementRecoveryCleanupPlanV2 cleanupPlan,
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    ) {
        return recoveryService.executeCleanup(cleanupPlan, placementPlan, journal, cancellation);
    }

    /** Discriminator for Paper admin routing onto the Release 2 recovery path. */
    public boolean isRelease2Path() {
        return true;
    }
}
