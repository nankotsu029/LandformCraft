package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeTileCachePlanV2;

import java.util.Objects;

/**
 * Pre-fill admission using {@code retained + concurrency × peak + cache}.
 */
public final class VolumeTileCacheAdmissionV2 {
    public static final String ADMISSION_VERSION = "volume-tile-cache-admission-v1";

    private VolumeTileCacheAdmissionV2() {
    }

    public static VolumeTileCacheAdmissionDecisionV2 admit(VolumeTileCachePlanV2 plan) {
        Objects.requireNonNull(plan, "plan");
        VolumeTileCachePlanV2.Kernel kernel = plan.kernel();
        VolumeDenseAllocationDetectorV2.rejectIfOversizedChunk(kernel.chunkEdgeBlocks());
        VolumeDenseAllocationDetectorV2.rejectIfOversizedSupport(kernel.haloBlocksXyz());

        long retained = Math.multiplyExact(
                (long) kernel.maximumRetainedChunks(), kernel.estimatedRetainedBytesPerChunk());
        long peak = kernel.estimatedPeakWorkingBytesPerChunk();
        long concurrency = kernel.maximumConcurrentChunks();
        long cache = Math.multiplyExact(
                (long) kernel.maximumRetainedChunks(), kernel.estimatedRetainedBytesPerChunk());
        long required;
        try {
            required = Math.addExact(
                    retained,
                    Math.addExact(Math.multiplyExact(concurrency, peak), cache));
        } catch (ArithmeticException exception) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.ARITHMETIC_OVERFLOW,
                    "admission arithmetic overflow",
                    exception);
        }

        VolumeTileCachePlanV2.ResourceBudget budget = plan.budget();
        if (retained > budget.maximumRetainedBytes()
                || peak > budget.maximumPeakWorkingBytesPerChunk()
                || concurrency > budget.maximumConcurrency()
                || cache > budget.maximumCacheBytes()
                || required > budget.maximumWorkingBytes()) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.BUDGET_EXCEEDED,
                    "admission rejected: retained+concurrency×peak+cache exceeds budget");
        }

        // Chunk working sets must stay far below the forbidden dense world size.
        VolumeDenseAllocationDetectorV2.rejectIfDenseWorldArray(
                kernel.chunkEdgeBlocks(),
                kernel.chunkEdgeBlocks(),
                kernel.chunkEdgeBlocks());

        return new VolumeTileCacheAdmissionDecisionV2(
                ADMISSION_VERSION, retained, peak, concurrency, cache, required);
    }
}
