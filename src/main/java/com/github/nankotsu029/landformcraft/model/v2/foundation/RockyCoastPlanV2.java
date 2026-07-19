package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Frozen V2-9-06 execution plan for an EXPERIMENTAL rocky-coast foundation profile. */
public record RockyCoastPlanV2(
        int planVersion,
        String featureId,
        List<CenterlinePoint> centerline,
        int selectedRockShelfWidthBlocks,
        long selectedRockExposureMillionths,
        TerrainIntentV2.Edge shoreSide,
        int selectedChannelCount,
        int capeOrBeachTransitionBandBlocks,
        int talusHandoffDepthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String shelfMaskFieldId,
        String exposureFieldId,
        String channelMaskFieldId,
        String talusHandoffFieldId,
        String solidOwnershipFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.rocky-coast";
    public static final String MODULE_VERSION = "0.1.0-v2-9-06";
    public static final String CONTRACT = "rocky-coast-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String SHELF_MASK_FIELD_ID = "foundation.rocky-coast.shelf-mask";
    public static final String EXPOSURE_FIELD_ID = "foundation.rocky-coast.exposure";
    public static final String CHANNEL_MASK_FIELD_ID = "foundation.rocky-coast.channel-mask";
    public static final String TALUS_HANDOFF_FIELD_ID = "foundation.rocky-coast.talus-handoff";
    public static final String SOLID_OWNERSHIP_FIELD_ID = "foundation.rocky-coast.solid-ownership";

    public RockyCoastPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("rocky coast planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        centerline = FoundationValidationV2.sorted(centerline, "centerline", 16_384,
                Comparator.comparingLong(CenterlinePoint::arcLengthMillionths));
        if (centerline.size() < 2) {
            throw new IllegalArgumentException("rocky coast needs at least two centerline points");
        }
        Objects.requireNonNull(shoreSide, "shoreSide");
        if (selectedRockShelfWidthBlocks < 1 || selectedRockShelfWidthBlocks > 64
                || selectedRockExposureMillionths < 50_000L
                || selectedRockExposureMillionths > TerrainIntentV2.FIXED_SCALE
                || selectedChannelCount < 1 || selectedChannelCount > 8
                || capeOrBeachTransitionBandBlocks < 1 || capeOrBeachTransitionBandBlocks > 32
                || talusHandoffDepthBlocks < 1 || talusHandoffDepthBlocks > 32) {
            throw new IllegalArgumentException("rocky coast profile dimensions are invalid");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("rocky coast bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        long previousArc = -1L;
        for (CenterlinePoint point : centerline) {
            if (point.arcLengthMillionths() <= previousArc
                    || point.xMillionths() < 0 || point.xMillionths() > maxX
                    || point.zMillionths() < 0 || point.zMillionths() > maxZ) {
                throw new IllegalArgumentException("rocky coast centerline is invalid");
            }
            previousArc = point.arcLengthMillionths();
        }
        shelfMaskFieldId = FoundationValidationV2.qualified(shelfMaskFieldId, "shelfMaskFieldId");
        exposureFieldId = FoundationValidationV2.qualified(exposureFieldId, "exposureFieldId");
        channelMaskFieldId = FoundationValidationV2.qualified(channelMaskFieldId, "channelMaskFieldId");
        talusHandoffFieldId = FoundationValidationV2.qualified(talusHandoffFieldId, "talusHandoffFieldId");
        solidOwnershipFieldId = FoundationValidationV2.qualified(solidOwnershipFieldId, "solidOwnershipFieldId");
        if (supportRadiusXZ < selectedRockShelfWidthBlocks || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("rocky coast support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public RockyCoastPlanV2 withCanonicalChecksum(String checksum) {
        return new RockyCoastPlanV2(
                planVersion, featureId, centerline, selectedRockShelfWidthBlocks,
                selectedRockExposureMillionths, shoreSide, selectedChannelCount,
                capeOrBeachTransitionBandBlocks, talusHandoffDepthBlocks,
                minY, maxY, waterLevel, width, length,
                shelfMaskFieldId, exposureFieldId, channelMaskFieldId, talusHandoffFieldId,
                solidOwnershipFieldId, supportRadiusXZ, estimatedRasterWorkUnits,
                geometryChecksum, checksum);
    }

    public record CenterlinePoint(long xMillionths, long zMillionths, long arcLengthMillionths) {
        public CenterlinePoint {
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0) {
                throw new IllegalArgumentException("rocky coast centerline point is invalid");
            }
        }
    }
}
