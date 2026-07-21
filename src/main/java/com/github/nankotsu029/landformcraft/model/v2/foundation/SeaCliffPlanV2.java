package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Frozen V2-9-06 execution plan for an EXPERIMENTAL sea-cliff foundation profile. */
public record SeaCliffPlanV2(
        int planVersion,
        String featureId,
        List<CenterlinePoint> centerline,
        int selectedCliffHeightBlocks,
        int selectedTalusWidthBlocks,
        int selectedNotchDepthBlocks,
        TerrainIntentV2.Edge seawardSide,
        int selectedSupportHalfExtentXZBlocks,
        int coastTransitionBandBlocks,
        VolumeSdfAabbV2 hostSupportAabb,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String cliffFaceMaskFieldId,
        String talusMaskFieldId,
        String notchMaskFieldId,
        String solidOwnershipFieldId,
        String volumeHostOwnershipFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.sea-cliff";
    public static final String MODULE_VERSION = "0.1.0-v2-9-06";
    public static final String CONTRACT = "sea-cliff-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String CLIFF_FACE_MASK_FIELD_ID = "foundation.sea-cliff.face-mask";
    public static final String TALUS_MASK_FIELD_ID = "foundation.sea-cliff.talus-mask";
    public static final String NOTCH_MASK_FIELD_ID = "foundation.sea-cliff.notch-mask";
    public static final String SOLID_OWNERSHIP_FIELD_ID = "foundation.sea-cliff.solid-ownership";
    public static final String VOLUME_HOST_OWNERSHIP_FIELD_ID = "foundation.sea-cliff.volume-host-ownership";

    public SeaCliffPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("sea cliff planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        centerline = FoundationValidationV2.sorted(centerline, "centerline", 16_384,
                Comparator.comparingLong(CenterlinePoint::arcLengthMillionths));
        if (centerline.size() < 2) {
            throw new IllegalArgumentException("sea cliff needs at least two centerline points");
        }
        Objects.requireNonNull(seawardSide, "seawardSide");
        Objects.requireNonNull(hostSupportAabb, "hostSupportAabb");
        if (selectedCliffHeightBlocks < 1 || selectedCliffHeightBlocks > 128
                || selectedTalusWidthBlocks < 1 || selectedTalusWidthBlocks > 64
                || selectedNotchDepthBlocks < 1 || selectedNotchDepthBlocks > 32
                || selectedSupportHalfExtentXZBlocks < 1 || selectedSupportHalfExtentXZBlocks > 64
                || coastTransitionBandBlocks < 1 || coastTransitionBandBlocks > 32) {
            throw new IllegalArgumentException("sea cliff profile dimensions are invalid");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("sea cliff bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        long previousArc = -1L;
        for (CenterlinePoint point : centerline) {
            if (point.arcLengthMillionths() <= previousArc
                    || point.xMillionths() < 0 || point.xMillionths() > maxX
                    || point.zMillionths() < 0 || point.zMillionths() > maxZ) {
                throw new IllegalArgumentException("sea cliff centerline is invalid");
            }
            previousArc = point.arcLengthMillionths();
        }
        cliffFaceMaskFieldId = FoundationValidationV2.qualified(cliffFaceMaskFieldId, "cliffFaceMaskFieldId");
        talusMaskFieldId = FoundationValidationV2.qualified(talusMaskFieldId, "talusMaskFieldId");
        notchMaskFieldId = FoundationValidationV2.qualified(notchMaskFieldId, "notchMaskFieldId");
        solidOwnershipFieldId = FoundationValidationV2.qualified(solidOwnershipFieldId, "solidOwnershipFieldId");
        volumeHostOwnershipFieldId = FoundationValidationV2.qualified(
                volumeHostOwnershipFieldId, "volumeHostOwnershipFieldId");
        if (supportRadiusXZ < Math.max(selectedSupportHalfExtentXZBlocks, selectedTalusWidthBlocks)
                || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("sea cliff support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public SeaCliffPlanV2 withCanonicalChecksum(String checksum) {
        return new SeaCliffPlanV2(
                planVersion, featureId, centerline, selectedCliffHeightBlocks, selectedTalusWidthBlocks,
                selectedNotchDepthBlocks, seawardSide, selectedSupportHalfExtentXZBlocks,
                coastTransitionBandBlocks, hostSupportAabb,
                minY, maxY, waterLevel, width, length,
                cliffFaceMaskFieldId, talusMaskFieldId, notchMaskFieldId,
                solidOwnershipFieldId, volumeHostOwnershipFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits,                 geometryChecksum, checksum);
    }

    public record CenterlinePoint(long xMillionths, long zMillionths, long arcLengthMillionths) {
        public CenterlinePoint {
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0) {
                throw new IllegalArgumentException("sea cliff centerline point is invalid");
            }
        }
    }
}
