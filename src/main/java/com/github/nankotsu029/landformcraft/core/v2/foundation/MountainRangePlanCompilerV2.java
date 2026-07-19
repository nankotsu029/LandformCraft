package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformMountainRangeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.mountain.MountainRangeFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MountainRangeComponentCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MountainRangePlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one MOUNTAIN_RANGE spline feature. */
public final class MountainRangePlanCompilerV2 {
    public MountainRangePlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE) {
            throw failure("v2.mountain-range-kind", "feature kind is not MOUNTAIN_RANGE");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)) {
            throw failure("v2.mountain-range-geometry", "mountain range requires SPLINE geometry");
        }
        TerrainIntentV2.MountainRangeParameters parameters =
                (TerrainIntentV2.MountainRangeParameters) feature.parameters();
        try {
            List<MountainRangePlanV2.RidgePoint> ridge = flattenSpline(spline, bounds);
            int peakCount = midpoint(parameters.peakCount());
            int halfWidth = midpoint(parameters.ridgeHalfWidthBlocks());
            int relief = midpoint(parameters.maxReliefBlocks());
            List<MountainRangePlanV2.Peak> peaks = placePeaks(feature.id(), ridge, peakCount, relief);
            List<MountainRangePlanV2.Saddle> saddles = placeSaddles(feature.id(), peaks, relief);
            int saddleBudget = midpoint(parameters.saddleCount());
            if (saddles.size() != peaks.size() - 1 || saddles.size() > saddleBudget) {
                throw failure("v2.mountain-range-saddle-budget", "mountain range saddle budget violated");
            }
            if (parameters.passCount() > peaks.size() - 1) {
                throw failure("v2.mountain-range-pass-budget", "mountain range pass budget exceeded");
            }
            List<MountainRangePlanV2.Pass> passes = placePasses(feature.id(), saddles, parameters.passCount());
            List<MountainRangePlanV2.Spur> spurs = placeSpurs(feature.id(), peaks, parameters.spurCount(), halfWidth);
            List<MountainRangePlanV2.Foothill> foothills = placeFoothills(
                    feature.id(), peaks, parameters.foothillBandBlocks());
            int support = Math.min(
                    LandformMountainRangeModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                    Math.addExact(halfWidth, parameters.foothillBandBlocks()));
            if (support > LandformMountainRangeModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.mountain-range-budget", "mountain range support radius exceeds trusted halo");
            }
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 8L);
            if (work > MountainRangePlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.mountain-range-budget", "mountain range profile/raster budget exceeded");
            }
            return new MountainRangePlanV2(
                    MountainRangePlanV2.VERSION,
                    feature.id(),
                    ridge,
                    peaks,
                    saddles,
                    spurs,
                    passes,
                    foothills,
                    halfWidth,
                    relief,
                    parameters.foothillBandBlocks(),
                    parameters.valleyTransitionBandBlocks(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    MountainRangePlanV2.RIDGE_MASK_FIELD_ID,
                    MountainRangePlanV2.PEAK_MASK_FIELD_ID,
                    MountainRangePlanV2.SADDLE_MASK_FIELD_ID,
                    MountainRangePlanV2.SPUR_MASK_FIELD_ID,
                    MountainRangePlanV2.PASS_MASK_FIELD_ID,
                    MountainRangePlanV2.ELEVATION_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.mountain-range-budget", "mountain range arithmetic overflow", exception);
        }
    }

    private static List<MountainRangePlanV2.RidgePoint> flattenSpline(
            TerrainIntentV2.SplineGeometry spline,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<MountainRangePlanV2.RidgePoint> result = new ArrayList<>();
        long arc = 0L;
        long[] previous = null;
        for (TerrainIntentV2.Point2 point : spline.points()) {
            long x = Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L);
            long z = Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L);
            if (previous != null) {
                arc = Math.addExact(arc, MountainRangeFixedMathV2.hypot(x - previous[0], z - previous[1]));
            }
            result.add(new MountainRangePlanV2.RidgePoint(x, z, arc));
            previous = new long[]{x, z};
        }
        if (result.getLast().arcLengthMillionths()
                < Math.multiplyExact(16L, TerrainIntentV2.FIXED_SCALE)) {
            throw failure("v2.mountain-range-geometry", "mountain range ridge is shorter than 16 blocks");
        }
        return List.copyOf(result);
    }

    private static List<MountainRangePlanV2.Peak> placePeaks(
            String featureId,
            List<MountainRangePlanV2.RidgePoint> ridge,
            int peakCount,
            int relief
    ) {
        long total = ridge.getLast().arcLengthMillionths();
        List<MountainRangePlanV2.Peak> peaks = new ArrayList<>();
        for (int index = 0; index < peakCount; index++) {
            long target = total * index / Math.max(1, peakCount - 1);
            MountainRangePlanV2.RidgePoint point = nearestByArc(ridge, target);
            int peakRelief = relief - (relief * Math.abs((peakCount - 1) / 2 - index)
                    / Math.max(1, peakCount));
            peaks.add(new MountainRangePlanV2.Peak(
                    MountainRangeComponentCatalogV2.derivedId(
                            MountainRangeComponentCatalogV2.ComponentRole.PEAK, featureId, index + 1),
                    point.xMillionths(),
                    point.zMillionths(),
                    point.arcLengthMillionths(),
                    Math.max(16, peakRelief)));
        }
        for (int i = 1; i < peaks.size(); i++) {
            if (peaks.get(i).arcLengthMillionths() <= peaks.get(i - 1).arcLengthMillionths()) {
                throw failure("v2.mountain-range-peak-order", "mountain range peaks are not ordered along ridge");
            }
        }
        return List.copyOf(peaks);
    }

    private static List<MountainRangePlanV2.Saddle> placeSaddles(
            String featureId,
            List<MountainRangePlanV2.Peak> peaks,
            int relief
    ) {
        List<MountainRangePlanV2.Saddle> saddles = new ArrayList<>();
        for (int i = 1; i < peaks.size(); i++) {
            MountainRangePlanV2.Peak a = peaks.get(i - 1);
            MountainRangePlanV2.Peak b = peaks.get(i);
            saddles.add(new MountainRangePlanV2.Saddle(
                    MountainRangeComponentCatalogV2.derivedId(
                            MountainRangeComponentCatalogV2.ComponentRole.SADDLE, featureId, i),
                    (a.xMillionths() + b.xMillionths()) / 2L,
                    (a.zMillionths() + b.zMillionths()) / 2L,
                    (a.arcLengthMillionths() + b.arcLengthMillionths()) / 2L,
                    Math.max(1, Math.min(a.reliefBlocks(), b.reliefBlocks()) * 2 / 3)));
        }
        return List.copyOf(saddles);
    }

    private static List<MountainRangePlanV2.Pass> placePasses(
            String featureId,
            List<MountainRangePlanV2.Saddle> saddles,
            int passCount
    ) {
        List<MountainRangePlanV2.Pass> passes = new ArrayList<>();
        for (int i = 0; i < passCount && i < saddles.size(); i++) {
            MountainRangePlanV2.Saddle saddle = saddles.get(i);
            passes.add(new MountainRangePlanV2.Pass(
                    MountainRangeComponentCatalogV2.derivedId(
                            MountainRangeComponentCatalogV2.ComponentRole.PASS, featureId, i + 1),
                    saddle.xMillionths(),
                    saddle.zMillionths(),
                    saddle.arcLengthMillionths(),
                    Math.max(1, saddle.reliefBlocks())));
        }
        return List.copyOf(passes);
    }

    private static List<MountainRangePlanV2.Spur> placeSpurs(
            String featureId,
            List<MountainRangePlanV2.Peak> peaks,
            int spurCount,
            int halfWidth
    ) {
        List<MountainRangePlanV2.Spur> spurs = new ArrayList<>();
        for (int i = 0; i < spurCount && i < peaks.size(); i++) {
            MountainRangePlanV2.Peak peak = peaks.get(i);
            spurs.add(new MountainRangePlanV2.Spur(
                    MountainRangeComponentCatalogV2.derivedId(
                            MountainRangeComponentCatalogV2.ComponentRole.SPUR, featureId, i + 1),
                    peak.xMillionths(),
                    peak.zMillionths(),
                    peak.arcLengthMillionths(),
                    Math.max(1, halfWidth)));
        }
        return List.copyOf(spurs);
    }

    private static List<MountainRangePlanV2.Foothill> placeFoothills(
            String featureId,
            List<MountainRangePlanV2.Peak> peaks,
            int bandBlocks
    ) {
        List<MountainRangePlanV2.Foothill> foothills = new ArrayList<>();
        foothills.add(new MountainRangePlanV2.Foothill(
                MountainRangeComponentCatalogV2.derivedId(
                        MountainRangeComponentCatalogV2.ComponentRole.FOOTHILL, featureId, 1),
                peaks.getFirst().xMillionths(),
                peaks.getFirst().zMillionths(),
                bandBlocks));
        if (peaks.size() > 1) {
            foothills.add(new MountainRangePlanV2.Foothill(
                    MountainRangeComponentCatalogV2.derivedId(
                            MountainRangeComponentCatalogV2.ComponentRole.FOOTHILL, featureId, 2),
                    peaks.getLast().xMillionths(),
                    peaks.getLast().zMillionths(),
                    bandBlocks));
        }
        return List.copyOf(foothills);
    }

    private static MountainRangePlanV2.RidgePoint nearestByArc(
            List<MountainRangePlanV2.RidgePoint> ridge,
            long target
    ) {
        MountainRangePlanV2.RidgePoint best = ridge.getFirst();
        long bestDelta = Math.abs(best.arcLengthMillionths() - target);
        for (MountainRangePlanV2.RidgePoint point : ridge) {
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
