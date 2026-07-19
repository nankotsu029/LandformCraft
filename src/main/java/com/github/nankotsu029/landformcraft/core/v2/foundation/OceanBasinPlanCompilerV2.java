package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformOceanBasinModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BathymetryRingsV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;

import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one OCEAN_BASIN polygon feature. */
public final class OceanBasinPlanCompilerV2 {
    public OceanBasinPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.OCEAN_BASIN) {
            throw failure("v2.ocean-basin-kind", "feature kind is not OCEAN_BASIN");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.ocean-basin-geometry", "ocean basin requires POLYGON geometry");
        }
        TerrainIntentV2.OceanBasinParameters parameters =
                (TerrainIntentV2.OceanBasinParameters) feature.parameters();
        try {
            List<BathymetryRingsV2.Ring> rings = BathymetryPlanSupportV2.toRings(polygon, bounds);
            long interiorCells = BathymetryPlanSupportV2.estimateInteriorCells(rings, bounds);
            if (interiorCells < 1L) {
                throw failure("v2.ocean-basin-degenerate", "ocean basin interior is empty or degenerate");
            }
            int maxDepth = BathymetryPlanSupportV2.midpoint(parameters.maxDepthBlocksBelowSea());
            int floorRelief = BathymetryPlanSupportV2.midpoint(parameters.floorReliefBlocks());
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > OceanBasinPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.ocean-basin-budget", "ocean basin profile/raster budget exceeded");
            }
            int support = Math.min(64, Math.max(8, floorRelief + 8));
            if (support > LandformOceanBasinModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.ocean-basin-budget", "ocean basin support radius exceeds trusted halo");
            }
            return new OceanBasinPlanV2(
                    OceanBasinPlanV2.VERSION,
                    feature.id(),
                    rings,
                    maxDepth,
                    floorRelief,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    OceanBasinPlanV2.DEPTH_FIELD_ID,
                    OceanBasinPlanV2.SLOPE_FIELD_ID,
                    OceanBasinPlanV2.COAST_DISTANCE_FIELD_ID,
                    OceanBasinPlanV2.OWNERSHIP_FIELD_ID,
                    OceanBasinPlanV2.FLUID_COLUMN_HINT_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.ocean-basin-budget", "ocean basin arithmetic overflow", exception);
        }
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
