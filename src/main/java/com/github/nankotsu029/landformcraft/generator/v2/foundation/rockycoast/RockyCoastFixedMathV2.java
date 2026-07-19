package com.github.nankotsu029.landformcraft.generator.v2.foundation.rockycoast;

/** Integer-only distance helpers for V2-9-06 rocky-coast rasterization. */
public final class RockyCoastFixedMathV2 {
    private RockyCoastFixedMathV2() {
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

    public static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public static long cellHash(String seedNamespace, int x, int z) {
        long mixed = mix64(mix64(seedNamespace.hashCode() ^ x) ^ z);
        return Long.remainderUnsigned(mixed, 1_000_000L);
    }
}
