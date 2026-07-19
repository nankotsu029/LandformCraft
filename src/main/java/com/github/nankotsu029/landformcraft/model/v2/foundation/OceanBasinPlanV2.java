package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Frozen V2-9-08 execution plan for an EXPERIMENTAL ocean-basin foundation profile. */
public record OceanBasinPlanV2(
        int planVersion,
        String featureId,
        List<BathymetryRingsV2.Ring> rings,
        int selectedMaxDepthBlocksBelowSea,
        int selectedFloorReliefBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String depthFieldId,
        String slopeFieldId,
        String coastDistanceFieldId,
        String ownershipFieldId,
        String fluidColumnHintFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.ocean-basin";
    public static final String MODULE_VERSION = "0.1.0-v2-9-08";
    public static final String CONTRACT = "ocean-basin-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String DEPTH_FIELD_ID = "foundation.ocean-basin.depth";
    public static final String SLOPE_FIELD_ID = "foundation.ocean-basin.slope";
    public static final String COAST_DISTANCE_FIELD_ID = "foundation.ocean-basin.coast-distance";
    public static final String OWNERSHIP_FIELD_ID = "foundation.ocean-basin.ownership";
    public static final String FLUID_COLUMN_HINT_FIELD_ID = "foundation.ocean-basin.fluid-column-hint";

    public OceanBasinPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("ocean basin planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        rings = FoundationValidationV2.immutable(rings, "rings", 64);
        if (rings.isEmpty()) {
            throw new IllegalArgumentException("ocean basin requires at least one ring");
        }
        if (selectedMaxDepthBlocksBelowSea < 16 || selectedMaxDepthBlocksBelowSea > 256
                || selectedFloorReliefBlocks < 1 || selectedFloorReliefBlocks > 16) {
            throw new IllegalArgumentException("ocean basin profile dimensions are invalid");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("ocean basin bounds are invalid");
        }
        long maxX = Math.addExact(
                Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        long maxZ = Math.addExact(
                Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        for (BathymetryRingsV2.Ring ring : rings) {
            if (ring.vertices().size() < 4 || !ring.vertices().getFirst().equals(ring.vertices().getLast())) {
                throw new IllegalArgumentException("ocean basin ring must be closed with at least three vertices");
            }
            for (BathymetryRingsV2.Vertex vertex : ring.vertices()) {
                if (vertex.xMillionths() < 0 || vertex.xMillionths() > maxX
                        || vertex.zMillionths() < 0 || vertex.zMillionths() > maxZ) {
                    throw new IllegalArgumentException("ocean basin ring vertex is out of bounds");
                }
            }
        }
        depthFieldId = FoundationValidationV2.qualified(depthFieldId, "depthFieldId");
        slopeFieldId = FoundationValidationV2.qualified(slopeFieldId, "slopeFieldId");
        coastDistanceFieldId = FoundationValidationV2.qualified(coastDistanceFieldId, "coastDistanceFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        fluidColumnHintFieldId = FoundationValidationV2.qualified(
                fluidColumnHintFieldId, "fluidColumnHintFieldId");
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("ocean basin support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public OceanBasinPlanV2 withCanonicalChecksum(String checksum) {
        return new OceanBasinPlanV2(
                planVersion, featureId, rings, selectedMaxDepthBlocksBelowSea, selectedFloorReliefBlocks,
                minY, maxY, waterLevel, width, length,
                depthFieldId, slopeFieldId, coastDistanceFieldId, ownershipFieldId, fluidColumnHintFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }
}
