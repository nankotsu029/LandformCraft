package com.github.nankotsu029.landformcraft.generator.v2.composition.coast;

import com.github.nankotsu029.landformcraft.format.v2.field.FieldValueSource;

import java.util.Objects;

/** Bounded transition output window containing core plus clipped halo cells. */
public final class CoastalTransitionWindowV2 {
    private final Bounds bounds;
    private final int[][] rawFields;
    private final long estimatedRetainedBytes;

    CoastalTransitionWindowV2(Bounds bounds, int[][] rawFields, long estimatedRetainedBytes) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.rawFields = Objects.requireNonNull(rawFields, "rawFields");
        int cells = Math.multiplyExact(bounds.width(), bounds.length());
        if (rawFields.length != CoastalTransitionCompositorV2.CompositionField.values().length) {
            throw new IllegalArgumentException("transition field count mismatch");
        }
        for (int[] field : rawFields) {
            if (field.length != cells) throw new IllegalArgumentException("transition field length mismatch");
        }
        this.estimatedRetainedBytes = estimatedRetainedBytes;
    }

    public Bounds bounds() { return bounds; }
    public long estimatedRetainedBytes() { return estimatedRetainedBytes; }

    public int rawValueAt(CoastalTransitionCompositorV2.CompositionField field, int globalX, int globalZ) {
        return rawFields[Objects.requireNonNull(field, "field").ordinal()][index(globalX, globalZ)];
    }

    public FieldValueSource localValueSource(CoastalTransitionCompositorV2.CompositionField field) {
        Objects.requireNonNull(field, "field");
        return (localX, localZ) -> {
            if (localX < 0 || localX >= bounds.width() || localZ < 0 || localZ >= bounds.length()) {
                throw new IndexOutOfBoundsException("coordinate outside transition window");
            }
            return rawFields[field.ordinal()][localZ * bounds.width() + localX];
        };
    }

    private int index(int globalX, int globalZ) {
        int localX = globalX - bounds.originX();
        int localZ = globalZ - bounds.originZ();
        if (localX < 0 || localX >= bounds.width() || localZ < 0 || localZ >= bounds.length()) {
            throw new IndexOutOfBoundsException("coordinate outside transition window");
        }
        return localZ * bounds.width() + localX;
    }

    public record Bounds(
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int originX,
            int originZ,
            int width,
            int length,
            int haloXZ
    ) {
        public Bounds {
            if (coreOriginX < 0 || coreOriginZ < 0 || coreWidth < 1 || coreLength < 1
                    || originX < 0 || originZ < 0 || width < 1 || length < 1 || haloXZ < 0
                    || originX > coreOriginX || originZ > coreOriginZ
                    || (long) originX + width < (long) coreOriginX + coreWidth
                    || (long) originZ + length < (long) coreOriginZ + coreLength) {
                throw new IllegalArgumentException("invalid transition window bounds");
            }
        }
    }
}
