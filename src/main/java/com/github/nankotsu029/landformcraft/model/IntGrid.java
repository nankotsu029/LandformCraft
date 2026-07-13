package com.github.nankotsu029.landformcraft.model;

import java.util.Arrays;

/** Compact immutable integer grid stored in row-major order. */
public final class IntGrid {
    private final int width;
    private final int length;
    private final int[] values;

    public IntGrid(int width, int length, int[] values) {
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("grid dimensions must be positive");
        }
        if ((long) width * length != values.length) {
            throw new IllegalArgumentException("grid value count does not match its dimensions");
        }
        this.width = width;
        this.length = length;
        this.values = values.clone();
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public int get(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("grid coordinate outside bounds: " + x + "," + z);
        }
        return values[z * width + x];
    }

    public int[] toArray() {
        return values.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof IntGrid grid
                && width == grid.width
                && length == grid.length
                && Arrays.equals(values, grid.values);
    }

    @Override
    public int hashCode() {
        int result = 31 * width + length;
        return 31 * result + Arrays.hashCode(values);
    }
}
