package com.github.nankotsu029.landformcraft.generator.v2.coast.beach;

/** Versioned integer lookup math; no platform floating point participates in beach output. */
public final class SandyBeachFixedMathV2 {
    public static final String VERSION = "beach-tangent-linear-v1";
    public static final long FIXED_SCALE = 1_000_000L;

    private static final int[] TANGENT_MILLIONTHS = {
            0, 17_455, 34_921, 52_408, 69_927, 87_489, 105_104, 122_785,
            140_541, 158_384, 176_327, 194_380, 212_556, 230_868, 249_328,
            267_949, 286_745, 305_731, 324_920, 344_328, 363_970, 383_864,
            404_026, 424_475, 445_229, 466_308, 487_733, 509_525, 531_709,
            554_309, 577_350
    };

    private SandyBeachFixedMathV2() {
    }

    public static int tangentMillionths(long degreesMillionths) {
        if (degreesMillionths <= 0L || degreesMillionths > 30L * FIXED_SCALE) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-slope", "shore slope must be within (0..30] degrees");
        }
        int lowerDegree = Math.toIntExact(degreesMillionths / FIXED_SCALE);
        long fraction = degreesMillionths % FIXED_SCALE;
        if (fraction == 0L) return TANGENT_MILLIONTHS[lowerDegree];
        int lower = TANGENT_MILLIONTHS[lowerDegree];
        int upper = TANGENT_MILLIONTHS[lowerDegree + 1];
        return Math.toIntExact(Math.addExact(lower, roundDivide(
                Math.multiplyExact((long) upper - lower, fraction), FIXED_SCALE)));
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
