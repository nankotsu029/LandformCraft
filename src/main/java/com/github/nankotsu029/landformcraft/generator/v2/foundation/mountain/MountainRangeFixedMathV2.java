package com.github.nankotsu029.landformcraft.generator.v2.foundation.mountain;

/** Integer-only distance helpers for V2-9-03 mountain-range rasterization. */
public final class MountainRangeFixedMathV2 {
    private MountainRangeFixedMathV2() {
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
