package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformEscarpmentModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.escarpment.EscarpmentFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.EscarpmentPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one ESCARPMENT spline feature. */
public final class EscarpmentPlanCompilerV2 {
    private static final long MINIMUM_LONG_SCARP_ARC = Math.multiplyExact(24L, TerrainIntentV2.FIXED_SCALE);

    public EscarpmentPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.ESCARPMENT) {
            throw failure("v2.escarpment-kind", "feature kind is not ESCARPMENT");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)) {
            throw failure("v2.escarpment-geometry", "escarpment requires SPLINE geometry");
        }
        TerrainIntentV2.EscarpmentParameters parameters =
                (TerrainIntentV2.EscarpmentParameters) feature.parameters();
        try {
            List<EscarpmentPlanV2.CenterlinePoint> centerline = flattenSpline(spline, bounds);
            if (centerline.size() < 2) {
                throw failure("v2.escarpment-geometry", "escarpment centerline is too short");
            }
            if (centerline.getLast().arcLengthMillionths() < MINIMUM_LONG_SCARP_ARC) {
                throw failure("v2.escarpment-too-short", "escarpment centerline is shorter than 24 blocks");
            }
            int scarpHeight = midpoint(parameters.scarpHeightBlocks());
            int talusWidth = midpoint(parameters.talusWidthBlocks());
            int floorDrop = midpoint(parameters.floorDropBlocks());
            int support = Math.max(Math.max(talusWidth, scarpHeight / 4), 4);
            if (support > LandformEscarpmentModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.escarpment-budget", "escarpment support radius exceeds trusted halo");
            }
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > EscarpmentPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.escarpment-budget", "escarpment profile/raster budget exceeded");
            }
            return new EscarpmentPlanV2(
                    EscarpmentPlanV2.VERSION,
                    feature.id(),
                    centerline,
                    scarpHeight,
                    talusWidth,
                    floorDrop,
                    parameters.dropSide(),
                    parameters.plateauTransitionBandBlocks(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.width(),
                    bounds.length(),
                    EscarpmentPlanV2.FACE_MASK_FIELD_ID,
                    EscarpmentPlanV2.TALUS_MASK_FIELD_ID,
                    EscarpmentPlanV2.FLOOR_MASK_FIELD_ID,
                    EscarpmentPlanV2.OWNERSHIP_FIELD_ID,
                    EscarpmentPlanV2.MATERIAL_HANDOFF_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.escarpment-budget", "escarpment arithmetic overflow", exception);
        }
    }

    private static List<EscarpmentPlanV2.CenterlinePoint> flattenSpline(
            TerrainIntentV2.SplineGeometry spline,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<EscarpmentPlanV2.CenterlinePoint> result = new ArrayList<>();
        long arc = 0L;
        long[] previous = null;
        for (TerrainIntentV2.Point2 point : spline.points()) {
            long x = Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L);
            long z = Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L);
            if (previous != null) {
                arc = Math.addExact(arc, EscarpmentFixedMathV2.hypot(x - previous[0], z - previous[1]));
            }
            result.add(new EscarpmentPlanV2.CenterlinePoint(x, z, arc));
            previous = new long[]{x, z};
        }
        return List.copyOf(result);
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
