package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Frozen V2-9-05 execution plan for an EXPERIMENTAL floodplain foundation profile. */
public record FloodplainPlanV2(
        int planVersion,
        String featureId,
        List<Ring> rings,
        int riverAdjacencyBandBlocks,
        int groundwaterHandoffDepthBlocks,
        int selectedMicroReliefBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String floodplainMaskFieldId,
        String elevationFieldId,
        String microReliefFieldId,
        String groundwaterHandoffFieldId,
        String solidOwnershipFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.floodplain";
    public static final String MODULE_VERSION = "0.1.0-v2-9-05";
    public static final String CONTRACT = "floodplain-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String FLOODPLAIN_MASK_FIELD_ID = "foundation.floodplain.mask";
    public static final String ELEVATION_FIELD_ID = "foundation.floodplain.elevation";
    public static final String MICRO_RELIEF_FIELD_ID = "foundation.floodplain.micro-relief";
    public static final String GROUNDWATER_HANDOFF_FIELD_ID = "foundation.floodplain.groundwater-handoff";
    public static final String SOLID_OWNERSHIP_FIELD_ID = "foundation.floodplain.solid-ownership";

    public FloodplainPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("floodplain planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        rings = FoundationValidationV2.immutable(rings, "rings", 64);
        if (rings.isEmpty()) {
            throw new IllegalArgumentException("floodplain requires at least one ring");
        }
        if (riverAdjacencyBandBlocks < 1 || riverAdjacencyBandBlocks > 32
                || groundwaterHandoffDepthBlocks < 1 || groundwaterHandoffDepthBlocks > 32
                || selectedMicroReliefBlocks < 1 || selectedMicroReliefBlocks > 8) {
            throw new IllegalArgumentException("floodplain profile dimensions are invalid");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("floodplain bounds are invalid");
        }
        long maxX = Math.addExact(
                Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        long maxZ = Math.addExact(
                Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        for (Ring ring : rings) {
            if (ring.vertices().size() < 4 || !ring.vertices().getFirst().equals(ring.vertices().getLast())) {
                throw new IllegalArgumentException("floodplain ring must be closed with at least three vertices");
            }
            for (Vertex vertex : ring.vertices()) {
                if (vertex.xMillionths() < 0 || vertex.xMillionths() > maxX
                        || vertex.zMillionths() < 0 || vertex.zMillionths() > maxZ) {
                    throw new IllegalArgumentException("floodplain ring vertex is out of bounds");
                }
            }
        }
        floodplainMaskFieldId = FoundationValidationV2.qualified(floodplainMaskFieldId, "floodplainMaskFieldId");
        elevationFieldId = FoundationValidationV2.qualified(elevationFieldId, "elevationFieldId");
        microReliefFieldId = FoundationValidationV2.qualified(microReliefFieldId, "microReliefFieldId");
        groundwaterHandoffFieldId = FoundationValidationV2.qualified(
                groundwaterHandoffFieldId, "groundwaterHandoffFieldId");
        solidOwnershipFieldId = FoundationValidationV2.qualified(solidOwnershipFieldId, "solidOwnershipFieldId");
        if (supportRadiusXZ < riverAdjacencyBandBlocks || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("floodplain support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FloodplainPlanV2 withCanonicalChecksum(String checksum) {
        return new FloodplainPlanV2(
                planVersion, featureId, rings, riverAdjacencyBandBlocks, groundwaterHandoffDepthBlocks,
                selectedMicroReliefBlocks, minY, maxY, waterLevel, width, length,
                floodplainMaskFieldId, elevationFieldId, microReliefFieldId, groundwaterHandoffFieldId,
                solidOwnershipFieldId, supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }

    public record Ring(List<Vertex> vertices) {
        public Ring {
            vertices = FoundationValidationV2.immutable(vertices, "vertices", 2_048);
        }
    }

    public record Vertex(long xMillionths, long zMillionths) {
        public Vertex {
            if (xMillionths < 0 || zMillionths < 0) {
                throw new IllegalArgumentException("floodplain vertex is invalid");
            }
        }
    }
}
