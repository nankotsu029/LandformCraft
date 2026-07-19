package com.github.nankotsu029.landformcraft.generator.v2.foundation.escarpment;

/** Integer-only distance helpers for V2-10-06 escarpment rasterization. */
public final class EscarpmentFixedMathV2 {
    private EscarpmentFixedMathV2() {
    }

    public static long hypot(long x, long z) {
        return isqrt(Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(z, z)));
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
