package com.github.nankotsu029.landformcraft.generator.v2.landform.mountain;

import com.github.nankotsu029.landformcraft.model.v2.MountainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for alpine/glacial mountain ridge skeletons. */
public final class MountainPlanCompilerV2 {
    public MountainPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        TerrainIntentV2.MountainVariant variant = variantOf(feature.kind());
        if (!(feature.parameters() instanceof TerrainIntentV2.MountainParameters parameters)) {
            throw failure("v2.mountain-parameters", "mountain feature requires MountainParameters");
        }
        try {
            List<long[]> controls = ridgeControls(feature, bounds);
            requireNoSelfCross(controls);
            requireInsideBounds(controls, bounds);
            requireNoHardCoastConflict(controls, intent, bounds);
            List<MountainPlanV2.RidgePoint> ridge = sampleRidge(controls);
            int peakCount = midpoint(parameters.peakCount());
            int halfWidth = midpoint(parameters.ridgeHalfWidthBlocks());
            int relief = midpoint(parameters.maxReliefBlocks());
            List<MountainPlanV2.NamedStation> peaks = placePeaks(feature.id(), ridge, peakCount, relief);
            List<MountainPlanV2.NamedStation> saddles = placeSaddles(feature.id(), peaks, relief);
            List<MountainPlanV2.SpurSegment> spurs = placeSpurs(feature.id(), ridge, peaks, parameters.spurCount(), halfWidth, bounds);
            int support = Math.max(halfWidth, 8);
            if (support > LandformMountainModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.mountain-budget", "mountain support radius exceeds trusted halo");
            }
            long work = Math.multiplyExact(Math.multiplyExact((long) bounds.width(), bounds.length()), 6L);
            if (work > MountainPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.mountain-budget", "mountain ridge/raster budget exceeded");
            }
            return new MountainPlanV2(
                    MountainPlanV2.VERSION,
                    feature.id(),
                    variant,
                    ridge,
                    peaks,
                    saddles,
                    spurs,
                    peakCount,
                    halfWidth,
                    relief,
                    parameters.spurCount(),
                    parameters.ridgeSharpnessMillionths(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    LandformMountainModuleV2.RIDGE_MASK_FIELD_ID,
                    LandformMountainModuleV2.PEAK_MASK_FIELD_ID,
                    LandformMountainModuleV2.SADDLE_MASK_FIELD_ID,
                    LandformMountainModuleV2.SPUR_MASK_FIELD_ID,
                    LandformMountainModuleV2.PROVISIONAL_SURFACE_FIELD_ID,
                    LandformMountainModuleV2.RIDGE_SEGMENT_ID_FIELD_ID,
                    support,
                    work,
                    geometryChecksum);
        } catch (ArithmeticException exception) {
            throw new MountainGenerationException("v2.mountain-budget", "mountain arithmetic overflow", exception);
        }
    }

    private static TerrainIntentV2.MountainVariant variantOf(TerrainIntentV2.FeatureKind kind) {
        return switch (kind) {
            case ALPINE_MOUNTAIN_RANGE -> TerrainIntentV2.MountainVariant.ALPINE;
            case GLACIAL_MOUNTAIN_RANGE -> TerrainIntentV2.MountainVariant.GLACIAL;
            default -> throw failure("v2.mountain-kind", "feature kind is not a mountain range");
        };
    }

