package com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake;

import java.util.Objects;

/** Bounded lake field window over global X/Z. */
public final class LakeWindowV2 {
    private final int originX;
    private final int originZ;
    private final int width;
    private final int length;
    private final int[][] rawFields;
    private final long estimatedRetainedBytes;

    LakeWindowV2(
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
        if (rawFields.length != LakeGeneratorV2.LakeField.values().length) {
            throw new IllegalArgumentException("lake window field count mismatch");
        }
        for (int[] field : rawFields) {
            if (field.length != cells) throw new IllegalArgumentException("lake window field length mismatch");
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

    public int rawValueAt(LakeGeneratorV2.LakeField field, int globalX, int globalZ) {
        Objects.requireNonNull(field, "field");
        int localX = globalX - originX;
        int localZ = globalZ - originZ;
        if (localX < 0 || localX >= width || localZ < 0 || localZ >= length) {
            throw new IndexOutOfBoundsException("coordinate outside lake window");
        }
        return rawFields[field.ordinal()][localZ * width + localX];
    }
}
