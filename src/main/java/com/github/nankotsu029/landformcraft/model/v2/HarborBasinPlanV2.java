package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Canonical V2-2 execution plan for one supported harbor basin. */
public record HarborBasinPlanV2(
        int planVersion,
        String featureId,
        BottomProfileKind bottomProfileKind,
        int minimumDepthBlocks,
        int maximumDepthBlocks,
        int profileTransitionBlocks,
        List<String> entranceEndpointIds,
        CoastalFeaturePlanV2.BlockPoint entranceFirst,
        CoastalFeaturePlanV2.BlockPoint entranceSecond,
        int outwardUnitXMillionths,
        int outwardUnitZMillionths,
        long openingWidthMillionths,
        int entranceCorridorLengthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        String regionFieldId,
        String waterFieldId,
        String depthFieldId,
        String bottomHeightFieldId,
        int supportRadiusXZ
) {
    public static final int VERSION = 1;
    public static final int FIXED_SCALE = 1_000_000;

    public HarborBasinPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("harbor basin planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        Objects.requireNonNull(bottomProfileKind, "bottomProfileKind");
        if (minimumDepthBlocks < 1 || minimumDepthBlocks > maximumDepthBlocks || maximumDepthBlocks > 64) {
            throw new IllegalArgumentException("harbor basin depth outside 1..64");
        }
        if (profileTransitionBlocks < 1 || profileTransitionBlocks > 64
                || entranceCorridorLengthBlocks < 1 || entranceCorridorLengthBlocks > 64) {
            throw new IllegalArgumentException("harbor basin corridor/profile outside 1..64");
        }
        entranceEndpointIds = V2Validation.sorted(
                entranceEndpointIds, "entranceEndpointIds", 2, Comparator.naturalOrder()).stream()
                .map(value -> V2Validation.slug(value, "endpoint id")).toList();
        if (entranceEndpointIds.size() != 2
                || entranceEndpointIds.getFirst().equals(entranceEndpointIds.getLast())) {
            throw new IllegalArgumentException("harbor basin requires two unique entrance endpoint ids");
        }
        Objects.requireNonNull(entranceFirst, "entranceFirst");
        Objects.requireNonNull(entranceSecond, "entranceSecond");
        if (entranceFirst.equals(entranceSecond) || openingWidthMillionths < 2L * FIXED_SCALE) {
            throw new IllegalArgumentException("harbor basin entrance is too narrow");
        }
        long normalLengthSquared = Math.addExact(
                Math.multiplyExact((long) outwardUnitXMillionths, outwardUnitXMillionths),
                Math.multiplyExact((long) outwardUnitZMillionths, outwardUnitZMillionths));
        long expected = (long) FIXED_SCALE * FIXED_SCALE;
        if (Math.abs(normalLengthSquared - expected) > 2_000_000L) {
            throw new IllegalArgumentException("harbor basin outward unit vector is not normalized");
        }
        if (minY >= maxY || waterLevel < minY || waterLevel > maxY
                || (long) waterLevel - maximumDepthBlocks < minY) {
            throw new IllegalArgumentException("harbor basin vertical bounds are invalid");
        }
        regionFieldId = V2Validation.qualifiedId(regionFieldId, "regionFieldId");
        waterFieldId = V2Validation.qualifiedId(waterFieldId, "waterFieldId");
        depthFieldId = V2Validation.qualifiedId(depthFieldId, "depthFieldId");
        bottomHeightFieldId = V2Validation.qualifiedId(bottomHeightFieldId, "bottomHeightFieldId");
        if (supportRadiusXZ < profileTransitionBlocks || supportRadiusXZ < entranceCorridorLengthBlocks
                || supportRadiusXZ > 64) {
            throw new IllegalArgumentException("harbor basin support radius is insufficient");
        }
    }

    public enum BottomProfileKind { EDGE_TO_CENTER_LINEAR }
}
