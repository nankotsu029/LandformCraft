package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeTileCachePlanV2;

/**
 * Rejects whole-world dense voxel allocations such as 1000×1000×512. Chunk-sized working sets are
 * admitted when within the sealed kernel edge.
 */
public final class VolumeDenseAllocationDetectorV2 {
    private VolumeDenseAllocationDetectorV2() {
    }

    public static void rejectIfDenseWorldArray(long width, long length, long height) {
        if (width < 1 || length < 1 || height < 1) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.BUDGET_EXCEEDED, "allocation dimensions invalid");
        }
        long cells;
        try {
            cells = Math.multiplyExact(Math.multiplyExact(width, length), height);
        } catch (ArithmeticException exception) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.DENSE_ALLOCATION_REJECTED,
                    "allocation cell count overflows",
                    exception);
        }
        if (cells >= VolumeTileCachePlanV2.FORBIDDEN_DENSE_CELL_COUNT
                || (width >= 1000L && length >= 1000L && height >= 512L)) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.DENSE_ALLOCATION_REJECTED,
                    "dense world volume allocation rejected: " + width + "x" + length + "x" + height);
        }
    }

    public static void rejectIfOversizedChunk(int chunkEdgeBlocks) {
        if (chunkEdgeBlocks != VolumeTileCachePlanV2.MINIMUM_CHUNK_EDGE_BLOCKS
                && chunkEdgeBlocks != VolumeTileCachePlanV2.MAXIMUM_CHUNK_EDGE_BLOCKS) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.OVERSIZED_CHUNK,
                    "chunk edge must be 16 or 32, got " + chunkEdgeBlocks);
        }
    }

    public static void rejectIfOversizedSupport(int haloBlocksXyz) {
        if (haloBlocksXyz < 0 || haloBlocksXyz > VolumeTileCachePlanV2.MAXIMUM_HALO_BLOCKS) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.OVERSIZED_CHUNK,
                    "halo must be 0.." + VolumeTileCachePlanV2.MAXIMUM_HALO_BLOCKS);
        }
    }
}
