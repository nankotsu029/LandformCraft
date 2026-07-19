package com.github.nankotsu029.landformcraft.generator.v2.landform.reef;

import java.util.Objects;

/** Bounded, tile-friendly coral reef field window. */
public record CoralReefWindowV2(int originX, int originZ, int width, int length, int[][] fields, long retainedBytes) {
    public CoralReefWindowV2 {
        if (width < 1 || length < 1 || retainedBytes < 0 || fields == null
                || fields.length != CoralReefGeneratorV2.CoralReefField.values().length) {
            throw new IllegalArgumentException("coral reef window is invalid");
        }
        int cells = Math.multiplyExact(width, length);
        for (int[] field : fields) {
            if (field == null || field.length != cells) {
                throw new IllegalArgumentException("coral reef field length is invalid");
            }
        }
    }

    public int rawValueAt(CoralReefGeneratorV2.CoralReefField field, int globalX, int globalZ) {
        Objects.requireNonNull(field, "field");
        int x = globalX - originX;
        int z = globalZ - originZ;
        if (x < 0 || z < 0 || x >= width || z >= length) {
            throw new IllegalArgumentException("coral reef sample outside window");
        }
        return fields[field.ordinal()][z * width + x];
    }
}
