package com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater;

/** Versioned integer-only math for BREAKWATER_HARBOR geometry and fields. */
public final class BreakwaterFixedMathV2 {
    public static final String VERSION = "breakwater-fixed-v1";

    private BreakwaterFixedMathV2() {
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

    public static long ceilDivide(long numerator, long denominator) {
        if (numerator < 0L || denominator <= 0L) throw new ArithmeticException("invalid ceil division");
        return Math.addExact(numerator, denominator - 1L) / denominator;
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
