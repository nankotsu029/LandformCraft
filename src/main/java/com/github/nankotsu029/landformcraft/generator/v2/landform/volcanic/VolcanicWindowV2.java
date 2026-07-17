package com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic;

import java.util.Objects;

/** Bounded, tile-friendly volcanic field window. */
public record VolcanicWindowV2(
        int originX, int originZ, int width, int length, int[][] fields, long retainedBytes
) {
    public VolcanicWindowV2 {
        if (width < 1 || length < 1 || retainedBytes < 0 || fields == null
                || fields.length != VolcanicGeneratorV2.VolcanicField.values().length) {
            throw new IllegalArgumentException("volcanic window is invalid");
        }
        int cells = Math.multiplyExact(width, length);
        for (int[] field : fields) {
            if (field == null || field.length != cells) {
                throw new IllegalArgumentException("volcanic field length is invalid");
            }
        }
    }

    public int rawValueAt(VolcanicGeneratorV2.VolcanicField field, int globalX, int globalZ) {
        Objects.requireNonNull(field, "field");
        int x = globalX - originX;
        int z = globalZ - originZ;
        if (x < 0 || z < 0 || x >= width || z >= length) {
            throw new IllegalArgumentException("volcanic sample outside window");
        }
        return fields[field.ordinal()][z * width + x];
    }
}
