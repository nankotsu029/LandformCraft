package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.operations.OperationalMetricsCollectorV2;
import com.github.nankotsu029.landformcraft.core.v2.operations.OperationalOperationsServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.operations.Release2RetentionServiceV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalDiagnosticsReportV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricsSnapshotV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.Release2RetentionCleanupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Paper-facing Release 2 operational metrics／diagnostics／retention entry point (V2-6-13).
 * Must not run on the Paper main thread for I/O; world mutation is out of scope.
 */
public final class PaperOperationalOperationsServiceV2 {
    private final OperationalOperationsServiceV2 operations;

    public PaperOperationalOperationsServiceV2(OperationalOperationsServiceV2 operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
        if (!operations.isRelease2Path()) {
            throw new IllegalArgumentException("Paper ops adapter requires the Release 2 operations path");
        }
    }

    public boolean isRelease2Path() {
        return operations.isRelease2Path();
    }

    public OperationalMetricsSnapshotV2 captureMetrics(
            OperationalMetricsCollectorV2.PlacementStageCountsV2 stages,
            long settleTicksObserved,
            long verifyScannedBlocks,
            int runningGenerationJobs,
            String actorCanonical
    ) throws IOException {
        return operations.captureMetrics(
                stages, settleTicksObserved, verifyScannedBlocks, runningGenerationJobs, actorCanonical);
    }

    public OperationalDiagnosticsReportV2 diagnose(
            UUID correlationId,
            boolean openaiKeyPresent,
            boolean anthropicKeyPresent,
            String actorCanonical
    ) throws IOException {
        return operations.diagnose(correlationId, openaiKeyPresent, anthropicKeyPresent, actorCanonical);
    }

    public Release2RetentionServiceV2.PreparedRetentionV2 planRetention(
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            PlacementPlanV2.PlacementActorV2 actor,
            CancellationToken cancellation
    ) throws IOException {
        return operations.planRetention(placementPlan, journal, actor, cancellation);
    }

    public Release2RetentionCleanupPlanV2 executeRetention(
            UUID planId,
            String confirmationToken,
            PlacementPlanV2.PlacementActorV2 actor,
            PlacementPlanV2 placementPlan,
            PlacementJournalV2 journal,
            CancellationToken cancellation
    ) throws IOException {
        return operations.executeRetention(
                planId, confirmationToken, actor, placementPlan, journal, cancellation);
    }
}
