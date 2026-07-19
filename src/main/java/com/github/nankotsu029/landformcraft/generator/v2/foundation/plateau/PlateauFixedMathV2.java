package com.github.nankotsu029.landformcraft.generator.v2.foundation.plateau;

import com.github.nankotsu029.landformcraft.model.v2.foundation.PlateauPlanV2;

import java.util.List;

/** Integer-only helpers for V2-10-06 plateau polygon tests and deterministic sampling. */
public final class PlateauFixedMathV2 {
    private PlateauFixedMathV2() {
    }

    public static boolean contains(List<PlateauPlanV2.Ring> rings, long x, long z) {
        if (!insideOrBoundary(rings.getFirst().vertices(), x, z)) {
            return false;
        }
        for (int index = 1; index < rings.size(); index++) {
            if (insideOrBoundary(rings.get(index).vertices(), x, z)) {
                return false;
            }
        }
        return true;
    }

    static boolean insideOrBoundary(List<PlateauPlanV2.Vertex> ring, long x, long z) {
        boolean inside = false;
        for (int index = 0; index < ring.size() - 1; index++) {
            PlateauPlanV2.Vertex a = ring.get(index);
            PlateauPlanV2.Vertex b = ring.get(index + 1);
            long cross = cross(a, b, x, z);
            if (cross == 0L && between(x, a.xMillionths(), b.xMillionths())
                    && between(z, a.zMillionths(), b.zMillionths())) {
                return true;
            }
            if ((a.zMillionths() > z) != (b.zMillionths() > z)) {
                long dy = Math.subtractExact(b.zMillionths(), a.zMillionths());
                boolean crossesRight = (dy > 0L && cross > 0L) || (dy < 0L && cross < 0L);
                if (crossesRight) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    static long cross(PlateauPlanV2.Vertex a, PlateauPlanV2.Vertex b, long x, long z) {
        return Math.subtractExact(
                Math.multiplyExact(Math.subtractExact(b.xMillionths(), a.xMillionths()),
                        Math.subtractExact(z, a.zMillionths())),
                Math.multiplyExact(Math.subtractExact(b.zMillionths(), a.zMillionths()),
                        Math.subtractExact(x, a.xMillionths())));
    }

    static boolean between(long value, long first, long second) {
        return value >= Math.min(first, second) && value <= Math.max(first, second);
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
