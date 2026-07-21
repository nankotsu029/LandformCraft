package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;

/** Frozen V2-10-04 execution plan for an EXPERIMENTAL seamount foundation profile. */
public record SeamountPlanV2(
        int planVersion,
        String featureId,
        String basinFeatureId,
        String basinGeometryChecksum,
        String basinRelationId,
        long centerXMillionths,
        long centerZMillionths,
        int selectedBaseRadiusBlocks,
        int selectedReliefBlocks,
        int selectedSummitDepthBlocksBelowSea,
        int width,
        int length,
        int minY,
        int maxY,
        int waterLevel,
        String reliefFieldId,
        String ownershipFieldId,
        String slopeFieldId,
        String fluidColumnHintFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.seamount";
    public static final String MODULE_VERSION = "0.1.0-v2-10-04";
    public static final String CONTRACT = "seamount-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 16_000_000L;
    public static final String RELIEF_FIELD_ID = "foundation.seamount.relief";
    public static final String OWNERSHIP_FIELD_ID = "foundation.seamount.ownership";
    public static final String SLOPE_FIELD_ID = "foundation.seamount.slope";
    public static final String FLUID_COLUMN_HINT_FIELD_ID = "foundation.seamount.fluid-column-hint";

    public SeamountPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("seamount planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        basinFeatureId = FoundationValidationV2.slug(basinFeatureId, "basinFeatureId");
        basinGeometryChecksum = FoundationValidationV2.checksum(basinGeometryChecksum, "basinGeometryChecksum");
        basinRelationId = FoundationValidationV2.slug(basinRelationId, "basinRelationId");
        if (centerXMillionths < 0 || centerZMillionths < 0) {
            throw new IllegalArgumentException("seamount center is invalid");
        }
        if (selectedBaseRadiusBlocks < 4 || selectedBaseRadiusBlocks > 64
                || selectedReliefBlocks < 4 || selectedReliefBlocks > 64
                || selectedSummitDepthBlocksBelowSea < 8 || selectedSummitDepthBlocksBelowSea > 240) {
            throw new IllegalArgumentException("seamount profile dimensions are invalid");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("seamount bounds are invalid");
        }
        long maxX = Math.addExact(
                Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        long maxZ = Math.addExact(
                Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE - 1L);
        if (centerXMillionths > maxX || centerZMillionths > maxZ) {
            throw new IllegalArgumentException("seamount center is out of bounds");
        }
        reliefFieldId = FoundationValidationV2.qualified(reliefFieldId, "reliefFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        slopeFieldId = FoundationValidationV2.qualified(slopeFieldId, "slopeFieldId");
        fluidColumnHintFieldId = FoundationValidationV2.qualified(
                fluidColumnHintFieldId, "fluidColumnHintFieldId");
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("seamount support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public SeamountPlanV2 withCanonicalChecksum(String checksum) {
        return new SeamountPlanV2(
                planVersion, featureId, basinFeatureId, basinGeometryChecksum, basinRelationId,
                centerXMillionths, centerZMillionths,
                selectedBaseRadiusBlocks, selectedReliefBlocks, selectedSummitDepthBlocksBelowSea,
                width, length, minY, maxY, waterLevel,
                reliefFieldId, ownershipFieldId, slopeFieldId, fluidColumnHintFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }
}
