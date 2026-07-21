package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.List;

/** Frozen V2-10-04 execution plan for an EXPERIMENTAL abyssal-plain foundation profile. */
public record AbyssalPlainPlanV2(
        int planVersion,
        String featureId,
        String basinFeatureId,
        String basinGeometryChecksum,
        String basinRelationId,
        List<BathymetryRingsV2.Ring> rings,
        int selectedFloorDepthBlocksBelowSea,
        int selectedFloorReliefBlocks,
        int width,
        int length,
        int minY,
        int maxY,
        int waterLevel,
        String depthFieldId,
        String ownershipFieldId,
        String reliefFieldId,
        String fluidColumnHintFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.abyssal-plain";
    public static final String MODULE_VERSION = "0.1.0-v2-10-04";
    public static final String CONTRACT = "abyssal-plain-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 16_000_000L;
    public static final String DEPTH_FIELD_ID = "foundation.abyssal-plain.depth";
    public static final String OWNERSHIP_FIELD_ID = "foundation.abyssal-plain.ownership";
    public static final String RELIEF_FIELD_ID = "foundation.abyssal-plain.relief";
    public static final String FLUID_COLUMN_HINT_FIELD_ID = "foundation.abyssal-plain.fluid-column-hint";

    public AbyssalPlainPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("abyssal plain planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        basinFeatureId = FoundationValidationV2.slug(basinFeatureId, "basinFeatureId");
        basinGeometryChecksum = FoundationValidationV2.checksum(basinGeometryChecksum, "basinGeometryChecksum");
        basinRelationId = FoundationValidationV2.slug(basinRelationId, "basinRelationId");
        rings = FoundationValidationV2.immutable(rings, "rings", 64);
        if (rings.isEmpty()) {
            throw new IllegalArgumentException("abyssal plain requires at least one ring");
        }
        if (selectedFloorDepthBlocksBelowSea < 16 || selectedFloorDepthBlocksBelowSea > 256
                || selectedFloorReliefBlocks < 0 || selectedFloorReliefBlocks > 8) {
            throw new IllegalArgumentException("abyssal plain profile dimensions are invalid");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("abyssal plain bounds are invalid");
        }
        long maxX = Math.addExact(
                Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        long maxZ = Math.addExact(
                Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        for (BathymetryRingsV2.Ring ring : rings) {
            if (ring.vertices().size() < 4 || !ring.vertices().getFirst().equals(ring.vertices().getLast())) {
                throw new IllegalArgumentException("abyssal plain ring must be closed with at least three vertices");
            }
            for (BathymetryRingsV2.Vertex vertex : ring.vertices()) {
                if (vertex.xMillionths() < 0 || vertex.xMillionths() > maxX
                        || vertex.zMillionths() < 0 || vertex.zMillionths() > maxZ) {
                    throw new IllegalArgumentException("abyssal plain ring vertex is out of bounds");
                }
            }
        }
        depthFieldId = FoundationValidationV2.qualified(depthFieldId, "depthFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        reliefFieldId = FoundationValidationV2.qualified(reliefFieldId, "reliefFieldId");
        fluidColumnHintFieldId = FoundationValidationV2.qualified(
                fluidColumnHintFieldId, "fluidColumnHintFieldId");
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("abyssal plain support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public AbyssalPlainPlanV2 withCanonicalChecksum(String checksum) {
        return new AbyssalPlainPlanV2(
                planVersion, featureId, basinFeatureId, basinGeometryChecksum, basinRelationId,
                rings, selectedFloorDepthBlocksBelowSea, selectedFloorReliefBlocks,
                width, length, minY, maxY, waterLevel,
                depthFieldId, ownershipFieldId, reliefFieldId, fluidColumnHintFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }
}
