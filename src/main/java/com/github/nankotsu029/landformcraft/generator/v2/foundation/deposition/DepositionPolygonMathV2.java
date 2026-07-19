package com.github.nankotsu029.landformcraft.generator.v2.foundation.deposition;

import com.github.nankotsu029.landformcraft.model.v2.foundation.MoraineFieldPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OutwashPlainPlanV2;

import java.util.List;

/** Integer-only polygon tests for V2-10-02 moraine/outwash deposition rasters. */
public final class DepositionPolygonMathV2 {
    private DepositionPolygonMathV2() {
    }

    public static boolean containsMoraineRings(List<MoraineFieldPlanV2.Ring> rings, long x, long z) {
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

    public static boolean containsOutwashRings(List<OutwashPlainPlanV2.Ring> rings, long x, long z) {
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

    static boolean insideOrBoundary(List<?> ring, long x, long z) {
        boolean inside = false;
        for (int index = 0; index < ring.size() - 1; index++) {
            long ax = xMillionths(ring.get(index));
            long az = zMillionths(ring.get(index));
            long bx = xMillionths(ring.get(index + 1));
            long bz = zMillionths(ring.get(index + 1));
            long cross = cross(ax, az, bx, bz, x, z);
            if (cross == 0L && between(x, ax, bx) && between(z, az, bz)) {
                return true;
            }
            if ((az > z) != (bz > z)) {
                long dy = Math.subtractExact(bz, az);
                boolean crossesRight = (dy > 0L && cross > 0L) || (dy < 0L && cross < 0L);
                if (crossesRight) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static long xMillionths(Object vertex) {
        return switch (vertex) {
            case MoraineFieldPlanV2.Vertex value -> value.xMillionths();
            case OutwashPlainPlanV2.Vertex value -> value.xMillionths();
            default -> throw new IllegalArgumentException("unsupported vertex type");
        };
    }

    private static long zMillionths(Object vertex) {
        return switch (vertex) {
            case MoraineFieldPlanV2.Vertex value -> value.zMillionths();
            case OutwashPlainPlanV2.Vertex value -> value.zMillionths();
            default -> throw new IllegalArgumentException("unsupported vertex type");
        };
    }

    static long cross(long ax, long az, long bx, long bz, long x, long z) {
        return Math.subtractExact(
                Math.multiplyExact(Math.subtractExact(bx, ax), Math.subtractExact(z, az)),
                Math.multiplyExact(Math.subtractExact(bz, az), Math.subtractExact(x, ax)));
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
