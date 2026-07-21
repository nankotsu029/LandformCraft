package com.github.nankotsu029.landformcraft.model.v2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Canonical V2-3-05 execution plan for one CANYON cross-section bound to a river reach.
 * Strata material, volume ledges, and waterfall are out of scope.
 */
public record CanyonPlanV2(
        int planVersion,
        String featureId,
        String riverFeatureId,
        String withinRelationId,
        TerrainIntentV2.CanyonCrossSection crossSection,
        List<MeanderingRiverPlanV2.CenterlineSample> centerline,
        long totalArcLengthMillionths,
        long sourceBedYMillionths,
        long mouthBedYMillionths,
        int minimumFloorWidthBlocks,
        int maximumFloorWidthBlocks,
        int selectedFloorWidthBlocks,
        int minimumRimWidthBlocks,
        int maximumRimWidthBlocks,
        int selectedRimWidthBlocks,
        int minimumDepthBlocks,
        int maximumDepthBlocks,
        int selectedDepthBlocks,
        int terraceCount,
        int terraceWidthBlocks,
        int riverBankfullWidthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String canyonMaskFieldId,
        String floorMaskFieldId,
        String rimMaskFieldId,
        String terraceMaskFieldId,
        String surfaceHeightFieldId,
        String wallHeightFieldId,
        String bedElevationFieldId,
        int supportRadiusXZ,
        String geometryChecksum,
        String riverGeometryChecksum
) {
    public static final int VERSION = 1;

    public CanyonPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("canyon planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        riverFeatureId = V2Validation.slug(riverFeatureId, "riverFeatureId");
        withinRelationId = V2Validation.slug(withinRelationId, "withinRelationId");
        Objects.requireNonNull(crossSection, "crossSection");
        centerline = V2Validation.sorted(
                centerline, "centerline", MeanderingRiverPlanV2.MAXIMUM_CENTERLINE_SAMPLES,
                Comparator.comparingLong(MeanderingRiverPlanV2.CenterlineSample::arcLengthMillionths)
                        .thenComparingInt(MeanderingRiverPlanV2.CenterlineSample::sequence));
        if (centerline.size() < 2
                || centerline.getFirst().arcLengthMillionths() != 0L
                || centerline.getLast().arcLengthMillionths() != totalArcLengthMillionths
                || totalArcLengthMillionths < TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("canyon centerline arc-length contract is invalid");
        }
        if (sourceBedYMillionths < mouthBedYMillionths) {
            throw new IllegalArgumentException("canyon shared bed must remain downstream-non-increasing");
        }
        if (minimumFloorWidthBlocks < 2
                || minimumFloorWidthBlocks > selectedFloorWidthBlocks
                || selectedFloorWidthBlocks > maximumFloorWidthBlocks
                || maximumFloorWidthBlocks > 64
                || minimumRimWidthBlocks < selectedFloorWidthBlocks + 2
                || minimumRimWidthBlocks > selectedRimWidthBlocks
                || selectedRimWidthBlocks > maximumRimWidthBlocks
                || maximumRimWidthBlocks > 256
                || minimumDepthBlocks < 1
                || minimumDepthBlocks > selectedDepthBlocks
                || selectedDepthBlocks > maximumDepthBlocks
                || maximumDepthBlocks > 128
                || riverBankfullWidthBlocks < 1
                || riverBankfullWidthBlocks > selectedFloorWidthBlocks) {
            throw new IllegalArgumentException("canyon width/depth contract is invalid");
        }
        boolean terraced = crossSection == TerrainIntentV2.CanyonCrossSection.TERRACED_V
                || crossSection == TerrainIntentV2.CanyonCrossSection.TERRACED_U;
        if (terraced) {
            if (terraceCount < 1 || terraceCount > 4
                    || terraceWidthBlocks < 1 || terraceWidthBlocks > 32) {
                throw new IllegalArgumentException("terraced canyon plan terrace contract is invalid");
            }
        } else if (terraceCount != 0 || terraceWidthBlocks != 0) {
            throw new IllegalArgumentException("non-terraced canyon plan must not carry terraces");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING
                || minY >= maxY || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("canyon world bounds are invalid");
        }
        canyonMaskFieldId = V2Validation.qualifiedId(canyonMaskFieldId, "canyonMaskFieldId");
        floorMaskFieldId = V2Validation.qualifiedId(floorMaskFieldId, "floorMaskFieldId");
        rimMaskFieldId = V2Validation.qualifiedId(rimMaskFieldId, "rimMaskFieldId");
        terraceMaskFieldId = V2Validation.qualifiedId(terraceMaskFieldId, "terraceMaskFieldId");
        surfaceHeightFieldId = V2Validation.qualifiedId(surfaceHeightFieldId, "surfaceHeightFieldId");
        wallHeightFieldId = V2Validation.qualifiedId(wallHeightFieldId, "wallHeightFieldId");
        bedElevationFieldId = V2Validation.qualifiedId(bedElevationFieldId, "bedElevationFieldId");
        if (supportRadiusXZ < (selectedRimWidthBlocks + 1) / 2 || supportRadiusXZ > 256) {
            throw new IllegalArgumentException("canyon support radius is insufficient");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
        riverGeometryChecksum = V2Validation.checksum(riverGeometryChecksum, "riverGeometryChecksum");
    }
}
