package com.github.nankotsu029.landformcraft.generator.v2.hydrology.river;

/** Integer-only helpers for V2-3-03 river centerline / distance math. */
final class RiverFixedMathV2 {
    static final int FIXED_SCALE = 1_000_000;

    private RiverFixedMathV2() {
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
        if (denominator <= 0L) throw new ArithmeticException("non-positive divisor");
        long half = denominator / 2L;
        if (numerator >= 0L) {
            return Math.floorDiv(Math.addExact(numerator, half), denominator);
        }
        return Math.floorDiv(Math.subtractExact(numerator, half), denominator);
    }

    static int clampInt(long value, int minimum, int maximum) {
        if (value < minimum) return minimum;
        if (value > maximum) return maximum;
        return Math.toIntExact(value);
    }

    static long clampLong(long value, long minimum, long maximum) {
        if (value < minimum) return minimum;
        if (value > maximum) return maximum;
        return value;
    }

    /** Computes {@code numerator * multiplier / denominator} with rounding, avoiding intermediate overflow. */
    static long mulDivExact(long numerator, long multiplier, long denominator) {
        if (denominator <= 0L) throw new ArithmeticException("non-positive divisor");
        java.math.BigInteger value = java.math.BigInteger.valueOf(numerator)
                .multiply(java.math.BigInteger.valueOf(multiplier));
        java.math.BigInteger divisor = java.math.BigInteger.valueOf(denominator);
        java.math.BigInteger half = divisor.shiftRight(1);
        if (value.signum() >= 0) {
            value = value.add(half);
        } else {
            value = value.subtract(half);
        }
        return value.divide(divisor).longValueExact();
    }

    /** Deterministic unit-turn sine approximation in millionths (triangle wave, overflow-free). */
    static int sinTurnMillionths(long phaseMillionths) {
        long phase = Math.floorMod(phaseMillionths, (long) FIXED_SCALE);
        long quarter = FIXED_SCALE / 4L;
        int segment = (int) Math.floorDiv(phase, quarter);
        long local = phase - Math.multiplyExact(segment, quarter);
        int rise = Math.toIntExact(roundDivide(Math.multiplyExact(local, FIXED_SCALE), quarter));
        return switch (segment) {
            case 0 -> rise;
            case 1 -> FIXED_SCALE - rise;
            case 2 -> -rise;
            default -> -FIXED_SCALE + rise;
        };
    }
}
