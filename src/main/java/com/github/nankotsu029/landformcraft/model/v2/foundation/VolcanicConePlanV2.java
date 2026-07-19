package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

/** Frozen V2-9-07 execution plan for an EXPERIMENTAL volcanic-cone foundation profile. */
public record VolcanicConePlanV2(
        int planVersion,
        String featureId,
        long centerXMillionths,
        long centerZMillionths,
        int selectedBaseRadiusBlocks,
        int selectedSummitHeightBlocksAboveSea,
        int selectedCraterRadiusBlocks,
        int selectedCraterFloorDepthBlocks,
        long selectedRadialDrainageMillionths,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String coneMaskFieldId,
        String craterMaskFieldId,
        String drainageFieldId,
        String solidOwnershipFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.volcanic-cone";
    public static final String MODULE_VERSION = "0.1.0-v2-9-07";
    public static final String CONTRACT = "volcanic-cone-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String CONE_MASK_FIELD_ID = "foundation.cone.mask";
    public static final String CRATER_MASK_FIELD_ID = "foundation.cone.crater-mask";
    public static final String DRAINAGE_FIELD_ID = "foundation.cone.drainage";
    public static final String SOLID_OWNERSHIP_FIELD_ID = "foundation.cone.solid-ownership";

    public VolcanicConePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("volcanic cone planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        if (selectedBaseRadiusBlocks < 8 || selectedBaseRadiusBlocks > 256
                || selectedSummitHeightBlocksAboveSea < 8 || selectedSummitHeightBlocksAboveSea > 256
                || selectedCraterRadiusBlocks < 1 || selectedCraterRadiusBlocks > 64
                || selectedCraterFloorDepthBlocks < 1 || selectedCraterFloorDepthBlocks > 64
                || selectedRadialDrainageMillionths < 0
                || selectedRadialDrainageMillionths > TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("volcanic cone profile dimensions are invalid");
        }
        if (selectedCraterRadiusBlocks >= selectedBaseRadiusBlocks) {
            throw new IllegalArgumentException("crater must be strictly inside base radius");
        }
        if (selectedCraterFloorDepthBlocks > selectedSummitHeightBlocksAboveSea) {
            throw new IllegalArgumentException("crater floor depth exceeds summit height");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("volcanic cone bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        if (centerXMillionths < 0 || centerXMillionths > maxX
                || centerZMillionths < 0 || centerZMillionths > maxZ) {
            throw new IllegalArgumentException("volcanic cone center is out of bounds");
        }
        coneMaskFieldId = FoundationValidationV2.qualified(coneMaskFieldId, "coneMaskFieldId");
        craterMaskFieldId = FoundationValidationV2.qualified(craterMaskFieldId, "craterMaskFieldId");
        drainageFieldId = FoundationValidationV2.qualified(drainageFieldId, "drainageFieldId");
        solidOwnershipFieldId = FoundationValidationV2.qualified(solidOwnershipFieldId, "solidOwnershipFieldId");
        if (supportRadiusXZ < selectedBaseRadiusBlocks || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("volcanic cone support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public VolcanicConePlanV2 withCanonicalChecksum(String checksum) {
        return new VolcanicConePlanV2(
                planVersion, featureId, centerXMillionths, centerZMillionths,
                selectedBaseRadiusBlocks, selectedSummitHeightBlocksAboveSea,
                selectedCraterRadiusBlocks, selectedCraterFloorDepthBlocks,
                selectedRadialDrainageMillionths,
                minY, maxY, waterLevel, width, length,
                coneMaskFieldId, craterMaskFieldId, drainageFieldId, solidOwnershipFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }
}
