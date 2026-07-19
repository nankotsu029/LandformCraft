package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformContinentalShelfModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BathymetryRingsV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalShelfPlanV2;

import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one CONTINENTAL_SHELF polygon feature. */
public final class ContinentalShelfPlanCompilerV2 {
    public ContinentalShelfPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF) {
            throw failure("v2.continental-shelf-kind", "feature kind is not CONTINENTAL_SHELF");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.continental-shelf-geometry", "continental shelf requires POLYGON geometry");
        }
        TerrainIntentV2.ContinentalShelfParameters parameters =
                (TerrainIntentV2.ContinentalShelfParameters) feature.parameters();
        try {
            List<BathymetryRingsV2.Ring> rings = BathymetryPlanSupportV2.toRings(polygon, bounds);
            long interiorCells = BathymetryPlanSupportV2.estimateInteriorCells(rings, bounds);
            if (interiorCells < 1L) {
                throw failure("v2.continental-shelf-degenerate",
                        "continental shelf interior is empty or degenerate");
            }
            int shelfWidth = BathymetryPlanSupportV2.midpoint(parameters.shelfWidthBlocks());
            int shelfDepth = BathymetryPlanSupportV2.midpoint(parameters.shelfDepthBlocksBelowSea());
            int coastBand = BathymetryPlanSupportV2.midpoint(parameters.coastDistanceBandBlocks());
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > ContinentalShelfPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.continental-shelf-budget",
                        "continental shelf profile/raster budget exceeded");
            }
            int support = Math.min(64, Math.max(shelfWidth / 4, coastBand));
            if (support > LandformContinentalShelfModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.continental-shelf-budget",
                        "continental shelf support radius exceeds trusted halo");
            }
            return new ContinentalShelfPlanV2(
                    ContinentalShelfPlanV2.VERSION,
                    feature.id(),
                    rings,
                    shelfWidth,
                    shelfDepth,
                    coastBand,
                    parameters.seawardSide(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    ContinentalShelfPlanV2.DEPTH_FIELD_ID,
                    ContinentalShelfPlanV2.SLOPE_FIELD_ID,
                    ContinentalShelfPlanV2.COAST_DISTANCE_FIELD_ID,
                    ContinentalShelfPlanV2.OWNERSHIP_FIELD_ID,
                    ContinentalShelfPlanV2.FLUID_COLUMN_HINT_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.continental-shelf-budget", "continental shelf arithmetic overflow", exception);
        }
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
