package com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater;

import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.ArrayList;
import java.util.List;

/** Fixed-grid path flattening shared by the breakwater compiler and sampler. */
final class BreakwaterPathKernelV2 {
    static final int GEOMETRY_SCALE = 4_096;
    static final int CURVE_SUBDIVISIONS = 16;
    static final int MAXIMUM_CONTROL_POINTS = 128;

    private BreakwaterPathKernelV2() {
    }

    static List<ArmGeometry> flatten(CoastalFeaturePlanV2 plan) {
        if (plan.geometry().geometryType() != TerrainIntentV2.GeometryType.MULTI_SPLINE
                || plan.geometry().paths().size() != 2) {
            throw failure("v2.breakwater-geometry", "breakwater requires exactly two named spline arms");
        }
        List<ArmGeometry> result = new ArrayList<>(2);
        for (CoastalFeaturePlanV2.BlockPath path : plan.geometry().paths()) {
            if (path.points().size() > MAXIMUM_CONTROL_POINTS) {
                throw failure("v2.breakwater-budget", "breakwater arm exceeds control-point budget");
            }
            List<Point> points = path.points().stream().map(BreakwaterPathKernelV2::quantize).toList();
            List<Segment> segments = new ArrayList<>();
            if (path.interpolation() == TerrainIntentV2.Interpolation.POLYLINE) {
                for (int index = 1; index < points.size(); index++) {
                    addSegment(segments, points.get(index - 1), points.get(index));
                }
            } else {
                for (int index = 0; index < points.size() - 1; index++) {
                    Point p0 = points.get(Math.max(0, index - 1));
                    Point p1 = points.get(index);
                    Point p2 = points.get(index + 1);
                    Point p3 = points.get(Math.min(points.size() - 1, index + 2));
                    Point b1 = new Point(
                            Math.addExact(p1.x(), BreakwaterFixedMathV2.roundDivide(p2.x() - p0.x(), 6L)),
                            Math.addExact(p1.z(), BreakwaterFixedMathV2.roundDivide(p2.z() - p0.z(), 6L)));
                    Point b2 = new Point(
                            Math.subtractExact(p2.x(), BreakwaterFixedMathV2.roundDivide(p3.x() - p1.x(), 6L)),
                            Math.subtractExact(p2.z(), BreakwaterFixedMathV2.roundDivide(p3.z() - p1.z(), 6L)));
                    Point previous = p1;
                    for (int step = 1; step <= CURVE_SUBDIVISIONS; step++) {
                        Point next = bezier(p1, b1, b2, p2, step);
                        addSegment(segments, previous, next);
                        previous = next;
                    }
                }
            }
            long lengthQ = 0L;
            for (Segment segment : segments) {
                lengthQ = Math.addExact(lengthQ, BreakwaterFixedMathV2.integerSquareRoot(segment.lengthSquared()));
            }
            long lengthMillionths = BreakwaterFixedMathV2.roundDivide(
                    Math.multiplyExact(lengthQ, (long) TerrainIntentV2.FIXED_SCALE), GEOMETRY_SCALE);
            result.add(new ArmGeometry(path.pathId(), List.copyOf(segments), lengthMillionths));
        }
        return result.stream().sorted(java.util.Comparator.comparing(ArmGeometry::armId)).toList();
    }

    static boolean intersects(ArmGeometry first, ArmGeometry second) {
        for (Segment a : first.segments()) {
            for (Segment b : second.segments()) {
                if (intersects(a, b)) return true;
            }
        }
        return false;
    }

    static boolean selfIntersects(ArmGeometry arm) {
        for (int first = 0; first < arm.segments().size(); first++) {
            for (int second = first + 2; second < arm.segments().size(); second++) {
                if (second == first + 1) continue;
                if (intersects(arm.segments().get(first), arm.segments().get(second))) return true;
            }
        }
        return false;
    }

    static Point quantize(CoastalFeaturePlanV2.BlockPoint point) {
        return new Point(
                BreakwaterFixedMathV2.roundDivide(
                        Math.multiplyExact(point.xMillionths(), GEOMETRY_SCALE), TerrainIntentV2.FIXED_SCALE),
                BreakwaterFixedMathV2.roundDivide(
                        Math.multiplyExact(point.zMillionths(), GEOMETRY_SCALE), TerrainIntentV2.FIXED_SCALE));
    }

    private static boolean intersects(Segment a, Segment b) {
        long ab1 = orientation(a.a(), a.b(), b.a());
        long ab2 = orientation(a.a(), a.b(), b.b());
        long ba1 = orientation(b.a(), b.b(), a.a());
        long ba2 = orientation(b.a(), b.b(), a.b());
        if (ab1 == 0L && onSegment(a.a(), a.b(), b.a())) return true;
        if (ab2 == 0L && onSegment(a.a(), a.b(), b.b())) return true;
        if (ba1 == 0L && onSegment(b.a(), b.b(), a.a())) return true;
        if (ba2 == 0L && onSegment(b.a(), b.b(), a.b())) return true;
        return Long.signum(ab1) != Long.signum(ab2) && Long.signum(ba1) != Long.signum(ba2);
    }

    private static long orientation(Point a, Point b, Point c) {
        return Math.subtractExact(
                Math.multiplyExact(b.x() - a.x(), c.z() - a.z()),
                Math.multiplyExact(b.z() - a.z(), c.x() - a.x()));
    }

    private static boolean onSegment(Point a, Point b, Point p) {
        return p.x() >= Math.min(a.x(), b.x()) && p.x() <= Math.max(a.x(), b.x())
                && p.z() >= Math.min(a.z(), b.z()) && p.z() <= Math.max(a.z(), b.z());
    }

    private static Point bezier(Point b0, Point b1, Point b2, Point b3, int step) {
        Point a = lerp(b0, b1, step);
        Point b = lerp(b1, b2, step);
        Point c = lerp(b2, b3, step);
        Point d = lerp(a, b, step);
        Point e = lerp(b, c, step);
        return lerp(d, e, step);
    }

    private static Point lerp(Point a, Point b, int step) {
        return new Point(
                Math.addExact(a.x(), BreakwaterFixedMathV2.roundDivide(
                        Math.multiplyExact(b.x() - a.x(), (long) step), CURVE_SUBDIVISIONS)),
                Math.addExact(a.z(), BreakwaterFixedMathV2.roundDivide(
                        Math.multiplyExact(b.z() - a.z(), (long) step), CURVE_SUBDIVISIONS)));
    }

    private static void addSegment(List<Segment> result, Point a, Point b) {
        if (a.equals(b)) throw failure("v2.breakwater-degenerate", "breakwater segment collapses at fixed precision");
        result.add(new Segment(a, b));
    }

    private static BreakwaterGenerationException failure(String ruleId, String message) {
        return new BreakwaterGenerationException(ruleId, message);
    }

    record Point(long x, long z) { }

    record Segment(Point a, Point b) {
        long dx() { return b.x() - a.x(); }
        long dz() { return b.z() - a.z(); }
        long lengthSquared() {
            return Math.addExact(Math.multiplyExact(dx(), dx()), Math.multiplyExact(dz(), dz()));
        }
    }

    record ArmGeometry(String armId, List<Segment> segments, long lengthMillionths) { }
}
