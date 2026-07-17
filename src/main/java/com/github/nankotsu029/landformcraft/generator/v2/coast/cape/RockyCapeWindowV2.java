package com.github.nankotsu029.landformcraft.generator.v2.coast.cape;

import java.util.Objects;

/** Bounded immutable-by-contract window for the four V2-2-06 cape fields. */
public final class RockyCapeWindowV2 {
    private final Bounds bounds;
    private final int[][] rawFields;
    private final long estimatedRetainedBytes;

    RockyCapeWindowV2(Bounds bounds, int[][] rawFields, long estimatedRetainedBytes) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.rawFields = Objects.requireNonNull(rawFields, "rawFields");
        int cells = Math.multiplyExact(bounds.width(), bounds.length());
        if (rawFields.length != RockyCapeGeneratorV2.CapeField.values().length) {
            throw new IllegalArgumentException("rocky cape window field count mismatch");
        }
        for (int[] field : rawFields) {
            if (field.length != cells) throw new IllegalArgumentException("rocky cape window field length mismatch");
        }
        this.estimatedRetainedBytes = estimatedRetainedBytes;
    }

    public Bounds bounds() { return bounds; }
    public long estimatedRetainedBytes() { return estimatedRetainedBytes; }

    public int rawValueAt(RockyCapeGeneratorV2.CapeField field, int globalX, int globalZ) {
        Objects.requireNonNull(field, "field");
        int localX = globalX - bounds.originX();
        int localZ = globalZ - bounds.originZ();
        if (localX < 0 || localX >= bounds.width() || localZ < 0 || localZ >= bounds.length()) {
            throw new IndexOutOfBoundsException("coordinate outside rocky cape window");
        }
        return rawFields[field.ordinal()][localZ * bounds.width() + localX];
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
                throw new IllegalArgumentException("invalid rocky cape window bounds");
            }
        }
    }
}
