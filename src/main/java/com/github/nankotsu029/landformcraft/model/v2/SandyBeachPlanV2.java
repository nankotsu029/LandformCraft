package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Objects;

/** Canonical V2-2 execution plan for one supported sandy beach. */
public record SandyBeachPlanV2(
        int planVersion,
        String featureId,
        WidthProfileKind widthProfileKind,
        int minimumWidthBlocks,
        int maximumWidthBlocks,
        int endpointTaperBlocks,
        int foreshoreShareMillionths,
        long minimumShoreSlopeDegreesMillionths,
        long maximumShoreSlopeDegreesMillionths,
        long selectedShoreSlopeDegreesMillionths,
        int risePerBlockMillionths,
        int nearshoreDistanceBlocks,
        int nearshoreTargetDepthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        String localWidthFieldId,
        String surfaceHeightFieldId,
        String bandFieldId,
        String semanticSandFieldId,
        int supportRadiusXZ
) {
    public static final int VERSION = 1;

    public SandyBeachPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("sandy beach planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        Objects.requireNonNull(widthProfileKind, "widthProfileKind");
        if (minimumWidthBlocks < 1 || minimumWidthBlocks > maximumWidthBlocks || maximumWidthBlocks > 64) {
            throw new IllegalArgumentException("sandy beach width outside 1..64");
        }
        if (endpointTaperBlocks < 1 || endpointTaperBlocks > 64) {
            throw new IllegalArgumentException("endpoint taper outside 1..64");
        }
        if (foreshoreShareMillionths < 100_000 || foreshoreShareMillionths > 900_000) {
            throw new IllegalArgumentException("foreshore share outside 0.1..0.9");
        }
        if (minimumShoreSlopeDegreesMillionths <= 0
                || minimumShoreSlopeDegreesMillionths > selectedShoreSlopeDegreesMillionths
                || selectedShoreSlopeDegreesMillionths > maximumShoreSlopeDegreesMillionths
                || maximumShoreSlopeDegreesMillionths > 30_000_000L
                || risePerBlockMillionths < 1 || risePerBlockMillionths > 577_350) {
            throw new IllegalArgumentException("sandy beach slope contract is invalid");
        }
        if (nearshoreDistanceBlocks < 1 || nearshoreDistanceBlocks > 63
                || nearshoreTargetDepthBlocks < 1 || nearshoreTargetDepthBlocks > 64) {
            throw new IllegalArgumentException("sandy beach nearshore contract is invalid");
        }
        if (minY >= maxY || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("sandy beach vertical bounds are invalid");
        }
        localWidthFieldId = V2Validation.qualifiedId(localWidthFieldId, "localWidthFieldId");
        surfaceHeightFieldId = V2Validation.qualifiedId(surfaceHeightFieldId, "surfaceHeightFieldId");
        bandFieldId = V2Validation.qualifiedId(bandFieldId, "bandFieldId");
        semanticSandFieldId = V2Validation.qualifiedId(semanticSandFieldId, "semanticSandFieldId");
        if (supportRadiusXZ < maximumWidthBlocks || supportRadiusXZ <= nearshoreDistanceBlocks
                || supportRadiusXZ > 64) {
            throw new IllegalArgumentException("sandy beach support radius is insufficient");
        }
    }

    public enum WidthProfileKind {
        ENDPOINT_TAPER
    }
}
