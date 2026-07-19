package com.github.nankotsu029.landformcraft.core.v2.placement.undo;

/**
 * Hard admission limits for Undo orchestration workers and restore／drift scanning slices.
 */
public record PlacementUndoLimitsV2(
        String limitsVersion,
        int workerThreads,
        int maximumQueuedTransactions,
        int maximumBlocksPerRestoreSlice,
        long maximumRestoreSlices,
        int maximumBlocksPerDriftSlice
) {
    public static final String VERSION = "release-2-placement-undo-limits-v1";
    public static final int MAXIMUM_RESTORE_SLICE_BLOCKS = 4_096;
    public static final int MAXIMUM_DRIFT_SLICE_BLOCKS = 4_096;

    public PlacementUndoLimitsV2 {
        if (!VERSION.equals(limitsVersion)) {
            throw new IllegalArgumentException("unknown placement undo limits version");
        }
        if (workerThreads < 1 || workerThreads > 16
                || maximumQueuedTransactions < 1 || maximumQueuedTransactions > 1_024
                || maximumBlocksPerRestoreSlice < 1
                || maximumBlocksPerRestoreSlice > MAXIMUM_RESTORE_SLICE_BLOCKS
                || maximumRestoreSlices < 1 || maximumRestoreSlices > 100_000_000L
                || maximumBlocksPerDriftSlice < 1
                || maximumBlocksPerDriftSlice > MAXIMUM_DRIFT_SLICE_BLOCKS) {
            throw new IllegalArgumentException("invalid placement undo limits");
        }
    }

    public static PlacementUndoLimitsV2 defaults() {
        return new PlacementUndoLimitsV2(VERSION, 2, 16, 1_024, 1_000_000L, 1_024);
    }
}
