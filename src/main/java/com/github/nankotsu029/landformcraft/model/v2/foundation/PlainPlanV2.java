package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Frozen V2-9-02 execution plan for an EXPERIMENTAL plain foundation regional profile. */
public record PlainPlanV2(
        int planVersion,
        String featureId,
        List<Ring> rings,
        int baseElevationBlocks,
        int minimumMicroReliefBlocks,
        int selectedMicroReliefBlocks,
        int maximumMicroReliefBlocks,
        int groundwaterHandoffDepthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String plainMaskFieldId,
        String baseElevationFieldId,
        String microReliefFieldId,
        String groundwaterHandoffFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.plain";
    public static final String MODULE_VERSION = "0.1.0-v2-9-02";
    public static final String CONTRACT = "plain-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String PLAIN_MASK_FIELD_ID = "foundation.plain.mask";
    public static final String BASE_ELEVATION_FIELD_ID = "foundation.plain.base-elevation";
    public static final String MICRO_RELIEF_FIELD_ID = "foundation.plain.micro-relief";
    public static final String GROUNDWATER_HANDOFF_FIELD_ID = "foundation.plain.groundwater-handoff";

    public PlainPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("plain planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        rings = FoundationValidationV2.immutable(rings, "rings", 64);
        if (rings.isEmpty()) {
            throw new IllegalArgumentException("plain requires at least one ring");
        }
        validateMicroReliefRange(minimumMicroReliefBlocks, selectedMicroReliefBlocks, maximumMicroReliefBlocks);
        if (baseElevationBlocks < 0 || baseElevationBlocks > 256) {
            throw new IllegalArgumentException("plain baseElevationBlocks is invalid");
        }
        if (groundwaterHandoffDepthBlocks < 1 || groundwaterHandoffDepthBlocks > 32) {
            throw new IllegalArgumentException("plain groundwaterHandoffDepthBlocks outside 1..32");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("plain bounds are invalid");
        }
        long maxX = Math.addExact(
                Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        long maxZ = Math.addExact(
                Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        for (Ring ring : rings) {
            if (ring.vertices().size() < 4 || !ring.vertices().getFirst().equals(ring.vertices().getLast())) {
                throw new IllegalArgumentException("plain ring must be closed with at least three vertices");
            }
            for (Vertex vertex : ring.vertices()) {
                if (vertex.xMillionths() < 0 || vertex.xMillionths() > maxX
                        || vertex.zMillionths() < 0 || vertex.zMillionths() > maxZ) {
                    throw new IllegalArgumentException("plain ring vertex is out of bounds");
                }
            }
        }
        plainMaskFieldId = FoundationValidationV2.qualified(plainMaskFieldId, "plainMaskFieldId");
        baseElevationFieldId = FoundationValidationV2.qualified(baseElevationFieldId, "baseElevationFieldId");
        microReliefFieldId = FoundationValidationV2.qualified(microReliefFieldId, "microReliefFieldId");
        groundwaterHandoffFieldId = FoundationValidationV2.qualified(
                groundwaterHandoffFieldId, "groundwaterHandoffFieldId");
        if (supportRadiusXZ < selectedMicroReliefBlocks || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("plain support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public PlainPlanV2 withCanonicalChecksum(String checksum) {
        return new PlainPlanV2(
                planVersion, featureId, rings, baseElevationBlocks,
                minimumMicroReliefBlocks, selectedMicroReliefBlocks, maximumMicroReliefBlocks,
                groundwaterHandoffDepthBlocks, minY, maxY, waterLevel, width, length,
                plainMaskFieldId, baseElevationFieldId, microReliefFieldId, groundwaterHandoffFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }

    private static void validateMicroReliefRange(int min, int selected, int max) {
        if (min < 1 || min > selected || selected > max || max > 8) {
            throw new IllegalArgumentException("plain microRelief range is invalid");
        }
    }

    public record Ring(List<Vertex> vertices) {
        public Ring {
            vertices = FoundationValidationV2.immutable(vertices, "vertices", 2_048);
        }
    }

    public record Vertex(long xMillionths, long zMillionths) {
        public Vertex {
            if (xMillionths < 0 || zMillionths < 0) {
                throw new IllegalArgumentException("plain vertex is invalid");
            }
        }
    }
}
