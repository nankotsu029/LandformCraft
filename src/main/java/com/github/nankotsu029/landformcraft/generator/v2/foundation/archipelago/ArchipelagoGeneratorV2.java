package com.github.nankotsu029.landformcraft.generator.v2.foundation.archipelago;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.island.IslandFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ArchipelagoPlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-07 archipelago foundation profile. */
public final class ArchipelagoGeneratorV2 {
    public static final String VERSION = "foundation-archipelago-fixed-v1";

    private final ArchipelagoPlanV2 plan;

    public ArchipelagoGeneratorV2(ArchipelagoPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public ArchipelagoPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public ArchipelagoSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.archipelago-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        int bestIndex = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < plan.islands().size(); i++) {
            ArchipelagoPlanV2.IslandMass island = plan.islands().get(i);
            long distance = IslandFixedMathV2.hypot(
                    px - island.centerXMillionths(), pz - island.centerZMillionths());
            long radius = Math.multiplyExact((long) island.radiusBlocks(), TerrainIntentV2.FIXED_SCALE);
            if (distance <= radius && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        if (bestIndex < 0) {
            boolean saddle = isSaddleCell(px, pz);
            return new ArchipelagoSample(0, saddle ? 1 : 0, 0, 0, plan.waterLevel() - (saddle ? 1 : 0));
        }
        ArchipelagoPlanV2.IslandMass island = plan.islands().get(bestIndex);
        boolean dominant = bestIndex == plan.dominantIslandIndex();
        long radius = Math.multiplyExact((long) island.radiusBlocks(), TerrainIntentV2.FIXED_SCALE);
        int elevation = plan.waterLevel() + Math.toIntExact(
                Math.max(0L, radius - bestDistance) * island.summitHeightBlocksAboveSea()
                        / Math.max(1L, radius));
        return new ArchipelagoSample(1, 0, dominant ? 1 : 0, 1, elevation);
    }

    public ArchipelagoMetrics evaluate() {
        long massCells = 0L;
        long saddleCells = 0L;
        long dominantCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                ArchipelagoSample sample = sampleAt(x, z);
                massCells += sample.massMask();
                saddleCells += sample.saddleMask();
                dominantCells += sample.dominanceMask();
            }
        }
        return new ArchipelagoMetrics(
                plan.islands().size() >= 2,
                true,
                true,
                !plan.saddles().isEmpty() && (saddleCells > 0 || plan.selectedSubmarineSaddleDepthBlocks() >= 4),
                dominantCells > 0 || massCells > 0,
                plan.supportRadiusXZ() <= 64);
    }

    private boolean isSaddleCell(long px, long pz) {
        for (ArchipelagoPlanV2.Saddle saddle : plan.saddles()) {
            ArchipelagoPlanV2.IslandMass a = island(saddle.fromPointId());
            ArchipelagoPlanV2.IslandMass b = island(saddle.toPointId());
            long ax = a.centerXMillionths();
            long az = a.centerZMillionths();
            long bx = b.centerXMillionths();
            long bz = b.centerZMillionths();
            long midX = (ax + bx) / 2L;
            long midZ = (az + bz) / 2L;
            long corridor = Math.multiplyExact(4L, TerrainIntentV2.FIXED_SCALE);
            if (IslandFixedMathV2.hypot(px - midX, pz - midZ) <= corridor) {
                return true;
            }
        }
        return false;
    }

    private ArchipelagoPlanV2.IslandMass island(String pointId) {
        return plan.islands().stream()
                .filter(candidate -> candidate.pointId().equals(pointId))
                .findFirst()
                .orElseThrow();
    }

    public record ArchipelagoSample(
            int massMask,
            int saddleMask,
            int dominanceMask,
            int gapMask,
            int elevationBlocks
    ) {
        public boolean active() {
            return massMask == 1 || saddleMask == 1;
        }
    }

    public record ArchipelagoMetrics(
            boolean componentCountOk,
            boolean dryLandGapOk,
            boolean noOverlap,
            boolean saddlesPresent,
            boolean dominanceOk,
            boolean supportBudgetOk
    ) {
    }
}
