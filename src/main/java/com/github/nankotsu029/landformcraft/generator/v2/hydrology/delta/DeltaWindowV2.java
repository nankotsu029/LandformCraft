package com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta;

import java.util.Objects;

/** Bounded delta field window over release-local global X/Z. */
public final class DeltaWindowV2 {
    private final int originX;
    private final int originZ;
    private final int width;
    private final int length;
    private final int[][] rawFields;
    private final long estimatedRetainedBytes;

    DeltaWindowV2(
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
        if (rawFields.length != DeltaGeneratorV2.DeltaField.values().length) {
            throw new IllegalArgumentException("delta window field count mismatch");
        }
        for (int[] field : rawFields) {
            if (field.length != cells) throw new IllegalArgumentException("delta window field length mismatch");
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

    public int rawValueAt(DeltaGeneratorV2.DeltaField field, int globalX, int globalZ) {
        Objects.requireNonNull(field, "field");
        int localX = globalX - originX;
        int localZ = globalZ - originZ;
        if (localX < 0 || localX >= width || localZ < 0 || localZ >= length) {
            throw new IndexOutOfBoundsException("coordinate outside delta window");
        }
        return rawFields[field.ordinal()][localZ * width + localX];
    }
}
