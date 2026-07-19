package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Frozen V2-9-05 execution plan for an EXPERIMENTAL marsh foundation profile. */
public record MarshPlanV2(
        int planVersion,
        String featureId,
        List<Ring> rings,
        int selectedHydroperiodBlocks,
        long selectedWetnessMillionths,
        long selectedOpenWaterShareMillionths,
        int selectedMicroReliefBlocks,
        int groundwaterMinDepthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String marshMaskFieldId,
        String openWaterFieldId,
        String microReliefFieldId,
        String wetnessFieldId,
        String hydroperiodFieldId,
        String fluidOwnershipFieldId,
        String solidOwnershipFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.marsh";
    public static final String MODULE_VERSION = "0.1.0-v2-9-05";
    public static final String CONTRACT = "marsh-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String MARSH_MASK_FIELD_ID = "foundation.marsh.mask";
    public static final String OPEN_WATER_FIELD_ID = "foundation.marsh.open-water";
    public static final String MICRO_RELIEF_FIELD_ID = "foundation.marsh.micro-relief";
    public static final String WETNESS_FIELD_ID = "foundation.marsh.wetness";
    public static final String HYDROPERIOD_FIELD_ID = "foundation.marsh.hydroperiod";
    public static final String FLUID_OWNERSHIP_FIELD_ID = "foundation.marsh.fluid-ownership";
    public static final String SOLID_OWNERSHIP_FIELD_ID = "foundation.marsh.solid-ownership";

    public MarshPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("marsh planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        rings = FoundationValidationV2.immutable(rings, "rings", 64);
        if (rings.isEmpty()) {
            throw new IllegalArgumentException("marsh requires at least one ring");
        }
        if (selectedHydroperiodBlocks < 1 || selectedHydroperiodBlocks > 64
                || selectedWetnessMillionths < 200_000L
                || selectedWetnessMillionths > TerrainIntentV2.FIXED_SCALE
                || selectedOpenWaterShareMillionths < 0L
                || selectedOpenWaterShareMillionths > 700_000L
                || selectedMicroReliefBlocks < 1 || selectedMicroReliefBlocks > 8
                || groundwaterMinDepthBlocks < 1 || groundwaterMinDepthBlocks > 32) {
            throw new IllegalArgumentException("marsh profile dimensions are invalid");
        }
        if (groundwaterMinDepthBlocks > selectedHydroperiodBlocks + 16) {
            throw new IllegalArgumentException("marsh groundwater/hydroperiod conflict");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("marsh bounds are invalid");
        }
        long maxX = Math.addExact(
                Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        long maxZ = Math.addExact(
                Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        for (Ring ring : rings) {
            if (ring.vertices().size() < 4 || !ring.vertices().getFirst().equals(ring.vertices().getLast())) {
                throw new IllegalArgumentException("marsh ring must be closed with at least three vertices");
            }
            for (Vertex vertex : ring.vertices()) {
                if (vertex.xMillionths() < 0 || vertex.xMillionths() > maxX
                        || vertex.zMillionths() < 0 || vertex.zMillionths() > maxZ) {
                    throw new IllegalArgumentException("marsh ring vertex is out of bounds");
                }
            }
        }
        marshMaskFieldId = FoundationValidationV2.qualified(marshMaskFieldId, "marshMaskFieldId");
        openWaterFieldId = FoundationValidationV2.qualified(openWaterFieldId, "openWaterFieldId");
        microReliefFieldId = FoundationValidationV2.qualified(microReliefFieldId, "microReliefFieldId");
        wetnessFieldId = FoundationValidationV2.qualified(wetnessFieldId, "wetnessFieldId");
        hydroperiodFieldId = FoundationValidationV2.qualified(hydroperiodFieldId, "hydroperiodFieldId");
        fluidOwnershipFieldId = FoundationValidationV2.qualified(fluidOwnershipFieldId, "fluidOwnershipFieldId");
        solidOwnershipFieldId = FoundationValidationV2.qualified(solidOwnershipFieldId, "solidOwnershipFieldId");
        if (supportRadiusXZ < selectedMicroReliefBlocks || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("marsh support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public MarshPlanV2 withCanonicalChecksum(String checksum) {
        return new MarshPlanV2(
                planVersion, featureId, rings, selectedHydroperiodBlocks, selectedWetnessMillionths,
                selectedOpenWaterShareMillionths, selectedMicroReliefBlocks, groundwaterMinDepthBlocks,
                minY, maxY, waterLevel, width, length,
                marshMaskFieldId, openWaterFieldId, microReliefFieldId, wetnessFieldId, hydroperiodFieldId,
                fluidOwnershipFieldId, solidOwnershipFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }

    public record Ring(List<Vertex> vertices) {
        public Ring {
            vertices = FoundationValidationV2.immutable(vertices, "vertices", 2_048);
        }
    }

    public record Vertex(long xMillionths, long zMillionths) {
        public Vertex {
            if (xMillionths < 0 || zMillionths < 0) {
                throw new IllegalArgumentException("marsh vertex is invalid");
            }
        }
    }
}
