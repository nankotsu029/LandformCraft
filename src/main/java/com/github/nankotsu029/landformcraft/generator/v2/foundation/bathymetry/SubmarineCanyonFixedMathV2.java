package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

import java.math.BigInteger;

/** Integer-only helpers for V2-9-09 submarine-canyon centerline and cross-section math. */
public final class SubmarineCanyonFixedMathV2 {
    public static final int FIXED_SCALE = 1_000_000;

    private SubmarineCanyonFixedMathV2() {
    }

    public static long integerSquareRoot(long value) {
        if (value < 0L) {
            throw new ArithmeticException("sqrt of negative");
        }
        if (value < 2L) {
            return value;
        }
        long guess = value;
        long previous;
        do {
            previous = guess;
            guess = (guess + value / guess) >>> 1;
        } while (guess < previous);
        return previous;
    }

    public static long hypotMillionths(long dx, long dz) {
        return integerSquareRoot(Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz)));
    }

    public static long roundDivide(long numerator, long denominator) {
        if (denominator == 0L) {
            throw new ArithmeticException("zero divisor");
        }
        boolean negative = denominator < 0L;
        long absDenominator = negative ? Math.negateExact(denominator) : denominator;
        long absNumerator = negative ? Math.negateExact(numerator) : numerator;
        long half = absDenominator / 2L;
        if (absNumerator >= 0L) {
            return Math.floorDiv(Math.addExact(absNumerator, half), absDenominator);
        }
        return Math.floorDiv(Math.subtractExact(absNumerator, half), absDenominator);
    }

    public static long mulDivExact(long a, long b, long c) {
        if (c == 0L) {
            throw new ArithmeticException("zero divisor");
        }
        BigInteger numerator = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
        BigInteger denominator = BigInteger.valueOf(c);
        BigInteger[] parts = numerator.divideAndRemainder(denominator);
        BigInteger quotient = parts[0];
        BigInteger remainder = parts[1].abs();
        if (remainder.multiply(BigInteger.TWO).compareTo(denominator.abs()) >= 0) {
            quotient = quotient.add(BigInteger.valueOf(numerator.signum() == denominator.signum() ? 1L : -1L));
        }
        return quotient.longValueExact();
    }

    public static long clampLong(long value, long minimum, long maximum) {
        if (value < minimum) {
            return minimum;
        }
        if (value > maximum) {
            return maximum;
        }
        return value;
    }
}
