package com.github.nankotsu029.landformcraft.generator.v2.landform.reef;

import com.github.nankotsu029.landformcraft.model.v2.CoralReefPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Integer-only helpers for V2-4-10 coral reef polygon tests and pass corridor distance. */
final class CoralReefFixedMathV2 {
    private CoralReefFixedMathV2() {
    }

    static boolean insideOuter(List<CoralReefPlanV2.Ring> rings, long x, long z) {
        return insideOrBoundary(rings.getFirst().vertices(), x, z);
    }

    static boolean contains(List<CoralReefPlanV2.Ring> rings, long x, long z) {
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

    static boolean inLagoon(List<CoralReefPlanV2.Ring> rings, long x, long z) {
        for (int index = 1; index < rings.size(); index++) {
            if (insideOrBoundary(rings.get(index).vertices(), x, z)) {
                return true;
            }
        }
        return false;
    }

    static boolean onReef(List<CoralReefPlanV2.Ring> rings, long x, long z) {
        return contains(rings, x, z) && !inLagoon(rings, x, z);
    }

    static long distanceToHoleBoundary(List<CoralReefPlanV2.Ring> rings, long x, long z) {
        long result = Long.MAX_VALUE;
        for (int index = 1; index < rings.size(); index++) {
            result = Math.min(result, distanceToRingBoundary(rings.get(index).vertices(), x, z));
        }
        return result;
    }

    static long nearestCenterlineDistance(List<CoralReefPlanV2.CenterlinePoint> centerline, long px, long pz) {
        long result = Long.MAX_VALUE;
        for (int i = 1; i < centerline.size(); i++) {
            CoralReefPlanV2.CenterlinePoint a = centerline.get(i - 1);
            CoralReefPlanV2.CenterlinePoint b = centerline.get(i);
            long dx = b.xMillionths() - a.xMillionths();
            long dz = b.zMillionths() - a.zMillionths();
            long d2 = dx * dx + dz * dz;
            long projection = 0;
            if (d2 != 0) {
                projection = Math.max(0, Math.min(TerrainIntentV2.FIXED_SCALE,
                        ((px - a.xMillionths()) * dx + (pz - a.zMillionths()) * dz)
                                * TerrainIntentV2.FIXED_SCALE / d2));
            }
            long qx = a.xMillionths() + dx * projection / TerrainIntentV2.FIXED_SCALE;
            long qz = a.zMillionths() + dz * projection / TerrainIntentV2.FIXED_SCALE;
            result = Math.min(result, hypot(px - qx, pz - qz));
        }
        return result;
    }

    static long hypot(long x, long z) {
        return isqrt(Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(z, z)));
    }

    private static long distanceToRingBoundary(List<CoralReefPlanV2.Vertex> ring, long x, long z) {
        long result = Long.MAX_VALUE;
        for (int index = 0; index < ring.size() - 1; index++) {
            CoralReefPlanV2.Vertex a = ring.get(index);
            CoralReefPlanV2.Vertex b = ring.get(index + 1);
            long dx = b.xMillionths() - a.xMillionths();
            long dz = b.zMillionths() - a.zMillionths();
            long d2 = dx * dx + dz * dz;
            long projection = 0;
            if (d2 != 0) {
                projection = Math.max(0, Math.min(TerrainIntentV2.FIXED_SCALE,
                        ((x - a.xMillionths()) * dx + (z - a.zMillionths()) * dz)
                                * TerrainIntentV2.FIXED_SCALE / d2));
            }
            long qx = a.xMillionths() + dx * projection / TerrainIntentV2.FIXED_SCALE;
            long qz = a.zMillionths() + dz * projection / TerrainIntentV2.FIXED_SCALE;
            result = Math.min(result, hypot(x - qx, z - qz));
        }
        return result;
    }

    static boolean insideOrBoundary(List<CoralReefPlanV2.Vertex> ring, long x, long z) {
        boolean inside = false;
        for (int index = 0; index < ring.size() - 1; index++) {
            CoralReefPlanV2.Vertex a = ring.get(index);
            CoralReefPlanV2.Vertex b = ring.get(index + 1);
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

    private static long cross(CoralReefPlanV2.Vertex a, CoralReefPlanV2.Vertex b, long x, long z) {
        return Math.subtractExact(
                Math.multiplyExact(Math.subtractExact(b.xMillionths(), a.xMillionths()),
                        Math.subtractExact(z, a.zMillionths())),
                Math.multiplyExact(Math.subtractExact(b.zMillionths(), a.zMillionths()),
                        Math.subtractExact(x, a.xMillionths())));
    }

    private static boolean between(long value, long first, long second) {
        return value >= Math.min(first, second) && value <= Math.max(first, second);
    }

    private static long isqrt(long value) {
        long low = 0;
        long high = Math.min(value, 3_037_000_499L);
        while (low <= high) {
            long mid = (low + high) >>> 1;
            if (mid != 0 && mid > value / mid) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return high;
    }
}
