package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformVolcanicConeModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.VolcanicConePlanV2;

import java.util.Objects;

/** Integer-only compiler for one VOLCANIC_CONE point feature. */
public final class VolcanicConePlanCompilerV2 {
    public VolcanicConePlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.VOLCANIC_CONE) {
            throw failure("v2.volcanic-cone-kind", "feature kind is not VOLCANIC_CONE");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PointGeometry point)) {
            throw failure("v2.volcanic-cone-geometry", "volcanic cone requires POINT geometry");
        }
        TerrainIntentV2.VolcanicConeParameters parameters =
                (TerrainIntentV2.VolcanicConeParameters) feature.parameters();
        try {
            long centerX = scaleCoordinate(point.point().xMillionths(), bounds.width());
            long centerZ = scaleCoordinate(point.point().zMillionths(), bounds.length());
            int base = midpoint(parameters.baseRadiusBlocks());
            int summit = midpoint(parameters.summitHeightBlocksAboveSea());
            int crater = midpoint(parameters.craterRadiusBlocks());
            int floor = midpoint(parameters.craterFloorDepthBlocks());
            long drainage = midpoint(parameters.radialDrainage01());
            if (crater >= base) {
                throw failure("v2.volcanic-cone-crater", "crater radius must be strictly inside base");
            }
            if (floor > summit) {
                throw failure("v2.volcanic-cone-crater", "crater floor depth exceeds summit height");
            }
            int support = Math.min(64, base);
            if (support > LandformVolcanicConeModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.volcanic-cone-budget", "volcanic cone support radius exceeds trusted halo");
            }
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > VolcanicConePlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.volcanic-cone-budget", "volcanic cone profile/raster budget exceeded");
            }
            return new VolcanicConePlanV2(
                    VolcanicConePlanV2.VERSION,
                    feature.id(),
                    centerX,
                    centerZ,
                    base,
                    summit,
                    crater,
                    floor,
                    drainage,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    VolcanicConePlanV2.CONE_MASK_FIELD_ID,
                    VolcanicConePlanV2.CRATER_MASK_FIELD_ID,
                    VolcanicConePlanV2.DRAINAGE_FIELD_ID,
                    VolcanicConePlanV2.SOLID_OWNERSHIP_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.volcanic-cone-budget", "volcanic cone arithmetic overflow", exception);
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
