package com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal;

/** Integer-only helpers for V2-3-08 tidal geometry/raster math. */
final class TidalFixedMathV2 {
    static final int FIXED_SCALE = 1_000_000;

    private TidalFixedMathV2() {
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
        return numerator >= 0L
                ? Math.floorDiv(Math.addExact(numerator, half), denominator)
                : -Math.floorDiv(Math.addExact(Math.negateExact(numerator), half), denominator);
    }
}
