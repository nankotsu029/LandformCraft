package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformContinentalSlopeModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BathymetryRingsV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalSlopePlanV2;

import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one CONTINENTAL_SLOPE polygon feature. */
public final class ContinentalSlopePlanCompilerV2 {
    public ContinentalSlopePlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.CONTINENTAL_SLOPE) {
            throw failure("v2.continental-slope-kind", "feature kind is not CONTINENTAL_SLOPE");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.continental-slope-geometry", "continental slope requires POLYGON geometry");
        }
        TerrainIntentV2.ContinentalSlopeParameters parameters =
                (TerrainIntentV2.ContinentalSlopeParameters) feature.parameters();
        try {
            List<BathymetryRingsV2.Ring> rings = BathymetryPlanSupportV2.toRings(polygon, bounds);
            long interiorCells = BathymetryPlanSupportV2.estimateInteriorCells(rings, bounds);
            if (interiorCells < 1L) {
                throw failure("v2.continental-slope-degenerate",
                        "continental slope interior is empty or degenerate");
            }
            int slopeWidth = BathymetryPlanSupportV2.midpoint(parameters.slopeWidthBlocks());
            int upper = BathymetryPlanSupportV2.midpoint(parameters.upperDepthBlocksBelowSea());
            int lower = BathymetryPlanSupportV2.midpoint(parameters.lowerDepthBlocksBelowSea());
            if (lower <= upper) {
                throw failure("v2.continental-slope-non-monotone",
                        "continental slope lower depth must exceed upper depth");
            }
            long maxGradient = BathymetryPlanSupportV2.midpoint(parameters.maxGradient01());
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > ContinentalSlopePlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.continental-slope-budget",
                        "continental slope profile/raster budget exceeded");
            }
            int support = Math.min(64, Math.max(8, slopeWidth / 4));
            if (support > LandformContinentalSlopeModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.continental-slope-budget",
                        "continental slope support radius exceeds trusted halo");
            }
            return new ContinentalSlopePlanV2(
                    ContinentalSlopePlanV2.VERSION,
                    feature.id(),
                    rings,
                    slopeWidth,
                    upper,
                    lower,
                    maxGradient,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    ContinentalSlopePlanV2.DEPTH_FIELD_ID,
                    ContinentalSlopePlanV2.SLOPE_FIELD_ID,
                    ContinentalSlopePlanV2.COAST_DISTANCE_FIELD_ID,
                    ContinentalSlopePlanV2.OWNERSHIP_FIELD_ID,
                    ContinentalSlopePlanV2.FLUID_COLUMN_HINT_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.continental-slope-budget", "continental slope arithmetic overflow", exception);
        }
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
