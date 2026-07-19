package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

/** Hard admission limits for settle／full-verify orchestration workers. */
public record PlacementSettleVerifyLimitsV2(
        String limitsVersion,
        int workerThreads,
        int maximumQueuedTransactions
) {
    public static final String VERSION = "release-2-placement-settle-verify-limits-v1";

    public PlacementSettleVerifyLimitsV2 {
        if (!VERSION.equals(limitsVersion)) {
            throw new IllegalArgumentException("unknown placement settle/verify limits version");
        }
        if (workerThreads < 1 || workerThreads > 16
                || maximumQueuedTransactions < 1 || maximumQueuedTransactions > 1_024) {
            throw new IllegalArgumentException("invalid placement settle/verify limits");
        }
    }

    public static PlacementSettleVerifyLimitsV2 defaults() {
        return new PlacementSettleVerifyLimitsV2(VERSION, 2, 16);
    }
}