    private static List<long[]> ridgeControls(TerrainIntentV2.Feature feature, WorldBlueprintV2.Bounds bounds) {
        if (feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline) {
            if (spline.points().size() < 2) {
                throw failure("v2.mountain-geometry", "mountain ridge spline needs at least two points");
            }
            List<long[]> controls = new ArrayList<>();
            for (TerrainIntentV2.Point2 point : spline.points()) {
                controls.add(new long[]{
                        Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L),
                        Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L)});
            }
            return controls;
        }
        if (feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon) {
            return majorAxisFromPolygon(polygon, bounds);
        }
        throw failure("v2.mountain-geometry", "mountain requires SPLINE or POLYGON geometry");
    }

    private static List<long[]> majorAxisFromPolygon(
            TerrainIntentV2.PolygonGeometry polygon,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<TerrainIntentV2.Point2> ring = polygon.rings().getFirst();
        if (ring.size() < 4) {
            throw failure("v2.mountain-geometry", "glacial mountain polygon ring is too small");
        }
        List<long[]> vertices = new ArrayList<>();
        for (int i = 0; i < ring.size() - 1; i++) {
            TerrainIntentV2.Point2 point = ring.get(i);
            vertices.add(new long[]{
                    Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L),
                    Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L)});
        }
        int bestI = 0;
        int bestJ = 1;
        long best = -1L;
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                long dx = vertices.get(j)[0] - vertices.get(i)[0];
                long dz = vertices.get(j)[1] - vertices.get(i)[1];
                long distance = hypot(dx, dz);
                if (distance > best) {
                    best = distance;
                    bestI = i;
                    bestJ = j;
                }
            }
        }
        if (best < Math.multiplyExact(16L, TerrainIntentV2.FIXED_SCALE)) {
            throw failure("v2.mountain-geometry", "glacial mountain polygon major axis is too short");
        }
        long[] a = vertices.get(bestI);
        long[] b = vertices.get(bestJ);
        long midX = (a[0] + b[0]) / 2L;
        long midZ = (a[1] + b[1]) / 2L;
        return List.of(a, new long[]{midX, midZ}, b);
    }

    private static void requireNoSelfCross(List<long[]> controls) {
        for (int i = 1; i < controls.size(); i++) {
            long[] a0 = controls.get(i - 1);
            long[] a1 = controls.get(i);
            for (int j = i + 2; j < controls.size(); j++) {
                long[] b0 = controls.get(j - 1);
                long[] b1 = controls.get(j);
                if (segmentsCross(a0, a1, b0, b1)) {
                    throw failure("v2.mountain-self-cross", "mountain ridge self-intersects");
                }
            }
        }
    }

    private static boolean segmentsCross(long[] a0, long[] a1, long[] b0, long[] b1) {
        long d1 = cross(a0, a1, b0);
        long d2 = cross(a0, a1, b1);
        long d3 = cross(b0, b1, a0);
        long d4 = cross(b0, b1, a1);
        return signsDiffer(d1, d2) && signsDiffer(d3, d4);
    }

    private static long cross(long[] origin, long[] a, long[] b) {
        return Math.subtractExact(
                Math.multiplyExact(a[0] - origin[0], b[1] - origin[1]),
                Math.multiplyExact(a[1] - origin[1], b[0] - origin[0]));
    }

    private static boolean signsDiffer(long left, long right) {
        return (left < 0 && right > 0) || (left > 0 && right < 0);
    }

    private static void requireInsideBounds(List<long[]> controls, WorldBlueprintV2.Bounds bounds) {
        long maxX = Math.multiplyExact((long) bounds.width() - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) bounds.length() - 1L, TerrainIntentV2.FIXED_SCALE);
        for (long[] point : controls) {
            if (point[0] < 0 || point[0] > maxX || point[1] < 0 || point[1] > maxZ) {
                throw failure("v2.mountain-bounds", "mountain ridge leaves world bounds");
            }
        }
    }

    private static void requireNoHardCoastConflict(
            List<long[]> controls,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds
    ) {
        long margin = TerrainIntentV2.FIXED_SCALE;
        long maxX = Math.multiplyExact((long) bounds.width() - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) bounds.length() - 1L, TerrainIntentV2.FIXED_SCALE);
        for (TerrainIntentV2.Constraint constraint : intent.constraints()) {
            if (!(constraint instanceof TerrainIntentV2.EdgeClassificationConstraint edge)
                    || edge.strength() != TerrainIntentV2.Strength.HARD
                    || edge.classification() != TerrainIntentV2.EdgeClassification.SEA) {
                continue;
            }
            for (long[] point : controls) {
                boolean conflict = switch (edge.edge()) {
                    case NORTH -> point[1] <= margin;
                    case SOUTH -> point[1] >= maxZ - margin;
                    case WEST -> point[0] <= margin;
                    case EAST -> point[0] >= maxX - margin;
                };
                if (conflict) {
                    throw failure("v2.mountain-hard-coast-conflict",
                            "mountain ridge conflicts with HARD SEA edge");
                }
            }
        }
    }

    private static List<MountainPlanV2.RidgePoint> sampleRidge(List<long[]> controls) {
        List<MountainPlanV2.RidgePoint> result = new ArrayList<>();
        long arc = 0L;
        result.add(new MountainPlanV2.RidgePoint(controls.getFirst()[0], controls.getFirst()[1], 0L, 1));
        for (int i = 1; i < controls.size(); i++) {
            long[] a = controls.get(i - 1);
            long[] b = controls.get(i);
            long dx = b[0] - a[0];
            long dz = b[1] - a[1];
            long distance = hypot(dx, dz);
            int steps = Math.max(1, Math.toIntExact((distance + TerrainIntentV2.FIXED_SCALE - 1L) / TerrainIntentV2.FIXED_SCALE));
            for (int step = 1; step <= steps; step++) {
                long x = a[0] + dx * step / steps;
                long z = a[1] + dz * step / steps;
                MountainPlanV2.RidgePoint previous = result.getLast();
                long segment = hypot(x - previous.xMillionths(), z - previous.zMillionths());
                if (segment == 0) {
                    continue;
                }
                arc = Math.addExact(arc, segment);
                result.add(new MountainPlanV2.RidgePoint(x, z, arc, i));
            }
        }
        if (result.size() < 2 || result.getLast().arcLengthMillionths() < Math.multiplyExact(32L, TerrainIntentV2.FIXED_SCALE)) {
            throw failure("v2.mountain-geometry", "mountain ridge is shorter than 32 blocks");
        }
        return List.copyOf(result);
    }

    private static List<MountainPlanV2.NamedStation> placePeaks(
            String featureId,
            List<MountainPlanV2.RidgePoint> ridge,
            int peakCount,
            int relief
    ) {
        long total = ridge.getLast().arcLengthMillionths();
        List<MountainPlanV2.NamedStation> peaks = new ArrayList<>();
        for (int index = 0; index < peakCount; index++) {
            long target = total * index / Math.max(1, peakCount - 1);
            MountainPlanV2.RidgePoint point = nearestByArc(ridge, target);
            int peakRelief = relief - (relief * Math.abs((peakCount - 1) / 2 - index) / Math.max(1, peakCount));
            peaks.add(new MountainPlanV2.NamedStation(
                    "peak-" + featureId + "-" + (index + 1),
                    point.xMillionths(),
                    point.zMillionths(),
                    point.arcLengthMillionths(),
                    Math.max(relief / 2, peakRelief)));
        }
        for (int i = 1; i < peaks.size(); i++) {
            if (peaks.get(i).arcLengthMillionths() <= peaks.get(i - 1).arcLengthMillionths()) {
                throw failure("v2.mountain-peak-order", "mountain peaks are not ordered along the ridge");
            }
        }
        return List.copyOf(peaks);
    }

    private static List<MountainPlanV2.NamedStation> placeSaddles(
            String featureId,
            List<MountainPlanV2.NamedStation> peaks,
            int relief
    ) {
        List<MountainPlanV2.NamedStation> saddles = new ArrayList<>();
        for (int i = 1; i < peaks.size(); i++) {
            MountainPlanV2.NamedStation a = peaks.get(i - 1);
            MountainPlanV2.NamedStation b = peaks.get(i);
            saddles.add(new MountainPlanV2.NamedStation(
                    "saddle-" + featureId + "-" + i,
                    (a.xMillionths() + b.xMillionths()) / 2L,
                    (a.zMillionths() + b.zMillionths()) / 2L,
                    (a.arcLengthMillionths() + b.arcLengthMillionths()) / 2L,
                    Math.max(8, Math.min(a.reliefBlocks(), b.reliefBlocks()) * 2 / 3)));
        }
        return List.copyOf(saddles);
    }

    private static List<MountainPlanV2.SpurSegment> placeSpurs(
            String featureId,
            List<MountainPlanV2.RidgePoint> ridge,
            List<MountainPlanV2.NamedStation> peaks,
            int spurCount,
            int halfWidth,
            WorldBlueprintV2.Bounds bounds
    ) {
        if (spurCount == 0) {
            return List.of();
        }
        long maxX = Math.multiplyExact((long) bounds.width() - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) bounds.length() - 1L, TerrainIntentV2.FIXED_SCALE);
        long spurLength = Math.multiplyExact((long) halfWidth * 2L, TerrainIntentV2.FIXED_SCALE);
        List<MountainPlanV2.SpurSegment> spurs = new ArrayList<>();
        for (int i = 0; i < spurCount; i++) {
            MountainPlanV2.NamedStation peak = peaks.get(Math.min(peaks.size() - 1, 1 + i % Math.max(1, peaks.size() - 1)));
            MountainPlanV2.RidgePoint near = nearestByArc(ridge, peak.arcLengthMillionths());
            int idx = ridge.indexOf(near);
            MountainPlanV2.RidgePoint tangentA = ridge.get(Math.max(0, idx - 1));
            MountainPlanV2.RidgePoint tangentB = ridge.get(Math.min(ridge.size() - 1, idx + 1));
            long tx = tangentB.xMillionths() - tangentA.xMillionths();
            long tz = tangentB.zMillionths() - tangentA.zMillionths();
            long nx = -tz;
            long nz = tx;
            long length = hypot(nx, nz);
            if (length == 0) {
                nx = TerrainIntentV2.FIXED_SCALE;
                nz = 0;
                length = TerrainIntentV2.FIXED_SCALE;
            }
            int sign = (i % 2 == 0) ? 1 : -1;
            long tipX = clamp(near.xMillionths() + sign * nx * spurLength / length, 0, maxX);
            long tipZ = clamp(near.zMillionths() + sign * nz * spurLength / length, 0, maxZ);
            long spurArc = hypot(tipX - near.xMillionths(), tipZ - near.zMillionths());
            if (spurArc < TerrainIntentV2.FIXED_SCALE) {
                throw failure("v2.mountain-spur", "mountain spur collapsed inside bounds");
            }
            spurs.add(new MountainPlanV2.SpurSegment(
                    "spur-" + featureId + "-" + (i + 1),
                    near.xMillionths(),
                    near.zMillionths(),
                    tipX,
                    tipZ,
                    spurArc));
        }
        return List.copyOf(spurs);
    }

    private static MountainPlanV2.RidgePoint nearestByArc(List<MountainPlanV2.RidgePoint> ridge, long target) {
        MountainPlanV2.RidgePoint best = ridge.getFirst();
        long bestDelta = Math.abs(best.arcLengthMillionths() - target);
        for (MountainPlanV2.RidgePoint point : ridge) {
            long delta = Math.abs(point.arcLengthMillionths() - target);
            if (delta < bestDelta) {
                best = point;
                bestDelta = delta;
            }
        }
        return best;
    }

    static long hypot(long x, long z) {
        return isqrt(Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(z, z)));
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

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int midpoint(TerrainIntentV2.IntRange value) {
        return value.minimum() + (value.maximum() - value.minimum()) / 2;
    }

    private static MountainGenerationException failure(String id, String message) {
        return new MountainGenerationException(id, message);
    }
}
