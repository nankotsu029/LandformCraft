package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Frozen V2-10-06 execution plan for an EXPERIMENTAL escarpment foundation profile. */
public record EscarpmentPlanV2(
        int planVersion,
        String featureId,
        List<CenterlinePoint> centerline,
        int selectedScarpHeightBlocks,
        int selectedTalusWidthBlocks,
        int selectedFloorDropBlocks,
        TerrainIntentV2.Edge dropSide,
        int plateauTransitionBandBlocks,
        int minY,
        int maxY,
        int width,
        int length,
        String faceMaskFieldId,
        String talusMaskFieldId,
        String floorMaskFieldId,
        String ownershipFieldId,
        String materialHandoffFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.escarpment";
    public static final String MODULE_VERSION = "0.1.0-v2-10-06";
    public static final String CONTRACT = "escarpment-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 16_000_000L;
    public static final String FACE_MASK_FIELD_ID = "foundation.escarpment.face-mask";
    public static final String TALUS_MASK_FIELD_ID = "foundation.escarpment.talus-mask";
    public static final String FLOOR_MASK_FIELD_ID = "foundation.escarpment.floor-mask";
    public static final String OWNERSHIP_FIELD_ID = "foundation.escarpment.ownership";
    public static final String MATERIAL_HANDOFF_FIELD_ID = "foundation.escarpment.material-handoff";
    public static final String TALUS_HANDOFF_ID = "escarpment-talus-handoff";

    public EscarpmentPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("escarpment planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        centerline = FoundationValidationV2.sorted(centerline, "centerline", 16_384,
                Comparator.comparingLong(CenterlinePoint::arcLengthMillionths));
        if (centerline.size() < 2) {
            throw new IllegalArgumentException("escarpment needs at least two centerline points");
        }
        Objects.requireNonNull(dropSide, "dropSide");
        if (selectedScarpHeightBlocks < 4 || selectedScarpHeightBlocks > 128
                || selectedTalusWidthBlocks < 2 || selectedTalusWidthBlocks > 64
                || selectedFloorDropBlocks < 4 || selectedFloorDropBlocks > 128
                || plateauTransitionBandBlocks < 2 || plateauTransitionBandBlocks > 32) {
            throw new IllegalArgumentException("escarpment profile dimensions are invalid");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY) {
            throw new IllegalArgumentException("escarpment bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        long previousArc = -1L;
        for (CenterlinePoint point : centerline) {
            if (point.arcLengthMillionths() <= previousArc
                    || point.xMillionths() < 0 || point.xMillionths() > maxX
                    || point.zMillionths() < 0 || point.zMillionths() > maxZ) {
                throw new IllegalArgumentException("escarpment centerline is invalid");
            }
            previousArc = point.arcLengthMillionths();
        }
        faceMaskFieldId = FoundationValidationV2.qualified(faceMaskFieldId, "faceMaskFieldId");
        talusMaskFieldId = FoundationValidationV2.qualified(talusMaskFieldId, "talusMaskFieldId");
        floorMaskFieldId = FoundationValidationV2.qualified(floorMaskFieldId, "floorMaskFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        materialHandoffFieldId = FoundationValidationV2.qualified(materialHandoffFieldId, "materialHandoffFieldId");
        if (supportRadiusXZ < Math.max(selectedTalusWidthBlocks, 4) || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("escarpment support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public EscarpmentPlanV2 withCanonicalChecksum(String checksum) {
        return new EscarpmentPlanV2(
                planVersion, featureId, centerline, selectedScarpHeightBlocks, selectedTalusWidthBlocks,
                selectedFloorDropBlocks, dropSide, plateauTransitionBandBlocks,
                minY, maxY, width, length,
                faceMaskFieldId, talusMaskFieldId, floorMaskFieldId, ownershipFieldId, materialHandoffFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }

    public record CenterlinePoint(long xMillionths, long zMillionths, long arcLengthMillionths) {
        public CenterlinePoint {
            if (xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0) {
                throw new IllegalArgumentException("escarpment centerline point is invalid");
            }
        }
    }
}
