package com.github.nankotsu029.landformcraft.generator.v2.volume.sdf;

/**
 * Integer-only helpers for V2-5-01 SDF evaluation. Coordinates use Q12 geometry scale;
 * distances are converted back to release-local millionths.
 */
final class VolumeSdfFixedMathV2 {
    static final int FIXED_SCALE = 1_000_000;
    static final int GEOMETRY_SCALE = 4_096;

    private VolumeSdfFixedMathV2() {
    }

    static long toGeometry(long millionths) {
        return roundDivide(Math.multiplyExact(millionths, GEOMETRY_SCALE), FIXED_SCALE);
    }

    static long toMillionths(long geometry) {
        return roundDivide(Math.multiplyExact(geometry, FIXED_SCALE), GEOMETRY_SCALE);
    }

    static long hypot3(long x, long y, long z) {
        return isqrt(Math.addExact(
                Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(y, y)),
                Math.multiplyExact(z, z)));
    }

    static long hypot2(long x, long y) {
        return isqrt(Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(y, y)));
    }

    static long max3(long a, long b, long c) {
        return Math.max(a, Math.max(b, c));
    }

    static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Round half away from zero. */
    static long roundDivide(long numerator, long denominator) {
        if (denominator <= 0L) {
            throw new ArithmeticException("non-positive denominator");
        }
        if (numerator >= 0L) {
            return Math.addExact(numerator, denominator / 2L) / denominator;
        }
        return Math.subtractExact(numerator, denominator / 2L) / denominator;
    }

    static long isqrt(long value) {
        if (value < 0L) {
            throw new ArithmeticException("sqrt of negative");
        }
        long low = 0L;
        long high = Math.min(value, 3_037_000_499L);
        while (low <= high) {
            long mid = (low + high) >>> 1;
            if (mid != 0L && mid > value / mid) {
                high = mid - 1L;
            } else {
                low = mid + 1L;
            }
        }
        return high;
    }
}
