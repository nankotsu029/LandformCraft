package com.github.nankotsu029.landformcraft.generator.v2.coast.cape;

/** Versioned integer-only helpers used by the ROCKY_CAPE compiler and sampler. */
public final class RockyCapeFixedMathV2 {
    public static final String VERSION = "rocky-cape-fixed-v1";
    public static final long FIXED_SCALE = 1_000_000L;

    private RockyCapeFixedMathV2() {
    }

    public static long roundDivide(long numerator, long denominator) {
        if (denominator == 0L) throw new ArithmeticException("zero denominator");
        if (denominator < 0L) {
            numerator = Math.negateExact(numerator);
            denominator = Math.negateExact(denominator);
        }
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

    public static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
