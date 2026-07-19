package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-04 VolumePlan 3D tile-cache contract. Describes chunk edge, XYZ halo, LRU／
 * concurrency budgets, and admission ceilings. Runtime evaluation lives in
 * {@code generator.v2.volume.cache}. TerrainQuery and feature generators are out of scope.
 */
public record VolumeTileCachePlanV2(
        int planVersion,
        String cacheContractVersion,
        CsgPlanBinding csgPlanBinding,
        Kernel kernel,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CACHE_CONTRACT_VERSION = "volume-tile-cache-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 64L * 1024L;

    public static final int MINIMUM_CHUNK_EDGE_BLOCKS = 16;
    public static final int MAXIMUM_CHUNK_EDGE_BLOCKS = 32;
    public static final int MAXIMUM_HALO_BLOCKS = 16;
    public static final int MAXIMUM_RETAINED_CHUNKS = 64;
    public static final int MAXIMUM_CONCURRENT_CHUNKS = 8;
    public static final int MAXIMUM_INTERVALS_PER_COLUMN = 64;

    /** Forbidden dense working-set size used by the allocation detector (1000×1000×512). */
    public static final long FORBIDDEN_DENSE_CELL_COUNT = 1000L * 1000L * 512L;

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public VolumeTileCachePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("volume-tile-cache planVersion must be 1");
        }
        cacheContractVersion = nonBlank(cacheContractVersion, "cacheContractVersion", 64);
        if (!CACHE_CONTRACT_VERSION.equals(cacheContractVersion)) {
            throw new IllegalArgumentException("unknown volume-tile-cache contract version");
        }
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateBudget(budget, kernel);
    }

    public VolumeTileCachePlanV2 withCanonicalChecksum(String checksum) {
        return new VolumeTileCachePlanV2(
                planVersion, cacheContractVersion, csgPlanBinding, kernel, budget, checksum);
    }

    public void requireCsgPlan(VolumeCsgPlanV2 csgPlan) {
        Objects.requireNonNull(csgPlan, "csgPlan");
        if (!csgPlanBinding.sourceVolumeCsgPlanChecksum().equals(csgPlan.canonicalChecksum())) {
            throw new IllegalArgumentException("volume-tile-cache CSG plan binding mismatch");
        }
    }

    public record CsgPlanBinding(
            int bindingVersion,
            String sourceVolumeCsgPlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "volume-tile-cache-csg-binding-v1";

        public CsgPlanBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown volume-tile-cache CSG binding");
            }
            sourceVolumeCsgPlanChecksum = checksum(
                    sourceVolumeCsgPlanChecksum, "sourceVolumeCsgPlanChecksum");
        }
    }

    public record Kernel(
            String kernelVersion,
            int chunkEdgeBlocks,
            int haloBlocksXyz,
            int maximumRetainedChunks,
            int maximumConcurrentChunks,
            int maximumSolidIntervalsPerColumn,
            int maximumFluidIntervalsPerColumn
    ) {
        public static final String VERSION = "volume-tile-cache-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown volume-tile-cache kernel version");
            }
            if (chunkEdgeBlocks != MINIMUM_CHUNK_EDGE_BLOCKS
                    && chunkEdgeBlocks != MAXIMUM_CHUNK_EDGE_BLOCKS) {
                throw new IllegalArgumentException(
                        "volume-tile-cache chunkEdgeBlocks must be 16 or 32");
            }
            if (haloBlocksXyz < 0 || haloBlocksXyz > MAXIMUM_HALO_BLOCKS
                    || maximumRetainedChunks < 1 || maximumRetainedChunks > MAXIMUM_RETAINED_CHUNKS
                    || maximumConcurrentChunks < 1
                    || maximumConcurrentChunks > MAXIMUM_CONCURRENT_CHUNKS
                    || maximumConcurrentChunks > maximumRetainedChunks
                    || maximumSolidIntervalsPerColumn < 1
                    || maximumSolidIntervalsPerColumn > MAXIMUM_INTERVALS_PER_COLUMN
                    || maximumFluidIntervalsPerColumn < 1
                    || maximumFluidIntervalsPerColumn > MAXIMUM_INTERVALS_PER_COLUMN) {
                throw new IllegalArgumentException("volume-tile-cache kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(
                    VERSION,
                    MINIMUM_CHUNK_EDGE_BLOCKS,
                    2,
                    16,
                    2,
                    MAXIMUM_INTERVALS_PER_COLUMN,
                    MAXIMUM_INTERVALS_PER_COLUMN);
        }

        public long cellsPerChunk() {
            long edge = chunkEdgeBlocks;
            return Math.multiplyExact(Math.multiplyExact(edge, edge), edge);
        }

        /** Conservative peak working bytes for one chunk fill (occupancy + interval scratch). */
        public long estimatedPeakWorkingBytesPerChunk() {
            return Math.addExact(
                    Math.multiplyExact(cellsPerChunk(), 2L),
                    Math.multiplyExact((long) chunkEdgeBlocks * chunkEdgeBlocks, 64L));
        }

        /** Conservative retained bytes for one sealed chunk of column intervals. */
        public long estimatedRetainedBytesPerChunk() {
            return Math.multiplyExact((long) chunkEdgeBlocks * chunkEdgeBlocks, 48L);
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            long maximumRetainedBytes,
            long maximumPeakWorkingBytesPerChunk,
            int maximumConcurrency,
            long maximumCacheBytes,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes,
            long maximumWorkingBytes
    ) {
        public static final String VERSION = "volume-tile-cache-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown volume-tile-cache budget version");
            }
            if (maximumRetainedBytes < 1
                    || maximumPeakWorkingBytesPerChunk < 1
                    || maximumConcurrency < 1 || maximumConcurrency > MAXIMUM_CONCURRENT_CHUNKS
                    || maximumCacheBytes < 1
                    || estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1) {
                throw new IllegalArgumentException("volume-tile-cache budget out of range");
            }
        }

        public static ResourceBudget forKernel(Kernel kernel) {
            long peak = kernel.estimatedPeakWorkingBytesPerChunk();
            long cache = Math.multiplyExact(
                    (long) kernel.maximumRetainedChunks(), kernel.estimatedRetainedBytesPerChunk());
            long retained = cache;
            long working = Math.addExact(
                    retained,
                    Math.multiplyExact((long) kernel.maximumConcurrentChunks(), peak));
            working = Math.addExact(working, cache);
            return new ResourceBudget(
                    VERSION,
                    retained,
                    peak,
                    kernel.maximumConcurrentChunks(),
                    cache,
                    2048L,
                    MAX_CANONICAL_BYTES,
                    working);
        }
    }

    private static void validateBudget(ResourceBudget budget, Kernel kernel) {
        if (budget.maximumConcurrency() > kernel.maximumConcurrentChunks()) {
            throw new IllegalArgumentException(
                    "volume-tile-cache budget concurrency exceeds kernel");
        }
        if (budget.maximumPeakWorkingBytesPerChunk() < kernel.estimatedPeakWorkingBytesPerChunk()) {
            throw new IllegalArgumentException(
                    "volume-tile-cache peak working budget below kernel estimate");
        }
        long requiredCache = Math.multiplyExact(
                (long) kernel.maximumRetainedChunks(), kernel.estimatedRetainedBytesPerChunk());
        if (budget.maximumCacheBytes() < requiredCache) {
            throw new IllegalArgumentException(
                    "volume-tile-cache cache budget below retained-chunk estimate");
        }
        long admission = Math.addExact(
                budget.maximumRetainedBytes(),
                Math.addExact(
                        Math.multiplyExact(
                                (long) budget.maximumConcurrency(),
                                budget.maximumPeakWorkingBytesPerChunk()),
                        budget.maximumCacheBytes()));
        if (budget.maximumWorkingBytes() < admission) {
            throw new IllegalArgumentException(
                    "volume-tile-cache working budget below retained+concurrency×peak+cache");
        }
    }

    private static String nonBlank(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " invalid");
        }
        return value;
    }

    private static String checksum(String value, String name) {
        if (value == null || !CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be 64 lowercase hex");
        }
        return value;
    }
}
