package com.github.nankotsu029.landformcraft.generator.v2.coast.beach;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalRasterWindowV2;

import java.util.Objects;

/** Bounded immutable-by-contract beach field window. */
public final class SandyBeachWindowV2 {
    private final CoastalRasterWindowV2.Bounds bounds;
    private final int[][] rawFields;
    private final long estimatedRetainedBytes;

    SandyBeachWindowV2(
            CoastalRasterWindowV2.Bounds bounds,
            int[][] rawFields,
            long estimatedRetainedBytes
    ) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.rawFields = Objects.requireNonNull(rawFields, "rawFields");
        int cells = Math.multiplyExact(bounds.width(), bounds.length());
        if (rawFields.length != SandyBeachGeneratorV2.BeachField.values().length) {
            throw new IllegalArgumentException("beach window field count mismatch");
        }
        for (int[] field : rawFields) {
            if (field.length != cells) throw new IllegalArgumentException("beach window field length mismatch");
        }
        this.estimatedRetainedBytes = estimatedRetainedBytes;
    }

    public CoastalRasterWindowV2.Bounds bounds() {
        return bounds;
    }

    public long estimatedRetainedBytes() {
        return estimatedRetainedBytes;
    }

    public int rawValueAt(SandyBeachGeneratorV2.BeachField field, int globalX, int globalZ) {
        Objects.requireNonNull(field, "field");
        int localX = globalX - bounds.originX();
        int localZ = globalZ - bounds.originZ();
        if (localX < 0 || localX >= bounds.width() || localZ < 0 || localZ >= bounds.length()) {
            throw new IndexOutOfBoundsException("coordinate outside beach window");
        }
        return rawFields[field.ordinal()][localZ * bounds.width() + localX];
    }
}
