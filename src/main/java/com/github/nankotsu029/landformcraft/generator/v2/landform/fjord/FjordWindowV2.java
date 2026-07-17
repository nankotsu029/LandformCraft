package com.github.nankotsu029.landformcraft.generator.v2.landform.fjord;

import java.util.Objects;

/** Bounded, tile-friendly fjord field window. */
public record FjordWindowV2(int originX, int originZ, int width, int length, int[][] fields, long retainedBytes) {
    public FjordWindowV2 {
        if (width < 1 || length < 1 || retainedBytes < 0 || fields == null
                || fields.length != FjordGeneratorV2.FjordField.values().length) {
            throw new IllegalArgumentException("fjord window is invalid");
        }
        int cells = Math.multiplyExact(width, length);
        for (int[] field : fields) if (field == null || field.length != cells) throw new IllegalArgumentException("fjord field length is invalid");
    }
    public int rawValueAt(FjordGeneratorV2.FjordField field, int globalX, int globalZ) {
        Objects.requireNonNull(field, "field");
        int x = globalX - originX, z = globalZ - originZ;
        if (x < 0 || z < 0 || x >= width || z >= length) throw new IllegalArgumentException("fjord sample outside window");
        return fields[field.ordinal()][z * width + x];
    }
}
