package com.github.nankotsu029.landformcraft.model.v2;

import java.util.List;
import java.util.Objects;

/** Frozen V2-4-10 execution plan for an EXPERIMENTAL coral reef regional bathymetry profile. */
public record CoralReefPlanV2(
        int planVersion,
        String featureId,
        List<Ring> rings,
        int minimumCrestDepthBlocks,
        int selectedCrestDepthBlocks,
        int maximumCrestDepthBlocks,
        int minimumReefWidthBlocks,
        int selectedReefWidthBlocks,
        int maximumReefWidthBlocks,
        int minimumOuterSlopeDegrees,
        int selectedOuterSlopeDegrees,
        int maximumOuterSlopeDegrees,
        LagoonPlanHook lagoonPlanHook,
        List<ReefPassPlanHook> passHooks,
        int minimumLagoonDepthBlocks,
        int selectedLagoonDepthBlocks,
        int maximumLagoonDepthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String reefMaskFieldId,
        String crestDepthFieldId,
        String lagoonDepthFieldId,
        String passCorridorFieldId,
        String marineConnectionFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum
) {
    public static final int VERSION = 1;
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    /** Carbonate reef substrate compact code for regional bathymetry descriptors. */
    public static final int SUBSTRATE_REEF_CARBONATE = 0;

    public CoralReefPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("coral reef planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        rings = V2Validation.immutable(rings, "rings", 64);
        if (rings.size() < 2) throw new IllegalArgumentException("coral reef requires outer ring and lagoon hole");
        validateRanges(minimumCrestDepthBlocks, selectedCrestDepthBlocks, maximumCrestDepthBlocks, 1, 4);
        validateRanges(minimumReefWidthBlocks, selectedReefWidthBlocks, maximumReefWidthBlocks, 18, 46);
        validateRanges(minimumOuterSlopeDegrees, selectedOuterSlopeDegrees, maximumOuterSlopeDegrees, 18, 42);
        Objects.requireNonNull(lagoonPlanHook, "lagoonPlanHook");
        passHooks = V2Validation.immutable(passHooks, "passHooks", 16);
        validateRanges(minimumLagoonDepthBlocks, selectedLagoonDepthBlocks, maximumLagoonDepthBlocks, 5, 14);
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("coral reef bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        for (Ring ring : rings) {
            if (ring.vertices().size() < 4 || !ring.vertices().getFirst().equals(ring.vertices().getLast())) {
                throw new IllegalArgumentException("coral reef ring must be closed with at least three vertices");
            }
            for (Vertex vertex : ring.vertices()) {
                if (vertex.xMillionths() < 0 || vertex.xMillionths() > maxX
                        || vertex.zMillionths() < 0 || vertex.zMillionths() > maxZ) {
                    throw new IllegalArgumentException("coral reef ring vertex is out of bounds");
                }
            }
        }
        for (ReefPassPlanHook pass : passHooks) {
            if (pass.centerline().size() < 2) {
                throw new IllegalArgumentException("reef pass centerline requires at least two points");
            }
        }
        reefMaskFieldId = V2Validation.qualifiedId(reefMaskFieldId, "reefMaskFieldId");
        crestDepthFieldId = V2Validation.qualifiedId(crestDepthFieldId, "crestDepthFieldId");
        lagoonDepthFieldId = V2Validation.qualifiedId(lagoonDepthFieldId, "lagoonDepthFieldId");
        passCorridorFieldId = V2Validation.qualifiedId(passCorridorFieldId, "passCorridorFieldId");
        marineConnectionFieldId = V2Validation.qualifiedId(marineConnectionFieldId, "marineConnectionFieldId");
        if (supportRadiusXZ < Math.max(selectedReefWidthBlocks, 1) || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("coral reef support or work budget is invalid");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
    }

    private static void validateRanges(int min, int selected, int max, int low, int high) {
        if (min < low || min > selected || selected > max || max > high) {
            throw new IllegalArgumentException("coral reef selected range is invalid");
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
                throw new IllegalArgumentException("coral reef vertex is invalid");
            }
        }
    }

    /** Required HARD ENCLOSED_BY lagoon→reef binding. */
    public record LagoonPlanHook(String lagoonFeatureId, String enclosedByRelationId) {
        public LagoonPlanHook {
            lagoonFeatureId = V2Validation.slug(lagoonFeatureId, "lagoonFeatureId");
            enclosedByRelationId = V2Validation.slug(enclosedByRelationId, "enclosedByRelationId");
        }
    }

    /** Stage 6 hook for one REEF_PASS with compiled centerline and selected corridor depth/width. */
    public record ReefPassPlanHook(
            String passFeatureId,
            String carvesThroughRelationId,
            String connectsToRelationId,
            List<CenterlinePoint> centerline,
            int minimumWidthBlocks,
            int selectedWidthBlocks,
            int maximumWidthBlocks,
            int minimumDepthBlocks,
            int selectedDepthBlocks,
            int maximumDepthBlocks
    ) {
        public ReefPassPlanHook {
            passFeatureId = V2Validation.slug(passFeatureId, "passFeatureId");
            carvesThroughRelationId = V2Validation.slug(carvesThroughRelationId, "carvesThroughRelationId");
            connectsToRelationId = V2Validation.slug(connectsToRelationId, "connectsToRelationId");
            centerline = V2Validation.immutable(centerline, "centerline", 4_096);
            validateRanges(minimumWidthBlocks, selectedWidthBlocks, maximumWidthBlocks, 10, 14);
            validateRanges(minimumDepthBlocks, selectedDepthBlocks, maximumDepthBlocks, 4, 7);
        }

        private static void validateRanges(int min, int selected, int max, int low, int high) {
            if (min < low || min > selected || selected > max || max > high) {
                throw new IllegalArgumentException("reef pass selected range is invalid");
            }
        }
    }

    public record CenterlinePoint(long xMillionths, long zMillionths, long arcLengthMillionths) {
        public CenterlinePoint {
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0) {
                throw new IllegalArgumentException("reef pass centerline point is invalid");
            }
        }
    }
}
