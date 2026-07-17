package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Canonical V2-2 execution plan for one supported breakwater harbor. */
public record BreakwaterHarborPlanV2(
        int planVersion,
        String featureId,
        CrestProfileKind crestProfileKind,
        FoundationProfileKind foundationProfileKind,
        int crestWidthBlocks,
        int crestAboveWaterBlocks,
        int outerDepthBlocks,
        int innerDepthBlocks,
        int foundationSideSlopeRunPerRiseMillionths,
        List<ArmPlan> arms,
        List<String> openingEndpointIds,
        CoastalFeaturePlanV2.BlockPoint openingFirst,
        CoastalFeaturePlanV2.BlockPoint openingSecond,
        int requestedClearOpeningWidthBlocks,
        long actualClearOpeningWidthMillionths,
        TerrainIntentV2.Measurement openingMeasurement,
        TerrainIntentV2.InnerSide innerSide,
        String basinFeatureId,
        String enclosureRelationId,
        int minY,
        int maxY,
        int waterLevel,
        String regionFieldId,
        String armIndexFieldId,
        String topHeightFieldId,
        String bottomHeightFieldId,
        int supportRadiusXZ
) {
    public static final int VERSION = 1;
    public static final int FIXED_SCALE = 1_000_000;

    public BreakwaterHarborPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("breakwater planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        Objects.requireNonNull(crestProfileKind, "crestProfileKind");
        Objects.requireNonNull(foundationProfileKind, "foundationProfileKind");
        if (crestWidthBlocks < 1 || crestWidthBlocks > 64
                || crestAboveWaterBlocks < 0 || crestAboveWaterBlocks > 32
                || outerDepthBlocks < 1 || outerDepthBlocks > 64
                || innerDepthBlocks < 1 || innerDepthBlocks > 64
                || foundationSideSlopeRunPerRiseMillionths < 250_000
                || foundationSideSlopeRunPerRiseMillionths > 4_000_000) {
            throw new IllegalArgumentException("breakwater dimensions are invalid");
        }
        arms = V2Validation.sorted(arms, "arms", 2, Comparator.comparing(ArmPlan::armId));
        if (arms.size() != 2) throw new IllegalArgumentException("breakwater requires exactly two arms");
        HashSet<String> armIds = new HashSet<>();
        for (int index = 0; index < arms.size(); index++) {
            ArmPlan arm = arms.get(index);
            if (!armIds.add(arm.armId()) || arm.armOrder() != index + 1) {
                throw new IllegalArgumentException("breakwater arm order must follow stable arm id order");
            }
        }
        openingEndpointIds = V2Validation.sorted(
                openingEndpointIds, "openingEndpointIds", 2, Comparator.naturalOrder()).stream()
                .map(value -> V2Validation.slug(value, "opening endpoint id")).toList();
        if (openingEndpointIds.size() != 2
                || openingEndpointIds.getFirst().equals(openingEndpointIds.getLast())) {
            throw new IllegalArgumentException("breakwater opening endpoint ids are invalid");
        }
        if (arms.stream().map(ArmPlan::openingEndpointId).distinct().count() != 2
                || !new HashSet<>(openingEndpointIds).equals(
                new HashSet<>(arms.stream().map(ArmPlan::openingEndpointId).toList()))) {
            throw new IllegalArgumentException("each breakwater arm must own one opening endpoint");
        }
        Objects.requireNonNull(openingFirst, "openingFirst");
        Objects.requireNonNull(openingSecond, "openingSecond");
        if (openingFirst.equals(openingSecond) || requestedClearOpeningWidthBlocks < 2
                || requestedClearOpeningWidthBlocks > 64
                || actualClearOpeningWidthMillionths < 2L * FIXED_SCALE) {
            throw new IllegalArgumentException("breakwater opening is invalid");
        }
        long requestedWidthMillionths = Math.multiplyExact(
                (long) requestedClearOpeningWidthBlocks, FIXED_SCALE);
        if (Math.abs(actualClearOpeningWidthMillionths - requestedWidthMillionths) > FIXED_SCALE / 2L) {
            throw new IllegalArgumentException("breakwater actual opening differs from requested width");
        }
        long openingDx = openingSecond.xMillionths() - openingFirst.xMillionths();
        long openingDz = openingSecond.zMillionths() - openingFirst.zMillionths();
        long centerDistance = integerSquareRoot(Math.addExact(
                Math.multiplyExact(openingDx, openingDx), Math.multiplyExact(openingDz, openingDz)));
        if (Math.abs(centerDistance - Math.multiplyExact((long) crestWidthBlocks, FIXED_SCALE)
                - actualClearOpeningWidthMillionths) > 1L) {
            throw new IllegalArgumentException("breakwater opening geometry differs from actual width");
        }
        Objects.requireNonNull(openingMeasurement, "openingMeasurement");
        Objects.requireNonNull(innerSide, "innerSide");
        basinFeatureId = V2Validation.slug(basinFeatureId, "basinFeatureId");
        enclosureRelationId = V2Validation.slug(enclosureRelationId, "enclosureRelationId");
        if (minY >= maxY || waterLevel < minY || waterLevel > maxY
                || (long) waterLevel - Math.max(outerDepthBlocks, innerDepthBlocks) < minY
                || (long) waterLevel + crestAboveWaterBlocks > maxY) {
            throw new IllegalArgumentException("breakwater vertical bounds are invalid");
        }
        regionFieldId = V2Validation.qualifiedId(regionFieldId, "regionFieldId");
        armIndexFieldId = V2Validation.qualifiedId(armIndexFieldId, "armIndexFieldId");
        topHeightFieldId = V2Validation.qualifiedId(topHeightFieldId, "topHeightFieldId");
        bottomHeightFieldId = V2Validation.qualifiedId(bottomHeightFieldId, "bottomHeightFieldId");
        long halfCrest = Math.multiplyExact((long) crestWidthBlocks, FIXED_SCALE) / 2L;
        long toeRun = Math.multiplyExact(
                (long) Math.max(outerDepthBlocks, innerDepthBlocks),
                foundationSideSlopeRunPerRiseMillionths);
        int requiredSupport = Math.toIntExact(
                Math.addExact(Math.addExact(halfCrest, toeRun), FIXED_SCALE - 1L) / FIXED_SCALE);
        if (supportRadiusXZ != requiredSupport || supportRadiusXZ > 64) {
            throw new IllegalArgumentException("breakwater support radius is invalid");
        }
    }

    public enum CrestProfileKind { FLAT }
    public enum FoundationProfileKind { LINEAR_SIDE_SLOPE }

    public record ArmPlan(
            int armOrder,
            String armId,
            String startEndpointId,
            String endEndpointId,
            String openingEndpointId,
            boolean openingAtStart,
            long lengthMillionths
    ) {
        public ArmPlan {
            if (armOrder < 1 || armOrder > 2) throw new IllegalArgumentException("armOrder outside 1..2");
            armId = V2Validation.slug(armId, "armId");
            startEndpointId = V2Validation.slug(startEndpointId, "startEndpointId");
            endEndpointId = V2Validation.slug(endEndpointId, "endEndpointId");
            openingEndpointId = V2Validation.slug(openingEndpointId, "openingEndpointId");
            if (startEndpointId.equals(endEndpointId)
                    || (!openingEndpointId.equals(startEndpointId) && !openingEndpointId.equals(endEndpointId))
                    || openingAtStart != openingEndpointId.equals(startEndpointId)
                    || lengthMillionths < FIXED_SCALE) {
                throw new IllegalArgumentException("breakwater arm topology is invalid");
            }
        }
    }

    private static long integerSquareRoot(long value) {
        if (value < 0L) throw new ArithmeticException("negative square root");
        long result = 0L;
        long bit = 1L << 62;
        while (bit > value) bit >>>= 2;
        long remaining = value;
        while (bit != 0L) {
            if (remaining >= result + bit) {
                remaining -= result + bit;
                result = (result >>> 1) + bit;
            } else {
                result >>>= 1;
            }
            bit >>>= 2;
        }
        return result;
    }
}
