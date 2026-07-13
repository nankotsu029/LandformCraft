package com.github.nankotsu029.landformcraft.model;

import java.util.Arrays;
import java.util.Objects;

/** Compact immutable material grid backed by enum ordinals. */
public final class SurfaceMaterialGrid {
    private static final SurfaceMaterial[] MATERIALS = SurfaceMaterial.values();

    private final int width;
    private final int length;
    private final byte[] ordinals;

    public SurfaceMaterialGrid(int width, int length, SurfaceMaterial[] materials) {
        if (width < 1 || length < 1 || (long) width * length != materials.length) {
            throw new IllegalArgumentException("material grid dimensions do not match its values");
        }
        this.width = width;
        this.length = length;
        this.ordinals = new byte[materials.length];
        for (int index = 0; index < materials.length; index++) {
            this.ordinals[index] = (byte) Objects.requireNonNull(materials[index], "materials").ordinal();
        }
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public SurfaceMaterial get(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("material coordinate outside bounds: " + x + "," + z);
        }
        return MATERIALS[Byte.toUnsignedInt(ordinals[z * width + x])];
    }

    public byte[] toOrdinalArray() {
        return ordinals.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SurfaceMaterialGrid grid
                && width == grid.width
                && length == grid.length
                && Arrays.equals(ordinals, grid.ordinals);
    }

    @Override
    public int hashCode() {
        int result = 31 * width + length;
        return 31 * result + Arrays.hashCode(ordinals);
    }
}
