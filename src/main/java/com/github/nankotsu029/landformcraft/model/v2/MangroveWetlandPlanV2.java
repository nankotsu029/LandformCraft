package com.github.nankotsu029.landformcraft.model.v2;

import java.util.List;
import java.util.Objects;

/** Frozen V2-4-09 execution plan for an EXPERIMENTAL mangrove wetland regional profile. */
public record MangroveWetlandPlanV2(
        int planVersion,
        String featureId,
        List<Ring> rings,
        TidalNetworkPlanHook tidalNetworkHook,
        int minimumMicroReliefBlocks,
        int selectedMicroReliefBlocks,
        int maximumMicroReliefBlocks,
        long minimumWaterloggedShareMillionths,
        long selectedWaterloggedShareMillionths,
        long maximumWaterloggedShareMillionths,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String wetlandMaskFieldId,
        String surfaceHeightFieldId,
        String openWaterGapFieldId,
        String substrateClassFieldId,
        String microReliefFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum
) {
    public static final int VERSION = 1;
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    /** Wet mud compact code aligned with MaterialProfilePlanV2.SEDIMENT_WET (literal to avoid package cycles). */
    public static final int SUBSTRATE_SEDIMENT_WET = 4;

    public MangroveWetlandPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("mangrove planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        rings = V2Validation.immutable(rings, "rings", 64);
        if (rings.isEmpty()) throw new IllegalArgumentException("mangrove requires at least one ring");
        validateRanges(minimumMicroReliefBlocks, selectedMicroReliefBlocks, maximumMicroReliefBlocks, 1, 4);
        validateShareRange(minimumWaterloggedShareMillionths, selectedWaterloggedShareMillionths,
                maximumWaterloggedShareMillionths);
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("mangrove bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        for (Ring ring : rings) {
            if (ring.vertices().size() < 4 || !ring.vertices().getFirst().equals(ring.vertices().getLast())) {
                throw new IllegalArgumentException("mangrove ring must be closed with at least three vertices");
            }
            for (Vertex vertex : ring.vertices()) {
                if (vertex.xMillionths() < 0 || vertex.xMillionths() > maxX
                        || vertex.zMillionths() < 0 || vertex.zMillionths() > maxZ) {
                    throw new IllegalArgumentException("mangrove ring vertex is out of bounds");
                }
            }
        }
        wetlandMaskFieldId = V2Validation.qualifiedId(wetlandMaskFieldId, "wetlandMaskFieldId");
        surfaceHeightFieldId = V2Validation.qualifiedId(surfaceHeightFieldId, "surfaceHeightFieldId");
        openWaterGapFieldId = V2Validation.qualifiedId(openWaterGapFieldId, "openWaterGapFieldId");
        substrateClassFieldId = V2Validation.qualifiedId(substrateClassFieldId, "substrateClassFieldId");
        microReliefFieldId = V2Validation.qualifiedId(microReliefFieldId, "microReliefFieldId");
        if (supportRadiusXZ < selectedMicroReliefBlocks || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("mangrove support or work budget is invalid");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
    }

    private static void validateRanges(int min, int selected, int max, int low, int high) {
        if (min < low || min > selected || selected > max || max > high) {
            throw new IllegalArgumentException("mangrove selected range is invalid");
        }
    }

    private static void validateShareRange(long min, long selected, long max) {
        if (min < 100_000L || min > selected || selected > max || max > 900_000L) {
            throw new IllegalArgumentException("mangrove waterloggedShare01 outside 0.1..0.9");
        }
    }

    public record Ring(List<Vertex> vertices) {
        public Ring {
            vertices = V2Validation.immutable(vertices, "vertices", 2_048);
        }
    }

    public record Vertex(long xMillionths, long zMillionths) {
        public Vertex {
            if (xMillionths < 0 || zMillionths < 0) {
                throw new IllegalArgumentException("mangrove vertex is invalid");
            }
        }
    }

    /** Stage 6 hook to a parent TIDAL_CHANNEL_NETWORK plan. Shaping only stores the binding here. */
    public record TidalNetworkPlanHook(String tidalFeatureId, String withinRelationId) {
        public TidalNetworkPlanHook {
            tidalFeatureId = V2Validation.slug(tidalFeatureId, "tidalFeatureId");
            withinRelationId = V2Validation.slug(withinRelationId, "withinRelationId");
        }
    }
}
