package com.github.nankotsu029.landformcraft.generator.v2.landform.fjord;

import com.github.nankotsu029.landformcraft.model.v2.FjordPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for a single centerline fjord with a declared marine outlet. */
public final class FjordPlanCompilerV2 {
    public FjordPlanV2 compile(TerrainIntentV2.Feature feature, TerrainIntentV2 intent,
                               WorldBlueprintV2.Bounds bounds, String geometryChecksum) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        if (feature.kind() != TerrainIntentV2.FeatureKind.FJORD) {
            throw failure("v2.fjord-kind", "feature kind is not FJORD");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)) {
            throw failure("v2.fjord-geometry", "fjord requires SPLINE geometry");
        }
        try {
            Outlet outlet = outlet(feature, intent);
            FjordPlanV2.GlacialWallPlanHook wallHook = wallHook(feature, intent);
            TerrainIntentV2.FjordParameters parameters = (TerrainIntentV2.FjordParameters) feature.parameters();
            int surfaceWidth = midpoint(parameters.surfaceWidthBlocks());
            int channelDepth = midpoint(parameters.channelDepthBlocks());
            List<FjordPlanV2.CenterlinePoint> line = sample(spline, bounds, outlet.boundary());
            long total = line.getLast().arcLengthMillionths();
            long minimum = Math.multiplyExact((long) surfaceWidth * 5L, TerrainIntentV2.FIXED_SCALE);
            // Ratio < 5 means the channel is too wide for its marine length (not slender enough).
            if (total < minimum) {
                throw failure("v2.fjord-too-wide", "fjord centerline/surface-width ratio is below 5");
            }
            long ratio = total / Math.multiplyExact((long) surfaceWidth, TerrainIntentV2.FIXED_SCALE);
            if (ratio > 14L) {
                throw failure("v2.fjord-too-slender", "fjord centerline/surface-width ratio is above 14");
            }
            int relief = Math.min(160, Math.max(8, Math.max(channelDepth, parameters.headBasinRadiusBlocks() / 2)));
            int support = Math.max((surfaceWidth + 1) / 2, parameters.headBasinRadiusBlocks());
            if (support > LandformFjordModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.fjord-budget", "fjord support radius exceeds trusted halo");
            }
            long box = Math.multiplyExact((long) bounds.width(), bounds.length());
            // Five fields are sampled per raster cell; centerline distance is streamed, not retained per cell.
            long work = Math.multiplyExact(box, 5L);
            if (work > FjordPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.fjord-budget", "fjord profile/raster budget exceeded");
            }
            return new FjordPlanV2(FjordPlanV2.VERSION, feature.id(), outlet.relationId(), outlet.boundary(),
                    parameters.crossSection(), wallHook, line,
                    parameters.surfaceWidthBlocks().minimum(), parameters.surfaceWidthBlocks().maximum(), surfaceWidth,
                    parameters.channelDepthBlocks().minimum(), parameters.channelDepthBlocks().maximum(), channelDepth,
                    parameters.headBasinRadiusBlocks(), relief,
                    bounds.minY(), bounds.maxY(), bounds.waterLevel(), bounds.width(), bounds.length(),
                    LandformFjordModuleV2.CHANNEL_MASK_FIELD_ID, LandformFjordModuleV2.FLOOR_MASK_FIELD_ID,
                    LandformFjordModuleV2.SIDEWALL_MASK_FIELD_ID, LandformFjordModuleV2.THALWEG_DEPTH_FIELD_ID,
                    LandformFjordModuleV2.SIDEWALL_RELIEF_FIELD_ID, support, work, geometryChecksum);
        } catch (ArithmeticException exception) {
            throw new FjordGenerationException("v2.fjord-budget", "fjord arithmetic overflow", exception);
        }
    }

    private static Outlet outlet(TerrainIntentV2.Feature feature, TerrainIntentV2 intent) {
        List<TerrainIntentV2.Relation> matches = intent.relations().stream()
                .filter(r -> r.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO
                        && r.strength() == TerrainIntentV2.Strength.HARD
                        && r.from().equals("feature:" + feature.id()) && r.to().startsWith("boundary:")).toList();
        if (matches.size() != 1) throw failure("v2.fjord-boundary-conflict",
                "fjord requires exactly one HARD EMPTIES_INTO boundary");
        TerrainIntentV2.Edge boundary;
        try { boundary = TerrainIntentV2.Edge.valueOf(matches.getFirst().to().substring("boundary:".length())); }
        catch (IllegalArgumentException exception) { throw failure("v2.fjord-boundary-conflict", "fjord outlet boundary is invalid"); }
        boolean sea = intent.constraints().stream().filter(TerrainIntentV2.EdgeClassificationConstraint.class::isInstance)
                .map(TerrainIntentV2.EdgeClassificationConstraint.class::cast)
                .anyMatch(c -> c.strength() == TerrainIntentV2.Strength.HARD && c.edge() == boundary
                        && c.classification() == TerrainIntentV2.EdgeClassification.SEA);
        if (!sea) throw failure("v2.fjord-hard-boundary-conflict", "fjord outlet lacks HARD SEA classification");
        return new Outlet(matches.getFirst().id(), boundary);
    }

    private static FjordPlanV2.GlacialWallPlanHook wallHook(TerrainIntentV2.Feature fjord, TerrainIntentV2 intent) {
        List<TerrainIntentV2.Relation> matches = intent.relations().stream()
                .filter(r -> r.kind() == TerrainIntentV2.RelationKind.FLANKS && r.strength() == TerrainIntentV2.Strength.HARD
                        && r.to().equals("feature:" + fjord.id()) && r.from().startsWith("feature:")).toList();
        if (matches.isEmpty()) return null;
        if (matches.size() != 1) throw failure("v2.fjord-broken-wall", "fjord permits one glacial wall hook");
        String wallId = matches.getFirst().from().substring("feature:".length());
        TerrainIntentV2.Feature wall = intent.features().stream().filter(f -> f.id().equals(wallId)).findFirst()
                .orElseThrow(() -> failure("v2.fjord-broken-wall", "glacial wall feature is missing"));
        if (wall.kind() != TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE) {
            throw failure("v2.fjord-broken-wall", "fjord FLANKS hook must originate at GLACIAL_MOUNTAIN_RANGE");
        }
        return new FjordPlanV2.GlacialWallPlanHook(wallId, matches.getFirst().id());
    }

    private static List<FjordPlanV2.CenterlinePoint> sample(TerrainIntentV2.SplineGeometry spline,
                                                              WorldBlueprintV2.Bounds bounds, TerrainIntentV2.Edge edge) {
        List<long[]> controls = new ArrayList<>();
        for (TerrainIntentV2.Point2 p : spline.points()) {
            controls.add(new long[]{Math.multiplyExact((long) p.xMillionths(), bounds.width() - 1L),
                    Math.multiplyExact((long) p.zMillionths(), bounds.length() - 1L)});
        }
        boolean first = onBoundary(controls.getFirst(), edge, bounds);
        boolean last = onBoundary(controls.getLast(), edge, bounds);
        if (!first && !last) throw failure("v2.fjord-landlocked", "fjord has no endpoint on receiving sea boundary");
        if (first && last) throw failure("v2.fjord-boundary-conflict", "fjord has ambiguous sea endpoints");
        if (!first) {
            List<long[]> reversed = new ArrayList<>();
            for (int i = controls.size() - 1; i >= 0; i--) reversed.add(controls.get(i));
            controls = reversed;
        }
        List<FjordPlanV2.CenterlinePoint> result = new ArrayList<>();
        long arc = 0L;
        result.add(new FjordPlanV2.CenterlinePoint(controls.getFirst()[0], controls.getFirst()[1], 0L));
        for (int i = 1; i < controls.size(); i++) {
            long[] a = controls.get(i - 1), b = controls.get(i);
            long dx = b[0] - a[0], dz = b[1] - a[1];
            long distance = hypot(dx, dz);
            int steps = Math.max(1, Math.toIntExact((distance + TerrainIntentV2.FIXED_SCALE - 1L) / TerrainIntentV2.FIXED_SCALE));
            for (int step = 1; step <= steps; step++) {
                long x = a[0] + dx * step / steps, z = a[1] + dz * step / steps;
                FjordPlanV2.CenterlinePoint previous = result.getLast();
                long segment = hypot(x - previous.xMillionths(), z - previous.zMillionths());
                if (segment == 0) continue;
                arc = Math.addExact(arc, segment);
                result.add(new FjordPlanV2.CenterlinePoint(x, z, arc));
            }
        }
        return List.copyOf(result);
    }

    private static boolean onBoundary(long[] p, TerrainIntentV2.Edge edge, WorldBlueprintV2.Bounds b) {
        return FjordPlanV2.onBoundary(new FjordPlanV2.CenterlinePoint(p[0], p[1], 0), edge, b.width(), b.length());
    }
    static long hypot(long x, long z) { return isqrt(Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(z, z))); }
    private static long isqrt(long value) {
        long low = 0, high = Math.min(value, 3_037_000_499L);
        while (low <= high) { long mid = (low + high) >>> 1; if (mid != 0 && mid > value / mid) high = mid - 1; else low = mid + 1; }
        return high;
    }
    private static int midpoint(TerrainIntentV2.IntRange value) { return value.minimum() + (value.maximum() - value.minimum()) / 2; }
    private static FjordGenerationException failure(String id, String message) { return new FjordGenerationException(id, message); }
    private record Outlet(String relationId, TerrainIntentV2.Edge boundary) {}
}
