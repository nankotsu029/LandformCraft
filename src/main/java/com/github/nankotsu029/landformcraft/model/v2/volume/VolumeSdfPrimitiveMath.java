package com.github.nankotsu029.landformcraft.model.v2.volume;

/** Shared integer helpers for volume SDF model validation (unit normal length). */
final class VolumeSdfPrimitiveMath {
    private VolumeSdfPrimitiveMath() {
    }

    static long hypot3(long x, long y, long z) {
        return isqrt(Math.addExact(
                Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(y, y)),
                Math.multiplyExact(z, z)));
    }

    private static long isqrt(long value) {
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
