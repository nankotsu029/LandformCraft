package com.github.nankotsu029.landformcraft.model.v2.scale;

import java.util.Objects;

/**
 * Execution policy for one scale class: tile geometry, coarse planning resolution, and
 * resource ceilings that admission checks before any generation stage allocates memory.
 * Budgets are planning ceilings; each subsystem still needs its own measured budget gate
 * before a scale class becomes supported for that subsystem.
 */
public record ScaleProfileV2(
        ScaleClassV2 scaleClass,
        int tileSizeBlocks,
        int haloBlocks,
        int coarseCellBlocks,
        int maximumTileCount,
        long maximumRetainedBytes,
        long maximumWorkingBytes,
        long maximumArtifactBytes
) {
    public static final int MINIMUM_TILE_SIZE_BLOCKS = 32;
    public static final int MAXIMUM_TILE_SIZE_BLOCKS = 512;
    public static final int TRUSTED_MAXIMUM_TILE_COUNT = 4_096;
    public static final long TRUSTED_MAXIMUM_RETAINED_BYTES = 256L * 1024L * 1024L;
    public static final long TRUSTED_MAXIMUM_WORKING_BYTES = 128L * 1024L * 1024L;
    public static final long TRUSTED_MAXIMUM_ARTIFACT_BYTES = 4L * 1024L * 1024L * 1024L;

    public ScaleProfileV2 {
        Objects.requireNonNull(scaleClass, "scaleClass");
        if (tileSizeBlocks < MINIMUM_TILE_SIZE_BLOCKS || tileSizeBlocks > MAXIMUM_TILE_SIZE_BLOCKS
                || tileSizeBlocks % MINIMUM_TILE_SIZE_BLOCKS != 0) {
            throw new IllegalArgumentException("tile size must be a multiple of "
                    + MINIMUM_TILE_SIZE_BLOCKS + " between " + MINIMUM_TILE_SIZE_BLOCKS
                    + " and " + MAXIMUM_TILE_SIZE_BLOCKS);
        }
        if (haloBlocks < 0 || haloBlocks > tileSizeBlocks / 2) {
            throw new IllegalArgumentException("halo must be between 0 and half of the tile size");
        }
        if (coarseCellBlocks < 1 || coarseCellBlocks > 64 || tileSizeBlocks % coarseCellBlocks != 0) {
            throw new IllegalArgumentException(
                    "coarse cell size must be between 1 and 64 and divide the tile size");
        }
        if (maximumTileCount < 1 || maximumRetainedBytes < 1
                || maximumWorkingBytes < 1 || maximumArtifactBytes < 1) {
            throw new IllegalArgumentException("scale profile budgets must be positive");
        }
        // Trusted implementation policy ceilings, not caller-controlled budget suggestions.
        maximumTileCount = Math.min(maximumTileCount, TRUSTED_MAXIMUM_TILE_COUNT);
        maximumRetainedBytes = Math.min(maximumRetainedBytes, TRUSTED_MAXIMUM_RETAINED_BYTES);
        maximumWorkingBytes = Math.min(maximumWorkingBytes, TRUSTED_MAXIMUM_WORKING_BYTES);
        maximumArtifactBytes = Math.min(maximumArtifactBytes, TRUSTED_MAXIMUM_ARTIFACT_BYTES);
        // A profile may budget fewer tiles than its class maximum needs; admission then
        // rejects oversized areas with TILE_BUDGET_EXCEEDED instead of this constructor.
    }

    /** Frozen default profile per scale class. Changing values is a versioned policy change. */
    public static ScaleProfileV2 defaults(ScaleClassV2 scaleClass) {
        Objects.requireNonNull(scaleClass, "scaleClass");
        return switch (scaleClass) {
            case SMALL -> new ScaleProfileV2(scaleClass, 128, 16, 4, 16,
                    64L * 1024L * 1024L, 32L * 1024L * 1024L, 256L * 1024L * 1024L);
            case MEDIUM -> new ScaleProfileV2(scaleClass, 128, 16, 8, 64,
                    96L * 1024L * 1024L, 64L * 1024L * 1024L, 1024L * 1024L * 1024L);
            case LARGE -> new ScaleProfileV2(scaleClass, 128, 32, 16, 576,
                    TRUSTED_MAXIMUM_RETAINED_BYTES, TRUSTED_MAXIMUM_WORKING_BYTES,
                    TRUSTED_MAXIMUM_ARTIFACT_BYTES);
        };
    }
}
