package com.github.nankotsu029.landformcraft.generator;

final class DeterministicNoise {
    private DeterministicNoise() {
    }

    static double fractal(long seed, double x, double z, int octaves) {
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double normalization = 0.0;
        for (int octave = 0; octave < octaves; octave++) {
            sum += value(seed + octave * 0x632BE59BD9B4E019L, x * frequency, z * frequency) * amplitude;
            normalization += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / normalization;
    }

    static double value(long seed, double x, double z) {
        long x0 = (long) Math.floor(x);
        long z0 = (long) Math.floor(z);
        double localX = smooth(x - x0);
        double localZ = smooth(z - z0);
        double north = lerp(lattice(seed, x0, z0), lattice(seed, x0 + 1, z0), localX);
        double south = lerp(lattice(seed, x0, z0 + 1), lattice(seed, x0 + 1, z0 + 1), localX);
        return lerp(north, south, localZ);
    }

    private static double lattice(long seed, long x, long z) {
        long value = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53 * 2.0 - 1.0;
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double first, double second, double amount) {
        return first + (second - first) * amount;
    }
}
