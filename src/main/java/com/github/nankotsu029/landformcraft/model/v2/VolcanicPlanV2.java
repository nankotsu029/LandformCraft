package com.github.nankotsu029.landformcraft.model.v2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Frozen V2-3-11 execution plan for an EXPERIMENTAL 2.5D volcanic archipelago skeleton. */
public record VolcanicPlanV2(
        int planVersion,
        String featureId,
        List<IslandMass> islands,
        List<SubmarineSaddle> saddles,
        CalderaPlanHook calderaPlanHook,
        LavaPlanHook lavaPlanHook,
        int selectedSubmarineSaddleDepthBlocks,
        int dominantIslandIndex,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String islandMaskFieldId,
        String islandIndexFieldId,
        String summitReliefFieldId,
        String submarineSaddleMaskFieldId,
        String radialDrainageFieldId,
        String provisionalSurfaceFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum
) {
    public static final int VERSION = 1;
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;

    public VolcanicPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("volcanic planVersion must be 1");
        }
        featureId = V2Validation.slug(featureId, "featureId");
        islands = V2Validation.sorted(islands, "islands", 64, Comparator.comparing(IslandMass::pointId));
        if (islands.size() < 2) {
            throw new IllegalArgumentException("volcanic plan needs at least two islands");
        }
        saddles = V2Validation.sorted(saddles, "saddles", 256, Comparator.comparing(SubmarineSaddle::saddleId));
        if (dominantIslandIndex < 0 || dominantIslandIndex >= islands.size()) {
            throw new IllegalArgumentException("dominantIslandIndex is invalid");
        }
        if (selectedSubmarineSaddleDepthBlocks < 4 || selectedSubmarineSaddleDepthBlocks > 64) {
            throw new IllegalArgumentException("selectedSubmarineSaddleDepthBlocks is invalid");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("volcanic bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        for (IslandMass island : islands) {
            if (island.xMillionths() < 0 || island.xMillionths() > maxX
                    || island.zMillionths() < 0 || island.zMillionths() > maxZ) {
                throw new IllegalArgumentException("volcanic island center is out of bounds");
            }
        }
        islandMaskFieldId = V2Validation.qualifiedId(islandMaskFieldId, "islandMaskFieldId");
        islandIndexFieldId = V2Validation.qualifiedId(islandIndexFieldId, "islandIndexFieldId");
        summitReliefFieldId = V2Validation.qualifiedId(summitReliefFieldId, "summitReliefFieldId");
        submarineSaddleMaskFieldId = V2Validation.qualifiedId(submarineSaddleMaskFieldId, "submarineSaddleMaskFieldId");
        radialDrainageFieldId = V2Validation.qualifiedId(radialDrainageFieldId, "radialDrainageFieldId");
        provisionalSurfaceFieldId = V2Validation.qualifiedId(provisionalSurfaceFieldId, "provisionalSurfaceFieldId");
        if (supportRadiusXZ < 8 || supportRadiusXZ > 256
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("volcanic support or work budget is invalid");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
    }

    public record IslandMass(
            String pointId,
            long xMillionths,
            long zMillionths,
            int radiusBlocks,
            int summitHeightBlocksAboveSea,
            int islandIndex
    ) {
        public IslandMass {
            pointId = V2Validation.slug(pointId, "pointId");
            if (xMillionths < 0 || zMillionths < 0
                    || radiusBlocks < 8 || radiusBlocks > 256
                    || summitHeightBlocksAboveSea < 8 || summitHeightBlocksAboveSea > 256
                    || islandIndex < 1) {
                throw new IllegalArgumentException("volcanic island mass is invalid");
            }
        }
    }

    public record SubmarineSaddle(
            String saddleId,
            String fromPointId,
            String toPointId,
            long midXMillionths,
            long midZMillionths,
            int depthBlocks
    ) {
        public SubmarineSaddle {
            saddleId = V2Validation.slug(saddleId, "saddleId");
            fromPointId = V2Validation.slug(fromPointId, "fromPointId");
            toPointId = V2Validation.slug(toPointId, "toPointId");
            if (midXMillionths < 0 || midZMillionths < 0 || depthBlocks < 4 || depthBlocks > 64) {
                throw new IllegalArgumentException("volcanic submarine saddle is invalid");
            }
        }
    }

    public record CalderaPlanHook(
            String calderaFeatureId,
            String withinRelationId,
            String hostPointId,
            int rimRadiusBlocks,
            int rimReliefBlocks,
            int craterFloorDepthBlocks,
            TerrainIntentV2.CalderaBreachDirection breachDirection
    ) {
        public CalderaPlanHook {
            calderaFeatureId = V2Validation.slug(calderaFeatureId, "calderaFeatureId");
            withinRelationId = V2Validation.slug(withinRelationId, "withinRelationId");
            hostPointId = V2Validation.slug(hostPointId, "hostPointId");
            Objects.requireNonNull(breachDirection, "breachDirection");
            if (rimRadiusBlocks < 8 || rimReliefBlocks < 4 || craterFloorDepthBlocks < 4) {
                throw new IllegalArgumentException("caldera hook dimensions are invalid");
            }
        }
    }

    /** Hook only; lava material is deferred to V2-4. */
    public record LavaPlanHook(
            String lavaFeatureId,
            String originatesAtRelationId,
            String calderaFeatureId,
            int selectedWidthBlocks,
            long surfaceRoughnessMillionths
    ) {
        public LavaPlanHook {
            lavaFeatureId = V2Validation.slug(lavaFeatureId, "lavaFeatureId");
            originatesAtRelationId = V2Validation.slug(originatesAtRelationId, "originatesAtRelationId");
            calderaFeatureId = V2Validation.slug(calderaFeatureId, "calderaFeatureId");
            if (selectedWidthBlocks < 4 || selectedWidthBlocks > 64
                    || surfaceRoughnessMillionths < 0
                    || surfaceRoughnessMillionths > TerrainIntentV2.FIXED_SCALE) {
                throw new IllegalArgumentException("lava hook dimensions are invalid");
            }
        }
    }
}
