package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Canonical V2-3-03 execution plan for one RIVER / MEANDERING_RIVER reach.
 * Meander reshapes the centerline corridor only; graph topology stays source→mouth.
 */
public record MeanderingRiverPlanV2(
        int planVersion,
        String featureId,
        TerrainIntentV2.RiverVariant variant,
        TerrainIntentV2.DischargeClass dischargeClass,
        String basinId,
        String waterBodyId,
        String reachId,
        String sourceNodeId,
        String mouthNodeId,
        List<CenterlineSample> centerline,
        long totalArcLengthMillionths,
        long sourceBedYMillionths,
        long mouthBedYMillionths,
        long waterDepthMillionths,
        int minimumBankfullWidthBlocks,
        int maximumBankfullWidthBlocks,
        int selectedBankfullWidthBlocks,
        int bankWidthBlocks,
        int floodplainWidthBlocks,
        int meanderAmplitudeBlocks,
        int meanderWavelengthBlocks,
        int meanderCorridorHalfWidthBlocks,
        long minimumBedSlopeMillionths,
        int selectedDischargeIndex,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String channelMaskFieldId,
        String bankMaskFieldId,
        String floodplainMaskFieldId,
        String meanderCorridorFieldId,
        String localWidthFieldId,
        String dischargeIndexFieldId,
        String bedElevationFieldId,
        String waterSurfaceFieldId,
        String waterDepthFieldId,
        String waterBodyIdFieldId,
        int supportRadiusXZ,
        String geometryChecksum
) {
    public static final int VERSION = 1;
    public static final int MAXIMUM_CENTERLINE_SAMPLES = 2_048;

    public MeanderingRiverPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("meandering river planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(dischargeClass, "dischargeClass");
        basinId = V2Validation.slug(basinId, "basinId");
        waterBodyId = V2Validation.slug(waterBodyId, "waterBodyId");
        reachId = V2Validation.slug(reachId, "reachId");
        sourceNodeId = V2Validation.slug(sourceNodeId, "sourceNodeId");
        mouthNodeId = V2Validation.slug(mouthNodeId, "mouthNodeId");
        if (sourceNodeId.equals(mouthNodeId)) {
            throw new IllegalArgumentException("river source and mouth nodes must differ");
        }
        centerline = V2Validation.sorted(
                centerline, "centerline", MAXIMUM_CENTERLINE_SAMPLES,
                Comparator.comparingLong(CenterlineSample::arcLengthMillionths)
                        .thenComparingInt(CenterlineSample::sequence));
        if (centerline.size() < 2) {
            throw new IllegalArgumentException("river centerline requires at least two samples");
        }
        if (centerline.getFirst().arcLengthMillionths() != 0L
                || centerline.getLast().arcLengthMillionths() != totalArcLengthMillionths
                || totalArcLengthMillionths < TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("river centerline arc-length contract is invalid");
        }
        for (int index = 1; index < centerline.size(); index++) {
            if (centerline.get(index).arcLengthMillionths() < centerline.get(index - 1).arcLengthMillionths()) {
                throw new IllegalArgumentException("river centerline arc length must be non-decreasing");
            }
            if (centerline.get(index).bedYMillionths() > centerline.get(index - 1).bedYMillionths()) {
                throw new IllegalArgumentException("river bed profile must be monotonically non-increasing");
            }
        }
        if (sourceBedYMillionths != centerline.getFirst().bedYMillionths()
                || mouthBedYMillionths != centerline.getLast().bedYMillionths()
                || mouthBedYMillionths > sourceBedYMillionths
                || waterDepthMillionths < TerrainIntentV2.FIXED_SCALE
                || waterDepthMillionths > 64L * TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("river bed/water vertical contract is invalid");
        }
        if (minimumBankfullWidthBlocks < 1
                || minimumBankfullWidthBlocks > selectedBankfullWidthBlocks
                || selectedBankfullWidthBlocks > maximumBankfullWidthBlocks
                || maximumBankfullWidthBlocks > 64
                || bankWidthBlocks < 1 || bankWidthBlocks > 32
                || floodplainWidthBlocks < selectedBankfullWidthBlocks
                || floodplainWidthBlocks > 128
                || meanderAmplitudeBlocks < 0 || meanderAmplitudeBlocks > 64
                || meanderWavelengthBlocks < 0 || meanderWavelengthBlocks > 256
                || meanderCorridorHalfWidthBlocks < selectedBankfullWidthBlocks
                || meanderCorridorHalfWidthBlocks > 192) {
            throw new IllegalArgumentException("river width/meander contract is invalid");
        }
        if (variant == TerrainIntentV2.RiverVariant.RIVER) {
            if (meanderAmplitudeBlocks != 0 || meanderWavelengthBlocks != 0) {
                throw new IllegalArgumentException("straight river variant forbids meander amplitude/wavelength");
            }
        } else if (meanderAmplitudeBlocks < 1 || meanderWavelengthBlocks < selectedBankfullWidthBlocks * 4) {
            throw new IllegalArgumentException("meandering variant requires positive bounded meander parameters");
        }
        if (minimumBedSlopeMillionths < 1 || minimumBedSlopeMillionths > TerrainIntentV2.FIXED_SCALE
                || selectedDischargeIndex < 1 || selectedDischargeIndex > 3
                || width < 2 || width > 1_000 || length < 2 || length > 1_000
                || minY >= maxY || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("river world/discharge contract is invalid");
        }
        channelMaskFieldId = V2Validation.qualifiedId(channelMaskFieldId, "channelMaskFieldId");
        bankMaskFieldId = V2Validation.qualifiedId(bankMaskFieldId, "bankMaskFieldId");
        floodplainMaskFieldId = V2Validation.qualifiedId(floodplainMaskFieldId, "floodplainMaskFieldId");
        meanderCorridorFieldId = V2Validation.qualifiedId(meanderCorridorFieldId, "meanderCorridorFieldId");
        localWidthFieldId = V2Validation.qualifiedId(localWidthFieldId, "localWidthFieldId");
        dischargeIndexFieldId = V2Validation.qualifiedId(dischargeIndexFieldId, "dischargeIndexFieldId");
        bedElevationFieldId = V2Validation.qualifiedId(bedElevationFieldId, "bedElevationFieldId");
        waterSurfaceFieldId = V2Validation.qualifiedId(waterSurfaceFieldId, "waterSurfaceFieldId");
        waterDepthFieldId = V2Validation.qualifiedId(waterDepthFieldId, "waterDepthFieldId");
        waterBodyIdFieldId = V2Validation.qualifiedId(waterBodyIdFieldId, "waterBodyIdFieldId");
        if (supportRadiusXZ < meanderCorridorHalfWidthBlocks
                || supportRadiusXZ < floodplainWidthBlocks
                || supportRadiusXZ > 256) {
            throw new IllegalArgumentException("river support radius is insufficient");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
    }

    public record CenterlineSample(
            int sequence,
            long arcLengthMillionths,
            long xMillionths,
            long zMillionths,
            long bedYMillionths,
            int localHalfWidthBlocks
    ) {
        public CenterlineSample {
            if (sequence < 0 || sequence >= MAXIMUM_CENTERLINE_SAMPLES
                    || arcLengthMillionths < 0
                    || xMillionths < 0 || zMillionths < 0
                    || localHalfWidthBlocks < 1 || localHalfWidthBlocks > 64) {
                throw new IllegalArgumentException("river centerline sample is out of range");
            }
        }
    }
}
