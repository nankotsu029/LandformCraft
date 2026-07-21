package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import java.util.List;

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
    public static final int DEFAULT_WORKER_THREADS = 2;
    public static final int DEFAULT_MAXIMUM_QUEUED_TRANSACTIONS = 16;
    public static final int PRODUCTION_MUTATIONS_PER_SCHEDULER_SLICE = 1_024;
    public static final int DEFAULT_MAXIMUM_OVERLAY_ORDINALS = 32;
    public static final long DEFAULT_MAXIMUM_BLOCKS_PER_TRANSACTION = 1_000_000_000L;
    public static final int DEFAULT_ESTIMATED_BYTES_PER_MUTATION = 640;
    public static final List<Integer> CALIBRATION_SLICE_CANDIDATES =
            List.of(32, 128, 256, 512, 1_024);
    public static final int MAXIMUM_CALIBRATION_MUTATIONS_PER_SLICE =
            CALIBRATION_SLICE_CANDIDATES.getLast();

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
        return withSliceSize(PRODUCTION_MUTATIONS_PER_SCHEDULER_SLICE);
    }

    /**
     * Returns the closed V2-13-06 calibration profile for one explicitly approved candidate.
     * This is not a general operator tuning surface: values outside the measured candidate set
     * fail closed even though the versioned gateway contract can represent larger slices.
     */
    public static PlacementApplyLimitsV2 withSliceSize(int mutationsPerSchedulerSlice) {
        if (!CALIBRATION_SLICE_CANDIDATES.contains(mutationsPerSchedulerSlice)) {
            throw new IllegalArgumentException("apply slice size is not a V2-13-06 calibration candidate");
        }
        return new PlacementApplyLimitsV2(
                VERSION,
                DEFAULT_WORKER_THREADS,
                DEFAULT_MAXIMUM_QUEUED_TRANSACTIONS,
                mutationsPerSchedulerSlice,
                DEFAULT_MAXIMUM_OVERLAY_ORDINALS,
                DEFAULT_MAXIMUM_BLOCKS_PER_TRANSACTION,
                DEFAULT_ESTIMATED_BYTES_PER_MUTATION);
    }

    /** Per-transaction mutation-list working set admitted by the placement plan budget. */
    public long maximumSliceWorkingBytes() {
        return Math.multiplyExact(maximumMutationsPerSchedulerSlice, estimatedBytesPerMutation);
    }

    /**
     * Worst-case aggregate mutation-list working set for all accepted transactions. Each
     * transaction can retain at most one scheduler slice while awaiting its real receipt.
     */
    public long maximumConcurrentSliceWorkingBytes() {
        long acceptedTransactions = Math.addExact(workerThreads, maximumQueuedTransactions);
        return Math.multiplyExact(acceptedTransactions, maximumSliceWorkingBytes());
    }

    /** Stable plan ceiling shared by every calibration candidate so plan checksums remain comparable. */
    public static long maximumCalibrationSliceWorkingBytes() {
        return Math.multiplyExact(
                (long) MAXIMUM_CALIBRATION_MUTATIONS_PER_SLICE,
                DEFAULT_ESTIMATED_BYTES_PER_MUTATION);
    }
}
