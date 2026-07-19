package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformRockyCoastModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.rockycoast.RockyCoastFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RockyCoastPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one ROCKY_COAST spline feature. */
public final class RockyCoastPlanCompilerV2 {
    public RockyCoastPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.ROCKY_COAST) {
            throw failure("v2.rocky-coast-kind", "feature kind is not ROCKY_COAST");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)) {
            throw failure("v2.rocky-coast-geometry", "rocky coast requires SPLINE geometry");
        }
        TerrainIntentV2.RockyCoastParameters parameters =
                (TerrainIntentV2.RockyCoastParameters) feature.parameters();
        try {
            List<RockyCoastPlanV2.CenterlinePoint> centerline = flattenSpline(spline, bounds);
            if (centerline.size() < 2) {
                throw failure("v2.rocky-coast-geometry", "rocky coast centerline is too short");
            }
            int shelfWidth = midpoint(parameters.rockShelfWidthBlocks());
            long exposure = midpoint(parameters.rockExposure01());
            int channelCount = midpoint(parameters.channelCount());
            int talusHandoff = midpoint(parameters.talusHandoffDepthBlocks());
            int support = shelfWidth;
            if (support > LandformRockyCoastModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.rocky-coast-budget", "rocky coast support radius exceeds trusted halo");
            }
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > RockyCoastPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.rocky-coast-budget", "rocky coast profile/raster budget exceeded");
            }
            return new RockyCoastPlanV2(
                    RockyCoastPlanV2.VERSION,
                    feature.id(),
                    centerline,
                    shelfWidth,
                    exposure,
                    parameters.shoreSide(),
                    channelCount,
                    parameters.capeOrBeachTransitionBandBlocks(),
                    talusHandoff,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    RockyCoastPlanV2.SHELF_MASK_FIELD_ID,
                    RockyCoastPlanV2.EXPOSURE_FIELD_ID,
                    RockyCoastPlanV2.CHANNEL_MASK_FIELD_ID,
                    RockyCoastPlanV2.TALUS_HANDOFF_FIELD_ID,
                    RockyCoastPlanV2.SOLID_OWNERSHIP_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.rocky-coast-budget", "rocky coast arithmetic overflow", exception);
        }
    }

    private static List<RockyCoastPlanV2.CenterlinePoint> flattenSpline(
            TerrainIntentV2.SplineGeometry spline,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<RockyCoastPlanV2.CenterlinePoint> result = new ArrayList<>();
        long arc = 0L;
        long[] previous = null;
        for (TerrainIntentV2.Point2 point : spline.points()) {
            long x = Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L);
            long z = Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L);
            if (previous != null) {
                arc = Math.addExact(arc, RockyCoastFixedMathV2.hypot(x - previous[0], z - previous[1]));
            }
            result.add(new RockyCoastPlanV2.CenterlinePoint(x, z, arc));
            previous = new long[]{x, z};
        }
        if (result.getLast().arcLengthMillionths()
                < Math.multiplyExact(8L, TerrainIntentV2.FIXED_SCALE)) {
            throw failure("v2.rocky-coast-geometry", "rocky coast centerline is shorter than 8 blocks");
        }
        return List.copyOf(result);
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static long midpoint(TerrainIntentV2.FixedRange range) {
        return range.minimumMillionths()
                + (range.maximumMillionths() - range.minimumMillionths()) / 2L;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
