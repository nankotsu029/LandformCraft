package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformAbyssalPlainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.BathymetryFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AbyssalPlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BathymetryRingsV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;

import java.util.List;
import java.util.Objects;

/** Compiles V2-10-04 abyssal-plain plans bound to an ocean-basin host by geometry checksum. */
public final class AbyssalPlainPlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public BasinHostBinding resolveBasinHost(TerrainIntentV2.Feature abyssal, TerrainIntentV2 intent) {
        Objects.requireNonNull(abyssal, "abyssal");
        Objects.requireNonNull(intent, "intent");
        String endpoint = "feature:" + abyssal.id();
        List<TerrainIntentV2.Relation> parents = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.WITHIN)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isOceanBasinEndpoint(intent, relation.to()))
                .toList();
        if (parents.isEmpty()) {
            throw failure("v2.abyssal-missing-basin",
                    "abyssal plain requires exactly one HARD WITHIN ocean-basin parent");
        }
        if (parents.size() > 1) {
            throw failure("v2.abyssal-ambiguous-basin",
                    "abyssal plain has multiple HARD ocean-basin parent relations");
        }
        TerrainIntentV2.Relation parent = parents.getFirst();
        String parentId = parent.to().substring("feature:".length());
        TerrainIntentV2.Feature parentFeature = featureOf(intent, parentId);
        return new BasinHostBinding(
                parent.id(),
                parentId,
                codec.geometryChecksum(parentFeature.geometry()));
    }

    public AbyssalPlainPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            OceanBasinPlanV2 basinPlan
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        Objects.requireNonNull(basinPlan, "basinPlan");
        if (feature.kind() != TerrainIntentV2.FeatureKind.ABYSSAL_PLAIN) {
            throw failure("v2.abyssal-kind", "feature kind is not ABYSSAL_PLAIN");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.abyssal-geometry", "abyssal plain requires POLYGON geometry");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.AbyssalPlainParameters params)) {
            throw failure("v2.abyssal-params", "abyssal plain parameters missing");
        }

        BasinHostBinding parent = resolveBasinHost(feature, intent);
        if (!parent.basinFeatureId().equals(basinPlan.featureId())) {
            throw failure("v2.abyssal-missing-basin", "compiled basin plan does not match resolved parent");
        }

        List<BathymetryRingsV2.Ring> rings = BathymetryPlanSupportV2.toRings(polygon, bounds);
        long interiorCells = BathymetryPlanSupportV2.estimateInteriorCells(rings, bounds);
        if (interiorCells < 1L) {
            throw failure("v2.abyssal-degenerate", "abyssal plain interior is empty or degenerate");
        }
        assertInsideBasin(rings, basinPlan.rings());

        int floorDepth = BathymetryPlanSupportV2.midpoint(params.floorDepthBlocksBelowSea());
        int floorRelief = BathymetryPlanSupportV2.midpoint(params.floorReliefBlocks());
        if (floorDepth < basinPlan.selectedMaxDepthBlocksBelowSea() - floorRelief) {
            throw failure("v2.abyssal-depth",
                    "abyssal floor depth must be at least basin max depth minus floor relief");
        }
        if (floorRelief > basinPlan.selectedFloorReliefBlocks()) {
            throw failure("v2.abyssal-depth", "abyssal floor relief exceeds basin floor relief");
        }

        long work = Math.multiplyExact(interiorCells, Math.max(1L, floorRelief + 1L));
        if (work > AbyssalPlainPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
            throw failure("v2.abyssal-budget", "abyssal plain raster work units exceed budget");
        }
        int support = Math.min(
                LandformAbyssalPlainModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                Math.max(4, floorRelief + 4));

        return new AbyssalPlainPlanV2(
                AbyssalPlainPlanV2.VERSION,
                feature.id(),
                parent.basinFeatureId(),
                parent.basinGeometryChecksum(),
                parent.basinRelationId(),
                rings,
                floorDepth,
                floorRelief,
                bounds.width(),
                bounds.length(),
                bounds.minY(),
                bounds.maxY(),
                bounds.waterLevel(),
                AbyssalPlainPlanV2.DEPTH_FIELD_ID,
                AbyssalPlainPlanV2.OWNERSHIP_FIELD_ID,
                AbyssalPlainPlanV2.RELIEF_FIELD_ID,
                AbyssalPlainPlanV2.FLUID_COLUMN_HINT_FIELD_ID,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    private static void assertInsideBasin(
            List<BathymetryRingsV2.Ring> abyssalRings,
            List<BathymetryRingsV2.Ring> basinRings
    ) {
        for (BathymetryRingsV2.Ring ring : abyssalRings) {
            for (BathymetryRingsV2.Vertex vertex : ring.vertices()) {
                if (!BathymetryFixedMathV2.contains(basinRings, vertex.xMillionths(), vertex.zMillionths())) {
                    throw failure("v2.abyssal-out-of-basin",
                            "abyssal plain vertex lies outside ocean basin host");
                }
            }
        }
    }

    private static boolean isOceanBasinEndpoint(TerrainIntentV2 intent, String endpoint) {
        if (!endpoint.startsWith("feature:")) {
            return false;
        }
        return featureOf(intent, endpoint.substring("feature:".length())).kind()
                == TerrainIntentV2.FeatureKind.OCEAN_BASIN;
    }

    private static TerrainIntentV2.Feature featureOf(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(feature -> feature.id().equals(featureId))
                .findFirst()
                .orElseThrow(() -> failure("v2.abyssal-missing-basin",
                        "referenced feature missing: " + featureId));
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }

    public record BasinHostBinding(
            String basinRelationId,
            String basinFeatureId,
            String basinGeometryChecksum
    ) {
    }
}
