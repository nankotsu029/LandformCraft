package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformValleyModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.valley.ValleyFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ValleyPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one VALLEY spline feature. */
public final class ValleyPlanCompilerV2 {
    public ValleyPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.VALLEY) {
            throw failure("v2.valley-kind", "feature kind is not VALLEY");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)) {
            throw failure("v2.valley-geometry", "valley requires SPLINE geometry");
        }
        TerrainIntentV2.ValleyParameters parameters =
                (TerrainIntentV2.ValleyParameters) feature.parameters();
        try {
            List<ValleyPlanV2.ThalwegPoint> thalweg = flattenSpline(spline, bounds);
            int floorHalf = midpoint(parameters.floorHalfWidthBlocks());
            int shoulder = midpoint(parameters.shoulderWidthBlocks());
            int depth = midpoint(parameters.maxDepthBlocks());
            List<ValleyPlanV2.ConnectionAnchor> anchors = placeAnchors(
                    feature.id(), thalweg, parameters.connectionRole());
            int support = floorHalf + shoulder;
            if (support > LandformValleyModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.valley-budget", "valley support radius exceeds trusted halo");
            }
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 6L);
            if (work > ValleyPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.valley-budget", "valley profile/raster budget exceeded");
            }
            return new ValleyPlanV2(
                    ValleyPlanV2.VERSION,
                    feature.id(),
                    thalweg,
                    parameters.crossSection(),
                    floorHalf,
                    shoulder,
                    depth,
                    parameters.mountainTransitionBandBlocks(),
                    parameters.connectionRole(),
                    anchors,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    ValleyPlanV2.FLOOR_MASK_FIELD_ID,
                    ValleyPlanV2.SHOULDER_MASK_FIELD_ID,
                    ValleyPlanV2.DEPTH_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException("v2.valley-budget", "valley arithmetic overflow", exception);
        }
    }

    private static List<ValleyPlanV2.ThalwegPoint> flattenSpline(
            TerrainIntentV2.SplineGeometry spline,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<ValleyPlanV2.ThalwegPoint> result = new ArrayList<>();
        long arc = 0L;
        long[] previous = null;
        for (TerrainIntentV2.Point2 point : spline.points()) {
            long x = Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L);
            long z = Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L);
            if (previous != null) {
                arc = Math.addExact(arc, ValleyFixedMathV2.hypot(x - previous[0], z - previous[1]));
            }
            result.add(new ValleyPlanV2.ThalwegPoint(x, z, arc));
            previous = new long[]{x, z};
        }
        if (result.getLast().arcLengthMillionths()
                < Math.multiplyExact(12L, TerrainIntentV2.FIXED_SCALE)) {
            throw failure("v2.valley-geometry", "valley thalweg is shorter than 12 blocks");
        }
        return List.copyOf(result);
    }

    private static List<ValleyPlanV2.ConnectionAnchor> placeAnchors(
            String featureId,
            List<ValleyPlanV2.ThalwegPoint> thalweg,
            TerrainIntentV2.ValleyConnectionRole role
    ) {
        if (role == TerrainIntentV2.ValleyConnectionRole.NONE) {
            return List.of();
        }
        ValleyPlanV2.ThalwegPoint head = thalweg.getFirst();
        ValleyPlanV2.ThalwegPoint mouth = thalweg.getLast();
        return List.of(
                new ValleyPlanV2.ConnectionAnchor(
                        "anchor-" + featureId + "-head", role, head.xMillionths(), head.zMillionths()),
                new ValleyPlanV2.ConnectionAnchor(
                        "anchor-" + featureId + "-mouth", role, mouth.xMillionths(), mouth.zMillionths()));
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
