package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSeamountModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.BathymetryFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeamountPlanV2;

import java.util.List;
import java.util.Objects;

/** Compiles V2-10-04 seamount plans bound to an ocean-basin host by geometry checksum. */
public final class SeamountPlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public AbyssalPlainPlanCompilerV2.BasinHostBinding resolveBasinHost(
            TerrainIntentV2.Feature seamount,
            TerrainIntentV2 intent
    ) {
        Objects.requireNonNull(seamount, "seamount");
        Objects.requireNonNull(intent, "intent");
        String endpoint = "feature:" + seamount.id();
        List<TerrainIntentV2.Relation> parents = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.WITHIN)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isOceanBasinEndpoint(intent, relation.to()))
                .toList();
        if (parents.isEmpty()) {
            throw failure("v2.seamount-missing-basin",
                    "seamount requires exactly one HARD WITHIN ocean-basin parent");
        }
        if (parents.size() > 1) {
            throw failure("v2.seamount-ambiguous-basin",
                    "seamount has multiple HARD ocean-basin parent relations");
        }
        TerrainIntentV2.Relation parent = parents.getFirst();
        String parentId = parent.to().substring("feature:".length());
        TerrainIntentV2.Feature parentFeature = featureOf(intent, parentId);
        return new AbyssalPlainPlanCompilerV2.BasinHostBinding(
                parent.id(),
                parentId,
                codec.geometryChecksum(parentFeature.geometry()));
    }

    public SeamountPlanV2 compile(
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
        if (feature.kind() != TerrainIntentV2.FeatureKind.SEAMOUNT) {
            throw failure("v2.seamount-kind", "feature kind is not SEAMOUNT");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PointGeometry pointGeometry)) {
            throw failure("v2.seamount-geometry", "seamount requires POINT geometry");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.SeamountParameters params)) {
            throw failure("v2.seamount-params", "seamount parameters missing");
        }

        AbyssalPlainPlanCompilerV2.BasinHostBinding parent = resolveBasinHost(feature, intent);
        if (!parent.basinFeatureId().equals(basinPlan.featureId())) {
            throw failure("v2.seamount-missing-basin", "compiled basin plan does not match resolved parent");
        }

        long centerX = BathymetryPlanSupportV2.scaleCoordinate(
                pointGeometry.point().xMillionths(), bounds.width());
        long centerZ = BathymetryPlanSupportV2.scaleCoordinate(
                pointGeometry.point().zMillionths(), bounds.length());
        if (!BathymetryFixedMathV2.contains(basinPlan.rings(), centerX, centerZ)) {
            throw failure("v2.seamount-out-of-basin", "seamount center lies outside ocean basin host");
        }

        int baseRadius = BathymetryPlanSupportV2.midpoint(params.baseRadiusBlocks());
        int relief = BathymetryPlanSupportV2.midpoint(params.reliefBlocks());
        int summitDepth = BathymetryPlanSupportV2.midpoint(params.summitDepthBlocksBelowSea());
        assertBaseDiskInsideBasin(centerX, centerZ, baseRadius, basinPlan.rings());

        if (summitDepth + relief > basinPlan.selectedMaxDepthBlocksBelowSea() + 4) {
            throw failure("v2.seamount-depth",
                    "seamount summit relief exceeds basin max depth envelope");
        }
        if (summitDepth < 8) {
            throw failure("v2.seamount-depth", "seamount summit depth below minimum");
        }

        long radiusMillionths = Math.multiplyExact((long) baseRadius, TerrainIntentV2.FIXED_SCALE);
        long work = Math.multiplyExact(
                Math.multiplyExact(radiusMillionths / TerrainIntentV2.FIXED_SCALE + 1L, 2L),
                Math.max(1L, relief));
        if (work > SeamountPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
            throw failure("v2.seamount-budget", "seamount raster work units exceed budget");
        }
        int support = Math.min(
                LandformSeamountModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                Math.max(4, baseRadius / 2 + relief));

        return new SeamountPlanV2(
                SeamountPlanV2.VERSION,
                feature.id(),
                parent.basinFeatureId(),
                parent.basinGeometryChecksum(),
                parent.basinRelationId(),
                centerX,
                centerZ,
                baseRadius,
                relief,
                summitDepth,
                bounds.width(),
                bounds.length(),
                bounds.minY(),
                bounds.maxY(),
                bounds.waterLevel(),
                SeamountPlanV2.RELIEF_FIELD_ID,
                SeamountPlanV2.OWNERSHIP_FIELD_ID,
                SeamountPlanV2.SLOPE_FIELD_ID,
                SeamountPlanV2.FLUID_COLUMN_HINT_FIELD_ID,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    private static void assertBaseDiskInsideBasin(
            long centerX,
            long centerZ,
            int baseRadiusBlocks,
            List<com.github.nankotsu029.landformcraft.model.v2.foundation.BathymetryRingsV2.Ring> basinRings
    ) {
        long radius = Math.multiplyExact((long) baseRadiusBlocks, TerrainIntentV2.FIXED_SCALE);
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4.0;
            long sampleX = centerX + Math.round(Math.cos(angle) * radius);
            long sampleZ = centerZ + Math.round(Math.sin(angle) * radius);
            if (!BathymetryFixedMathV2.contains(basinRings, sampleX, sampleZ)) {
                throw failure("v2.seamount-out-of-basin",
                        "seamount base disk extends outside ocean basin host");
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
                .orElseThrow(() -> failure("v2.seamount-missing-basin",
                        "referenced feature missing: " + featureId));
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
