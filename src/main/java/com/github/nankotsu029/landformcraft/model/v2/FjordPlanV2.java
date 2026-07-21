package com.github.nankotsu029.landformcraft.model.v2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Frozen V2-3-09 execution plan for an EXPERIMENTAL 2.5D fjord. */
public record FjordPlanV2(
        int planVersion, String featureId, String emptiesIntoRelationId,
        TerrainIntentV2.Edge receivingSeaBoundary, TerrainIntentV2.FjordCrossSection crossSection,
        GlacialWallPlanHook glacialWallPlanHook, List<CenterlinePoint> centerline,
        int minimumSurfaceWidthBlocks, int maximumSurfaceWidthBlocks, int selectedSurfaceWidthBlocks,
        int minimumChannelDepthBlocks, int maximumChannelDepthBlocks, int selectedChannelDepthBlocks,
        int headBasinRadiusBlocks, int selectedSidewallReliefBlocks,
        int minY, int maxY, int waterLevel, int width, int length,
        String channelMaskFieldId, String floorMaskFieldId, String sidewallMaskFieldId,
        String thalwegDepthFieldId, String sidewallReliefFieldId,
        int supportRadiusXZ, long estimatedRasterWorkUnits, String geometryChecksum
) {
    public static final int VERSION = 1;
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;

    public FjordPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("fjord planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        emptiesIntoRelationId = V2Validation.slug(emptiesIntoRelationId, "emptiesIntoRelationId");
        Objects.requireNonNull(receivingSeaBoundary, "receivingSeaBoundary");
        if (crossSection != TerrainIntentV2.FjordCrossSection.GLACIAL_U) {
            throw new IllegalArgumentException("fjord cross section must be GLACIAL_U");
        }
        centerline = V2Validation.sorted(centerline, "centerline", 16_384,
                Comparator.comparingLong(CenterlinePoint::arcLengthMillionths));
        if (centerline.size() < 2) throw new IllegalArgumentException("fjord needs a centerline");
        validateRanges(minimumSurfaceWidthBlocks, selectedSurfaceWidthBlocks, maximumSurfaceWidthBlocks, 16, 128);
        validateRanges(minimumChannelDepthBlocks, selectedChannelDepthBlocks, maximumChannelDepthBlocks, 8, 64);
        if (headBasinRadiusBlocks < 8 || headBasinRadiusBlocks > 128
                || selectedSidewallReliefBlocks < 8 || selectedSidewallReliefBlocks > 160) {
            throw new IllegalArgumentException("fjord profile dimensions are invalid");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) throw new IllegalArgumentException("fjord bounds are invalid");
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        long previous = -1L;
        for (CenterlinePoint point : centerline) {
            if (point.arcLengthMillionths() <= previous || point.xMillionths() < 0 || point.xMillionths() > maxX
                    || point.zMillionths() < 0 || point.zMillionths() > maxZ) {
                throw new IllegalArgumentException("fjord centerline is invalid");
            }
            previous = point.arcLengthMillionths();
        }
        if (!onBoundary(centerline.getFirst(), receivingSeaBoundary, width, length)) {
            throw new IllegalArgumentException("fjord mouth must be first centerline point on sea boundary");
        }
        long totalLength = centerline.getLast().arcLengthMillionths();
        long ratio = totalLength / Math.multiplyExact((long) selectedSurfaceWidthBlocks, TerrainIntentV2.FIXED_SCALE);
        if (ratio < 5L || ratio > 14L) throw new IllegalArgumentException("fjord slenderness is invalid");
        channelMaskFieldId = V2Validation.qualifiedId(channelMaskFieldId, "channelMaskFieldId");
        floorMaskFieldId = V2Validation.qualifiedId(floorMaskFieldId, "floorMaskFieldId");
        sidewallMaskFieldId = V2Validation.qualifiedId(sidewallMaskFieldId, "sidewallMaskFieldId");
        thalwegDepthFieldId = V2Validation.qualifiedId(thalwegDepthFieldId, "thalwegDepthFieldId");
        sidewallReliefFieldId = V2Validation.qualifiedId(sidewallReliefFieldId, "sidewallReliefFieldId");
        if (supportRadiusXZ < (selectedSurfaceWidthBlocks + 1) / 2 || supportRadiusXZ > 256
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("fjord support or work budget is invalid");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
    }

    public static boolean onBoundary(CenterlinePoint point, TerrainIntentV2.Edge edge, int width, int length) {
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        return switch (edge) {
            case NORTH -> point.zMillionths() == 0L;
            case EAST -> point.xMillionths() == maxX;
            case SOUTH -> point.zMillionths() == maxZ;
            case WEST -> point.xMillionths() == 0L;
        };
    }

    private static void validateRanges(int min, int selected, int max, int low, int high) {
        if (min < low || min > selected || selected > max || max > high) {
            throw new IllegalArgumentException("fjord selected range is invalid");
        }
    }

    public record CenterlinePoint(long xMillionths, long zMillionths, long arcLengthMillionths) {
        public CenterlinePoint {
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0) {
                throw new IllegalArgumentException("fjord centerline point is invalid");
            }
        }
    }

    /** Hook only; mountain shaping is intentionally deferred to V2-3-10. */
    public record GlacialWallPlanHook(String wallFeatureId, String flanksRelationId) {
        public GlacialWallPlanHook {
            wallFeatureId = V2Validation.slug(wallFeatureId, "wallFeatureId");
            flanksRelationId = V2Validation.slug(flanksRelationId, "flanksRelationId");
        }
    }
}
