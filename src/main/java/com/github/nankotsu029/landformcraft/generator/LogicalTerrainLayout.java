package com.github.nankotsu029.landformcraft.generator;

import java.util.Arrays;

/** Immutable low-resolution maps sampled with bilinear interpolation by the full-resolution generator. */
public final class LogicalTerrainLayout {
    private final int resolution;
    private final double[] continental;
    private final double[] relief;

    LogicalTerrainLayout(int resolution, double[] continental, double[] relief) {
        if (resolution < 16 || Integer.bitCount(resolution) != 1
                || continental.length != resolution * resolution
                || relief.length != resolution * resolution) {
            throw new IllegalArgumentException("invalid logical layout dimensions");
        }
        this.resolution = resolution;
        this.continental = continental.clone();
        this.relief = relief.clone();
    }

    public int resolution() {
        return resolution;
    }

    public double continentalAt(double normalizedX, double normalizedZ) {
        return sample(continental, normalizedX, normalizedZ);
    }

    public double reliefAt(double normalizedX, double normalizedZ) {
        return sample(relief, normalizedX, normalizedZ);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof LogicalTerrainLayout layout
                && resolution == layout.resolution
                && Arrays.equals(continental, layout.continental)
                && Arrays.equals(relief, layout.relief);
    }

    @Override
    public int hashCode() {
        int result = 31 * resolution + Arrays.hashCode(continental);
        return 31 * result + Arrays.hashCode(relief);
    }

    private double sample(double[] values, double normalizedX, double normalizedZ) {
        double scaledX = clamp(normalizedX) * (resolution - 1);
        double scaledZ = clamp(normalizedZ) * (resolution - 1);
        int x0 = (int) Math.floor(scaledX);
        int z0 = (int) Math.floor(scaledZ);
        int x1 = Math.min(resolution - 1, x0 + 1);
        int z1 = Math.min(resolution - 1, z0 + 1);
        double amountX = scaledX - x0;
        double amountZ = scaledZ - z0;
        double north = lerp(values[z0 * resolution + x0], values[z0 * resolution + x1], amountX);
        double south = lerp(values[z1 * resolution + x0], values[z1 * resolution + x1], amountX);
        return lerp(north, south, amountZ);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double lerp(double first, double second, double amount) {
        return first + (second - first) * amount;
    }
}
