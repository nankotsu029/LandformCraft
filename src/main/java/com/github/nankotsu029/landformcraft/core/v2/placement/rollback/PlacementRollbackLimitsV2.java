package com.github.nankotsu029.landformcraft.core.v2.placement.rollback;

/**
 * Hard admission limits for rollback orchestration workers and restore slicing. Restore slices are
 * bounded like apply slices; the total slice budget guarantees termination before the first world
 * mutation is submitted.
 */
public record PlacementRollbackLimitsV2(
        String limitsVersion,
        int workerThreads,
        int maximumQueuedTransactions,
        int maximumBlocksPerRestoreSlice,
        long maximumRestoreSlices
) {
    public static final String VERSION = "release-2-placement-rollback-limits-v1";
    public static final int MAXIMUM_RESTORE_SLICE_BLOCKS = 4_096;

    public PlacementRollbackLimitsV2 {
        if (!VERSION.equals(limitsVersion)) {
            throw new IllegalArgumentException("unknown placement rollback limits version");
        }
        if (workerThreads < 1 || workerThreads > 16
                || maximumQueuedTransactions < 1 || maximumQueuedTransactions > 1_024
                || maximumBlocksPerRestoreSlice < 1
                || maximumBlocksPerRestoreSlice > MAXIMUM_RESTORE_SLICE_BLOCKS
                || maximumRestoreSlices < 1 || maximumRestoreSlices > 100_000_000L) {
            throw new IllegalArgumentException("invalid placement rollback limits");
        }
    }

    public static PlacementRollbackLimitsV2 defaults() {
        return new PlacementRollbackLimitsV2(VERSION, 2, 16, 1_024, 1_000_000L);
    }
}
