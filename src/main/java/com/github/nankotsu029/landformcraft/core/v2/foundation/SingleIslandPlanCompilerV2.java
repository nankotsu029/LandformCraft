package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSingleIslandModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SingleIslandPlanV2;

import java.util.Objects;

/** Integer-only compiler for one SINGLE_ISLAND point feature. */
public final class SingleIslandPlanCompilerV2 {
    public SingleIslandPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.SINGLE_ISLAND) {
            throw failure("v2.single-island-kind", "feature kind is not SINGLE_ISLAND");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PointGeometry point)) {
            throw failure("v2.single-island-geometry", "single island requires POINT geometry");
        }
        TerrainIntentV2.SingleIslandParameters parameters =
                (TerrainIntentV2.SingleIslandParameters) feature.parameters();
        try {
            long centerX = scaleCoordinate(point.point().xMillionths(), bounds.width());
            long centerZ = scaleCoordinate(point.point().zMillionths(), bounds.length());
            int radius = midpoint(parameters.radiusBlocks());
            int summit = midpoint(parameters.summitHeightBlocksAboveSea());
            int shore = midpoint(parameters.shoreBandWidthBlocks());
            long drainage = midpoint(parameters.radialDrainage01());
            int apron = midpoint(parameters.submarineApronDepthBlocks());
            int support = Math.min(64, radius + apron);
            if (support > LandformSingleIslandModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.single-island-budget", "single island support radius exceeds trusted halo");
            }
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > SingleIslandPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.single-island-budget", "single island profile/raster budget exceeded");
            }
            return new SingleIslandPlanV2(
                    SingleIslandPlanV2.VERSION,
                    feature.id(),
                    centerX,
                    centerZ,
                    radius,
                    summit,
                    shore,
                    drainage,
                    apron,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    SingleIslandPlanV2.ISLAND_MASK_FIELD_ID,
                    SingleIslandPlanV2.SHORE_FIELD_ID,
                    SingleIslandPlanV2.DRAINAGE_FIELD_ID,
                    SingleIslandPlanV2.APRON_FIELD_ID,
                    SingleIslandPlanV2.SOLID_OWNERSHIP_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.single-island-budget", "single island arithmetic overflow", exception);
        }
    }

    private static long scaleCoordinate(int normalizedMillionths, int span) {
        return Math.multiplyExact((long) normalizedMillionths, span - 1L);
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
