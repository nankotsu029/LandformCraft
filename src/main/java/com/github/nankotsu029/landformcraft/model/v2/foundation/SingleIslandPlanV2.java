package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;

/** Frozen V2-9-07 execution plan for an EXPERIMENTAL single-island foundation profile. */
public record SingleIslandPlanV2(
        int planVersion,
        String featureId,
        long centerXMillionths,
        long centerZMillionths,
        int selectedRadiusBlocks,
        int selectedSummitHeightBlocksAboveSea,
        int selectedShoreBandWidthBlocks,
        long selectedRadialDrainageMillionths,
        int selectedSubmarineApronDepthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String islandMaskFieldId,
        String shoreFieldId,
        String drainageFieldId,
        String apronFieldId,
        String solidOwnershipFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.single-island";
    public static final String MODULE_VERSION = "0.1.0-v2-9-07";
    public static final String CONTRACT = "single-island-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final String ISLAND_MASK_FIELD_ID = "foundation.island.mask";
    public static final String SHORE_FIELD_ID = "foundation.island.shore";
    public static final String DRAINAGE_FIELD_ID = "foundation.island.drainage";
    public static final String APRON_FIELD_ID = "foundation.island.apron";
    public static final String SOLID_OWNERSHIP_FIELD_ID = "foundation.island.solid-ownership";

    public SingleIslandPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("single island planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        if (selectedRadiusBlocks < 8 || selectedRadiusBlocks > 256
                || selectedSummitHeightBlocksAboveSea < 8 || selectedSummitHeightBlocksAboveSea > 256
                || selectedShoreBandWidthBlocks < 1 || selectedShoreBandWidthBlocks > 32
                || selectedRadialDrainageMillionths < 0
                || selectedRadialDrainageMillionths > TerrainIntentV2.FIXED_SCALE
                || selectedSubmarineApronDepthBlocks < 4 || selectedSubmarineApronDepthBlocks > 64) {
            throw new IllegalArgumentException("single island profile dimensions are invalid");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("single island bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        if (centerXMillionths < 0 || centerXMillionths > maxX
                || centerZMillionths < 0 || centerZMillionths > maxZ) {
            throw new IllegalArgumentException("single island center is out of bounds");
        }
        islandMaskFieldId = FoundationValidationV2.qualified(islandMaskFieldId, "islandMaskFieldId");
        shoreFieldId = FoundationValidationV2.qualified(shoreFieldId, "shoreFieldId");
        drainageFieldId = FoundationValidationV2.qualified(drainageFieldId, "drainageFieldId");
        apronFieldId = FoundationValidationV2.qualified(apronFieldId, "apronFieldId");
        solidOwnershipFieldId = FoundationValidationV2.qualified(solidOwnershipFieldId, "solidOwnershipFieldId");
        int outer = selectedRadiusBlocks + selectedSubmarineApronDepthBlocks;
        if (supportRadiusXZ < outer || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("single island support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public SingleIslandPlanV2 withCanonicalChecksum(String checksum) {
        return new SingleIslandPlanV2(
                planVersion, featureId, centerXMillionths, centerZMillionths,
                selectedRadiusBlocks, selectedSummitHeightBlocksAboveSea, selectedShoreBandWidthBlocks,
                selectedRadialDrainageMillionths, selectedSubmarineApronDepthBlocks,
                minY, maxY, waterLevel, width, length,
                islandMaskFieldId, shoreFieldId, drainageFieldId, apronFieldId, solidOwnershipFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }
}
