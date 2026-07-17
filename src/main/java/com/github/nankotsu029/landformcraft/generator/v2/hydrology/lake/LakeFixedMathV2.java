package com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake;

import java.math.BigInteger;

/** Integer-only helpers for V2-3-04 lake geometry. */
final class LakeFixedMathV2 {
    static final int FIXED_SCALE = 1_000_000;

    private LakeFixedMathV2() {
    }

    static long integerSquareRoot(long value) {
        if (value < 0L) throw new ArithmeticException("sqrt of negative");
        if (value < 2L) return value;
        long guess = value;
        long previous;
        do {
            previous = guess;
            guess = (guess + value / guess) >>> 1;
        } while (guess < previous);
        return previous;
    }

    static long hypotMillionths(long dx, long dz) {
        return integerSquareRoot(Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz)));
    }

    static long roundDivide(long numerator, long denominator) {
        if (denominator == 0L) throw new ArithmeticException("zero divisor");
        boolean negative = denominator < 0L;
        long absDenominator = negative ? Math.negateExact(denominator) : denominator;
        long absNumerator = negative ? Math.negateExact(numerator) : numerator;
        long half = absDenominator / 2L;
        if (absNumerator >= 0L) {
            return Math.floorDiv(Math.addExact(absNumerator, half), absDenominator);
        }
        return Math.floorDiv(Math.subtractExact(absNumerator, half), absDenominator);
    }

    /** Exact (a * b) / c with half-up rounding and overflow-safe intermediate products. */
    static long mulDivExact(long a, long b, long c) {
        if (c == 0L) throw new ArithmeticException("zero divisor");
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

    static long clampLong(long value, long minimum, long maximum) {
        if (value < minimum) return minimum;
        if (value > maximum) return maximum;
        return value;
    }

    static boolean pointInRing(java.util.List<com.github.nankotsu029.landformcraft.model.v2.LakePlanV2.RingPoint> ring,
                               long x, long z) {
        boolean inside = false;
        for (int index = 0, previous = ring.size() - 1; index < ring.size(); previous = index++) {
            long x1 = ring.get(previous).xMillionths();
            long z1 = ring.get(previous).zMillionths();
            long x2 = ring.get(index).xMillionths();
            long z2 = ring.get(index).zMillionths();
            boolean crosses = (z1 > z) != (z2 > z);
            if (!crosses) continue;
            long numerator = Math.multiplyExact(Math.subtractExact(x2, x1), Math.subtractExact(z, z1));
            long denominator = Math.subtractExact(z2, z1);
            long xIntersect = Math.addExact(x1, roundDivide(numerator, denominator));
            if (x < xIntersect) inside = !inside;
        }
        return inside;
    }
}
