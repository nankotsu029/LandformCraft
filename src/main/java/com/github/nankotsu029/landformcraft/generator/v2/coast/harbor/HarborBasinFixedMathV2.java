package com.github.nankotsu029.landformcraft.generator.v2.coast.harbor;

/** Versioned integer-only geometry helpers for HARBOR_BASIN fields. */
public final class HarborBasinFixedMathV2 {
    public static final String VERSION = "harbor-basin-fixed-v1";
    public static final long FIXED_SCALE = 1_000_000L;

    private HarborBasinFixedMathV2() {
    }

    public static long roundDivide(long numerator, long denominator) {
        if (denominator <= 0L) throw new ArithmeticException("non-positive denominator");
        long quotient = numerator / denominator;
        long remainder = numerator % denominator;
        if (Math.multiplyExact(Math.abs(remainder), 2L) >= denominator) {
            quotient = Math.addExact(quotient, numerator < 0L ? -1L : 1L);
        }
        return quotient;
    }

    public static long integerSquareRoot(long value) {
        if (value < 0L) throw new ArithmeticException("negative square root");
        long result = 0L;
        long bit = 1L << 62;
        while (bit > value) bit >>>= 2;
        long remaining = value;
        while (bit != 0L) {
            if (remaining >= result + bit) {
                remaining -= result + bit;
                result = (result >>> 1) + bit;
            } else {
                result >>>= 1;
            }
            bit >>>= 2;
        }
        return result;
    }
}
