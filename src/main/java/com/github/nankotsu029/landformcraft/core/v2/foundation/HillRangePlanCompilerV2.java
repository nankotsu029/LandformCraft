package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformHillRangeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.hill.HillRangeFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.HillRangePlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one HILL_RANGE spline feature. */
public final class HillRangePlanCompilerV2 {
    public HillRangePlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.HILL_RANGE) {
            throw failure("v2.hill-kind", "feature kind is not HILL_RANGE");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)) {
            throw failure("v2.hill-geometry", "hill range requires SPLINE geometry");
        }
        TerrainIntentV2.HillRangeParameters parameters =
                (TerrainIntentV2.HillRangeParameters) feature.parameters();
        try {
            List<HillRangePlanV2.RidgePoint> ridge = flattenSpline(spline, bounds);
            if (ridge.size() < 2) {
                throw failure("v2.hill-geometry", "hill range ridge is too short");
            }
            int stationCount = midpoint(parameters.ridgeStationCount());
            int halfWidth = midpoint(parameters.ridgeHalfWidthBlocks());
            int relief = midpoint(parameters.maxReliefBlocks());
            List<HillRangePlanV2.RidgeStation> stations = placeStations(feature.id(), ridge, stationCount, relief);
            List<HillRangePlanV2.Saddle> saddles = placeSaddles(feature.id(), stations, relief);
            int saddleBudget = midpoint(parameters.saddleCount());
            if (saddles.size() != stations.size() - 1 || saddles.size() > saddleBudget) {
                throw failure("v2.hill-saddle-budget", "hill range closed ridge/saddle budget violated");
            }
            int support = Math.max(halfWidth, 1);
            if (support > LandformHillRangeModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.hill-budget", "hill range support radius exceeds trusted halo");
            }
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > HillRangePlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.hill-budget", "hill range profile/raster budget exceeded");
            }
            return new HillRangePlanV2(
                    HillRangePlanV2.VERSION,
                    feature.id(),
                    ridge,
                    stations,
                    saddles,
                    halfWidth,
                    relief,
                    parameters.plainTransitionBandBlocks(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    HillRangePlanV2.RIDGE_MASK_FIELD_ID,
                    HillRangePlanV2.SADDLE_MASK_FIELD_ID,
                    HillRangePlanV2.ELEVATION_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException("v2.hill-budget", "hill range arithmetic overflow", exception);
        }
    }

    private static List<HillRangePlanV2.RidgePoint> flattenSpline(
            TerrainIntentV2.SplineGeometry spline,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<HillRangePlanV2.RidgePoint> result = new ArrayList<>();
        long arc = 0L;
        long[] previous = null;
        for (TerrainIntentV2.Point2 point : spline.points()) {
            long x = Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L);
            long z = Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L);
            if (previous != null) {
                arc = Math.addExact(arc, HillRangeFixedMathV2.hypot(x - previous[0], z - previous[1]));
            }
            result.add(new HillRangePlanV2.RidgePoint(x, z, arc));
            previous = new long[]{x, z};
        }
        if (result.getLast().arcLengthMillionths()
                < Math.multiplyExact(8L, TerrainIntentV2.FIXED_SCALE)) {
            throw failure("v2.hill-geometry", "hill range ridge is shorter than 8 blocks");
        }
        return List.copyOf(result);
    }

    private static List<HillRangePlanV2.RidgeStation> placeStations(
            String featureId,
            List<HillRangePlanV2.RidgePoint> ridge,
            int stationCount,
            int relief
    ) {
        long total = ridge.getLast().arcLengthMillionths();
        List<HillRangePlanV2.RidgeStation> stations = new ArrayList<>();
        for (int index = 0; index < stationCount; index++) {
            long target = total * index / Math.max(1, stationCount - 1);
            HillRangePlanV2.RidgePoint point = nearestByArc(ridge, target);
            int stationRelief = relief - (relief * Math.abs((stationCount - 1) / 2 - index)
                    / Math.max(1, stationCount));
            stations.add(new HillRangePlanV2.RidgeStation(
                    "station-" + featureId + "-" + (index + 1),
                    point.xMillionths(),
                    point.zMillionths(),
                    point.arcLengthMillionths(),
                    Math.max(1, stationRelief)));
        }
        for (int i = 1; i < stations.size(); i++) {
            if (stations.get(i).arcLengthMillionths() <= stations.get(i - 1).arcLengthMillionths()) {
                throw failure("v2.hill-station-order", "hill range stations are not ordered along ridge");
            }
        }
        return List.copyOf(stations);
    }

    private static List<HillRangePlanV2.Saddle> placeSaddles(
            String featureId,
            List<HillRangePlanV2.RidgeStation> stations,
            int relief
    ) {
        List<HillRangePlanV2.Saddle> saddles = new ArrayList<>();
        for (int i = 1; i < stations.size(); i++) {
            HillRangePlanV2.RidgeStation a = stations.get(i - 1);
            HillRangePlanV2.RidgeStation b = stations.get(i);
            saddles.add(new HillRangePlanV2.Saddle(
                    "saddle-" + featureId + "-" + i,
                    (a.xMillionths() + b.xMillionths()) / 2L,
                    (a.zMillionths() + b.zMillionths()) / 2L,
                    (a.arcLengthMillionths() + b.arcLengthMillionths()) / 2L,
                    Math.max(1, Math.min(a.reliefBlocks(), b.reliefBlocks()) * 2 / 3)));
        }
        return List.copyOf(saddles);
    }

    private static HillRangePlanV2.RidgePoint nearestByArc(
            List<HillRangePlanV2.RidgePoint> ridge,
            long target
    ) {
        HillRangePlanV2.RidgePoint best = ridge.getFirst();
        long bestDelta = Math.abs(best.arcLengthMillionths() - target);
        for (HillRangePlanV2.RidgePoint point : ridge) {
            long delta = Math.abs(point.arcLengthMillionths() - target);
            if (delta < bestDelta) {
                best = point;
                bestDelta = delta;
            }
        }
        return best;
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
