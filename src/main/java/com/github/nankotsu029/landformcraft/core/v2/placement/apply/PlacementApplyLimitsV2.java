package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

/** Hard, allocation-before-admission limits for Release 2 apply orchestration. */
public record PlacementApplyLimitsV2(
        String limitsVersion,
        int workerThreads,
        int maximumQueuedTransactions,
        int maximumMutationsPerSchedulerSlice,
        int maximumOverlayOrdinals,
        long maximumBlocksPerTransaction,
        int estimatedBytesPerMutation
) {
    public static final String VERSION = "release-2-placement-apply-limits-v1";

    public PlacementApplyLimitsV2 {
        if (!VERSION.equals(limitsVersion)) {
            throw new IllegalArgumentException("unknown placement apply limits version");
        }
        if (workerThreads < 1 || workerThreads > 16
                || maximumQueuedTransactions < 1 || maximumQueuedTransactions > 1_024
                || maximumMutationsPerSchedulerSlice < 1
                || maximumMutationsPerSchedulerSlice > 4_096
                || maximumOverlayOrdinals < 1 || maximumOverlayOrdinals > 64
                || maximumBlocksPerTransaction < 1 || maximumBlocksPerTransaction > 1_500_000_000L
                || estimatedBytesPerMutation < 64 || estimatedBytesPerMutation > 4_096) {
            throw new IllegalArgumentException("invalid placement apply limits");
        }
    }

    public static PlacementApplyLimitsV2 defaults() {
        return new PlacementApplyLimitsV2(VERSION, 2, 16, 32, 32, 1_000_000_000L, 640);
    }
}
