package com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall;

import java.util.Objects;

/** Bounded waterfall field window over global X/Z. */
public final class WaterfallWindowV2 {
    private final int originX;
    private final int originZ;
    private final int width;
    private final int length;
    private final int[][] rawFields;
    private final long estimatedRetainedBytes;

    WaterfallWindowV2(
            int originX,
            int originZ,
            int width,
            int length,
            int[][] rawFields,
            long estimatedRetainedBytes
    ) {
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.length = length;
        this.rawFields = Objects.requireNonNull(rawFields, "rawFields");
        int cells = Math.multiplyExact(width, length);
        if (rawFields.length != WaterfallGeneratorV2.WaterfallField.values().length) {
            throw new IllegalArgumentException("waterfall window field count mismatch");
        }
        for (int[] field : rawFields) {
            if (field.length != cells) throw new IllegalArgumentException("waterfall window field length mismatch");
        }
        this.estimatedRetainedBytes = estimatedRetainedBytes;
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public long estimatedRetainedBytes() {
        return estimatedRetainedBytes;
    }

    public int rawValueAt(WaterfallGeneratorV2.WaterfallField field, int globalX, int globalZ) {
        Objects.requireNonNull(field, "field");
        int localX = globalX - originX;
        int localZ = globalZ - originZ;
        if (localX < 0 || localX >= width || localZ < 0 || localZ >= length) {
            throw new IndexOutOfBoundsException("coordinate outside waterfall window");
        }
        return rawFields[field.ordinal()][localZ * width + localX];
    }
}
