package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Canonical V2-3-06 execution plan for one WATERFALL fall node on a river path.
 * Falling water column, behind-fall cavity, and volume fluid are out of scope (V2-5 deferred).
 */
public record WaterfallPlanV2(
        int planVersion,
        String featureId,
        String riverFeatureId,
        String onPathRelationId,
        long lipXMillionths,
        long lipZMillionths,
        long baseXMillionths,
        long baseZMillionths,
        long lipArcLengthMillionths,
        long baseArcLengthMillionths,
        long lipBedYMillionths,
        long baseBedYMillionths,
        int minimumDropBlocks,
        int maximumDropBlocks,
        int selectedDropBlocks,
        int lipWidthBlocks,
        int plungePoolRadiusBlocks,
        int plungePoolDepthBlocks,
        int behindFallClearanceBlocks,
        String upstreamReachId,
        String downstreamReachId,
        String lipNodeId,
        String baseNodeId,
        List<MeanderingRiverPlanV2.CenterlineSample> upstreamCenterline,
        List<MeanderingRiverPlanV2.CenterlineSample> downstreamCenterline,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String lipMaskFieldId,
        String baseMaskFieldId,
        String plungePoolMaskFieldId,
        String lipElevationFieldId,
        String baseElevationFieldId,
        String plungePoolFloorFieldId,
        String bedElevationFieldId,
        int supportRadiusXZ,
        String geometryChecksum,
        String riverGeometryChecksum
) {
    public static final int VERSION = 1;
    public static final int MAXIMUM_REACH_SAMPLES = MeanderingRiverPlanV2.MAXIMUM_CENTERLINE_SAMPLES;

    public WaterfallPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("waterfall planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        riverFeatureId = V2Validation.slug(riverFeatureId, "riverFeatureId");
        onPathRelationId = V2Validation.slug(onPathRelationId, "onPathRelationId");
        upstreamReachId = V2Validation.slug(upstreamReachId, "upstreamReachId");
        downstreamReachId = V2Validation.slug(downstreamReachId, "downstreamReachId");
        lipNodeId = V2Validation.slug(lipNodeId, "lipNodeId");
        baseNodeId = V2Validation.slug(baseNodeId, "baseNodeId");
        if (upstreamReachId.equals(downstreamReachId) || lipNodeId.equals(baseNodeId)) {
            throw new IllegalArgumentException("waterfall reach/node split IDs must differ");
        }
        if (minimumDropBlocks < 1
                || minimumDropBlocks > selectedDropBlocks
                || selectedDropBlocks > maximumDropBlocks
                || maximumDropBlocks > 128
                || lipWidthBlocks < 1 || lipWidthBlocks > 32
                || plungePoolRadiusBlocks < 2 || plungePoolRadiusBlocks > 64
                || plungePoolDepthBlocks < 1 || plungePoolDepthBlocks > 16
                || behindFallClearanceBlocks != 0) {
            throw new IllegalArgumentException("waterfall drop/lip/pool contract is invalid");
        }
        long expectedDrop = Math.multiplyExact((long) selectedDropBlocks, TerrainIntentV2.FIXED_SCALE);
        if (lipBedYMillionths - baseBedYMillionths != expectedDrop
                || baseBedYMillionths >= lipBedYMillionths
                || baseArcLengthMillionths <= lipArcLengthMillionths
                || lipArcLengthMillionths < TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("waterfall lip/base elevation or arc contract is invalid");
        }
        upstreamCenterline = V2Validation.sorted(
                upstreamCenterline, "upstreamCenterline", MAXIMUM_REACH_SAMPLES,
                Comparator.comparingLong(MeanderingRiverPlanV2.CenterlineSample::arcLengthMillionths)
                        .thenComparingInt(MeanderingRiverPlanV2.CenterlineSample::sequence));
        downstreamCenterline = V2Validation.sorted(
                downstreamCenterline, "downstreamCenterline", MAXIMUM_REACH_SAMPLES,
                Comparator.comparingLong(MeanderingRiverPlanV2.CenterlineSample::arcLengthMillionths)
                        .thenComparingInt(MeanderingRiverPlanV2.CenterlineSample::sequence));
        if (upstreamCenterline.size() < 2 || downstreamCenterline.size() < 2) {
            throw new IllegalArgumentException("waterfall reach split requires upstream and downstream samples");
        }
        if (upstreamCenterline.getLast().arcLengthMillionths() != lipArcLengthMillionths
                || upstreamCenterline.getLast().bedYMillionths() != lipBedYMillionths
                || downstreamCenterline.getFirst().arcLengthMillionths() != baseArcLengthMillionths
                || downstreamCenterline.getFirst().bedYMillionths() != baseBedYMillionths) {
            throw new IllegalArgumentException("waterfall reach endpoints must match lip/base samples");
        }
        for (int index = 1; index < upstreamCenterline.size(); index++) {
            if (upstreamCenterline.get(index).bedYMillionths()
                    > upstreamCenterline.get(index - 1).bedYMillionths()) {
                throw new IllegalArgumentException("upstream waterfall reach bed must be non-increasing");
            }
        }
        for (int index = 1; index < downstreamCenterline.size(); index++) {
            if (downstreamCenterline.get(index).bedYMillionths()
                    > downstreamCenterline.get(index - 1).bedYMillionths()) {
                throw new IllegalArgumentException("downstream waterfall reach bed must be non-increasing");
            }
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000
                || minY >= maxY || waterLevel < minY || waterLevel > maxY
                || baseBedYMillionths < (long) minY * TerrainIntentV2.FIXED_SCALE
                || lipBedYMillionths > (long) maxY * TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("waterfall world bounds are invalid");
        }
        lipMaskFieldId = V2Validation.qualifiedId(lipMaskFieldId, "lipMaskFieldId");
        baseMaskFieldId = V2Validation.qualifiedId(baseMaskFieldId, "baseMaskFieldId");
        plungePoolMaskFieldId = V2Validation.qualifiedId(plungePoolMaskFieldId, "plungePoolMaskFieldId");
        lipElevationFieldId = V2Validation.qualifiedId(lipElevationFieldId, "lipElevationFieldId");
        baseElevationFieldId = V2Validation.qualifiedId(baseElevationFieldId, "baseElevationFieldId");
        plungePoolFloorFieldId = V2Validation.qualifiedId(plungePoolFloorFieldId, "plungePoolFloorFieldId");
        bedElevationFieldId = V2Validation.qualifiedId(bedElevationFieldId, "bedElevationFieldId");
        if (supportRadiusXZ < Math.max(lipWidthBlocks, plungePoolRadiusBlocks)
                || supportRadiusXZ > 256) {
            throw new IllegalArgumentException("waterfall support radius is insufficient");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
        riverGeometryChecksum = V2Validation.checksum(riverGeometryChecksum, "riverGeometryChecksum");
        Objects.requireNonNull(upstreamCenterline, "upstreamCenterline");
        Objects.requireNonNull(downstreamCenterline, "downstreamCenterline");
    }
}
