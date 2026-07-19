package com.github.nankotsu029.landformcraft.generator.v2.foundation.oxbow;

import com.github.nankotsu029.landformcraft.model.v2.foundation.OxbowLakePlanV2;

import java.util.List;

/** Integer-only helpers for V2-10-11 oxbow basin geometry. */
public final class OxbowLakeFixedMathV2 {
    private OxbowLakeFixedMathV2() {
    }

    public static boolean pointInRing(List<OxbowLakePlanV2.RingPoint> ring, long x, long z) {
        boolean inside = false;
        for (int index = 0, previous = ring.size() - 1; index < ring.size(); previous = index++) {
            long x1 = ring.get(previous).xMillionths();
            long z1 = ring.get(previous).zMillionths();
            long x2 = ring.get(index).xMillionths();
            long z2 = ring.get(index).zMillionths();
            boolean crosses = (z1 > z) != (z2 > z);
            if (!crosses) {
                continue;
            }
            long numerator = Math.multiplyExact(Math.subtractExact(x2, x1), Math.subtractExact(z, z1));
            long denominator = Math.subtractExact(z2, z1);
            long xIntersect = Math.addExact(x1, roundDivide(numerator, denominator));
            if (x < xIntersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static long roundDivide(long numerator, long denominator) {
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
}
