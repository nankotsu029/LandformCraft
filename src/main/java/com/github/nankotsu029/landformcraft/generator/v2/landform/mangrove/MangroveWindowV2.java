package com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove;

import java.util.Objects;

/** Bounded, tile-friendly mangrove field window. */
public record MangroveWindowV2(int originX, int originZ, int width, int length, int[][] fields, long retainedBytes) {
    public MangroveWindowV2 {
        if (width < 1 || length < 1 || retainedBytes < 0 || fields == null
                || fields.length != MangroveGeneratorV2.MangroveField.values().length) {
            throw new IllegalArgumentException("mangrove window is invalid");
        }
        int cells = Math.multiplyExact(width, length);
        for (int[] field : fields) {
            if (field == null || field.length != cells) {
                throw new IllegalArgumentException("mangrove field length is invalid");
            }
        }
    }

    public int rawValueAt(MangroveGeneratorV2.MangroveField field, int globalX, int globalZ) {
        Objects.requireNonNull(field, "field");
        int x = globalX - originX;
        int z = globalZ - originZ;
        if (x < 0 || z < 0 || x >= width || z >= length) {
            throw new IllegalArgumentException("mangrove sample outside window");
        }
        return fields[field.ordinal()][z * width + x];
    }
}
