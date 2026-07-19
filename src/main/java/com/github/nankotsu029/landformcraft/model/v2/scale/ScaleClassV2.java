package com.github.nankotsu029.landformcraft.model.v2.scale;

/**
 * Versioned generation scale classes. The stable {@code id} is the persisted identity;
 * enum ordinal and declaration order never participate in serialization or seeds.
 */
public enum ScaleClassV2 {
    /** Interactive-size areas. Whole-grid working sets are still acceptable. */
    SMALL("small", 512),
    /** Current supported ceiling. Tiled execution with bounded windows is mandatory. */
    MEDIUM("medium", 1024),
    /** Offline streaming target up to 3072 blocks per side (recommended 3000). */
    LARGE("large", 3072);

    /** Absolute horizontal ceiling of any v2 scale class in blocks. */
    public static final int MAXIMUM_HORIZONTAL_BLOCKS = 3_072;
    /** Documented recommendation for the largest practical request. */
    public static final int RECOMMENDED_LARGE_HORIZONTAL_BLOCKS = 3_000;

    private final String id;
    private final int maximumHorizontalBlocks;

    ScaleClassV2(String id, int maximumHorizontalBlocks) {
        this.id = id;
        this.maximumHorizontalBlocks = maximumHorizontalBlocks;
    }

    public String id() {
        return id;
    }

    public int maximumHorizontalBlocks() {
        return maximumHorizontalBlocks;
    }

    /** LARGE areas must never materialize whole-grid full-resolution working sets. */
    public boolean requiresStreamingExecution() {
        return this == LARGE;
    }

    /** Smallest class covering the requested area, by explicit threshold comparison. */
    public static ScaleClassV2 forDimensions(int widthBlocks, int lengthBlocks) {
        if (widthBlocks < 1 || lengthBlocks < 1
                || widthBlocks > MAXIMUM_HORIZONTAL_BLOCKS || lengthBlocks > MAXIMUM_HORIZONTAL_BLOCKS) {
            throw new IllegalArgumentException(
                    "scale dimensions must be between 1 and " + MAXIMUM_HORIZONTAL_BLOCKS + " blocks");
        }
        int longestSide = Math.max(widthBlocks, lengthBlocks);
        if (longestSide <= SMALL.maximumHorizontalBlocks) {
            return SMALL;
        }
        if (longestSide <= MEDIUM.maximumHorizontalBlocks) {
            return MEDIUM;
        }
        return LARGE;
    }
}
