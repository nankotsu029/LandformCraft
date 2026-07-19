package com.github.nankotsu029.landformcraft.generator.v2.foundation.river;

/** Integer-only distance helpers for V2-9-04 general river rasterization. */
public final class RiverFixedMathV2 {
    public static final long FIXED_SCALE = 1_000_000L;

    private RiverFixedMathV2() {
    }

    public static long hypot(long x, long z) {
        return isqrt(Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(z, z)));
    }

    public static long roundDivide(long numerator, long denominator) {
        if (denominator <= 0L) {
            throw new IllegalArgumentException("denominator must be positive");
        }
        long half = denominator / 2L;
        if (numerator >= 0L) {
            return (numerator + half) / denominator;
        }
        return -((-numerator + half) / denominator);
    }

    private static long isqrt(long value) {
        long low = 0;
        long high = Math.min(value, 3_037_000_499L);
        while (low <= high) {
            long mid = (low + high) >>> 1;
            if (mid != 0 && mid > value / mid) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return high;
    }
}
