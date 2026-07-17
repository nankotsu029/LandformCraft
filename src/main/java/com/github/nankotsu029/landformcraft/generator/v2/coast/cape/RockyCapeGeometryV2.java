package com.github.nankotsu029.landformcraft.generator.v2.coast.cape;

import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.ArrayList;
import java.util.List;

/** Fixed-point polygon and cardinal seaward-boundary operations for one cape ring. */
final class RockyCapeGeometryV2 {
    private RockyCapeGeometryV2() {
    }

    static boolean contains(List<CoastalFeaturePlanV2.BlockPoint> ring, long x, long z) {
        boolean inside = false;
        for (int index = 0, previous = ring.size() - 2; index < ring.size() - 1; previous = index++) {
            CoastalFeaturePlanV2.BlockPoint a = ring.get(index);
            CoastalFeaturePlanV2.BlockPoint b = ring.get(previous);
            if (pointOnSegment(a, b, x, z)) return true;
            boolean crosses = (a.zMillionths() > z) != (b.zMillionths() > z);
            if (crosses) {
                long left = Math.multiplyExact(
                        a.xMillionths() - b.xMillionths(), z - b.zMillionths());
                long right = Math.multiplyExact(
                        x - b.xMillionths(), a.zMillionths() - b.zMillionths());
                if ((a.zMillionths() > b.zMillionths()) == (left > right)) inside = !inside;
            }
        }
        return inside;
    }

    static long distanceToBoundary(
            List<CoastalFeaturePlanV2.BlockPoint> ring, long x, long z
    ) {
        long minimum = Long.MAX_VALUE;
        for (int index = 0; index < ring.size() - 1; index++) {
            minimum = Math.min(minimum, distanceToSegment(ring.get(index), ring.get(index + 1), x, z));
        }
        return minimum;
    }

    static long distanceToSegment(
            CoastalFeaturePlanV2.BlockPoint a,
            CoastalFeaturePlanV2.BlockPoint b,
            long x,
            long z
    ) {
        long dx = b.xMillionths() - a.xMillionths();
        long dz = b.zMillionths() - a.zMillionths();
        long relativeX = x - a.xMillionths();
        long relativeZ = z - a.zMillionths();
        long lengthSquared = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long projection = Math.addExact(
                Math.multiplyExact(relativeX, dx), Math.multiplyExact(relativeZ, dz));
        if (projection <= 0L) return vectorLength(relativeX, relativeZ);
        if (projection >= lengthSquared) return vectorLength(x - b.xMillionths(), z - b.zMillionths());
        long cross = Math.subtractExact(
                Math.multiplyExact(dx, relativeZ), Math.multiplyExact(dz, relativeX));
        return Math.abs(cross) / Math.max(1L, RockyCapeFixedMathV2.integerSquareRoot(lengthSquared));
    }

    static Long seawardBoundary(
            List<CoastalFeaturePlanV2.BlockPoint> ring,
            TerrainIntentV2.Edge side,
            long tangent
    ) {
        List<Long> intersections = new ArrayList<>();
        for (int index = 0; index < ring.size() - 1; index++) {
            CoastalFeaturePlanV2.BlockPoint a = ring.get(index);
            CoastalFeaturePlanV2.BlockPoint b = ring.get(index + 1);
            if (side == TerrainIntentV2.Edge.EAST || side == TerrainIntentV2.Edge.WEST) {
                addIntersection(intersections, tangent, a.zMillionths(), b.zMillionths(),
                        a.xMillionths(), b.xMillionths());
            } else {
                addIntersection(intersections, tangent, a.xMillionths(), b.xMillionths(),
                        a.zMillionths(), b.zMillionths());
            }
        }
        if (intersections.isEmpty()) return null;
        return switch (side) {
            case EAST, SOUTH -> intersections.stream().mapToLong(Long::longValue).max().orElseThrow();
            case WEST, NORTH -> intersections.stream().mapToLong(Long::longValue).min().orElseThrow();
        };
    }

    static int nonCollinearTurningCount(List<CoastalFeaturePlanV2.BlockPoint> ring) {
        int vertices = ring.size() - 1;
        int turns = 0;
        for (int index = 0; index < vertices; index++) {
            CoastalFeaturePlanV2.BlockPoint previous = ring.get((index - 1 + vertices) % vertices);
            CoastalFeaturePlanV2.BlockPoint current = ring.get(index);
            CoastalFeaturePlanV2.BlockPoint next = ring.get((index + 1) % vertices);
            long firstX = current.xMillionths() - previous.xMillionths();
            long firstZ = current.zMillionths() - previous.zMillionths();
            long secondX = next.xMillionths() - current.xMillionths();
            long secondZ = next.zMillionths() - current.zMillionths();
            if (Math.subtractExact(Math.multiplyExact(firstX, secondZ),
                    Math.multiplyExact(firstZ, secondX)) != 0L) turns++;
        }
        return turns;
    }

    private static void addIntersection(
            List<Long> result,
            long tangent,
            long aTangent,
            long bTangent,
            long aAxis,
            long bAxis
    ) {
        if (aTangent == bTangent) return;
        long minimum = Math.min(aTangent, bTangent);
        long maximum = Math.max(aTangent, bTangent);
        if (tangent < minimum || tangent >= maximum) return;
        long numerator = Math.multiplyExact(bAxis - aAxis, tangent - aTangent);
        result.add(Math.addExact(aAxis,
                RockyCapeFixedMathV2.roundDivide(numerator, bTangent - aTangent)));
    }

    private static boolean pointOnSegment(
            CoastalFeaturePlanV2.BlockPoint a,
            CoastalFeaturePlanV2.BlockPoint b,
            long x,
            long z
    ) {
        long cross = Math.subtractExact(
                Math.multiplyExact(b.xMillionths() - a.xMillionths(), z - a.zMillionths()),
                Math.multiplyExact(b.zMillionths() - a.zMillionths(), x - a.xMillionths()));
        return cross == 0L
                && x >= Math.min(a.xMillionths(), b.xMillionths())
                && x <= Math.max(a.xMillionths(), b.xMillionths())
                && z >= Math.min(a.zMillionths(), b.zMillionths())
                && z <= Math.max(a.zMillionths(), b.zMillionths());
    }

    private static long vectorLength(long x, long z) {
        return RockyCapeFixedMathV2.integerSquareRoot(
                Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(z, z)));
    }
}
