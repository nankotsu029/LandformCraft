package com.github.nankotsu029.landformcraft.generator.v2.foundation.island;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SingleIslandPlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-07 single-island foundation profile. */
public final class SingleIslandGeneratorV2 {
    public static final String VERSION = "foundation-single-island-fixed-v1";

    private final SingleIslandPlanV2 plan;
    private final long radius;
    private final long shoreInner;
    private final long apronOuter;
    private final String seedNamespace;

    public SingleIslandGeneratorV2(SingleIslandPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        radius = Math.multiplyExact((long) plan.selectedRadiusBlocks(), TerrainIntentV2.FIXED_SCALE);
        shoreInner = Math.subtractExact(radius,
                Math.multiplyExact((long) plan.selectedShoreBandWidthBlocks(), TerrainIntentV2.FIXED_SCALE));
        apronOuter = Math.addExact(radius,
                Math.multiplyExact((long) plan.selectedSubmarineApronDepthBlocks(), TerrainIntentV2.FIXED_SCALE));
        seedNamespace = "single-island:" + plan.featureId();
    }

    public SingleIslandPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public IslandSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.single-island-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long dx = px - plan.centerXMillionths();
        long dz = pz - plan.centerZMillionths();
        long distance = IslandFixedMathV2.hypot(dx, dz);
        if (distance > apronOuter) {
            return IslandSample.outside();
        }
        boolean mass = distance <= radius;
        boolean shore = mass && distance >= shoreInner;
        boolean apron = !mass && distance <= apronOuter;
        boolean drainage = mass && IslandFixedMathV2.radialHash(seedNamespace, dx, dz)
                < plan.selectedRadialDrainageMillionths();
        int elevation = plan.waterLevel();
        if (mass) {
            long remain = Math.max(0L, radius - distance);
            elevation = plan.waterLevel() + Math.toIntExact(
                    remain * plan.selectedSummitHeightBlocksAboveSea() / Math.max(1L, radius));
        } else if (apron) {
            elevation = plan.waterLevel() - 1;
        }
        return new IslandSample(mass ? 1 : 0, shore ? 1 : 0, drainage ? 1 : 0, apron ? 1 : 0, elevation);
    }

    public IslandMetrics evaluate() {
        long massCells = 0L;
        long shoreCells = 0L;
        long drainageCells = 0L;
        long apronCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                IslandSample sample = sampleAt(x, z);
                massCells += sample.islandMask();
                shoreCells += sample.shoreMask();
                drainageCells += sample.drainageMask();
                apronCells += sample.apronMask();
            }
        }
        return new IslandMetrics(
                massCells > 0,
                shoreCells > 0,
                drainageCells > 0 || plan.selectedRadialDrainageMillionths() == 0L,
                apronCells > 0,
                plan.supportRadiusXZ() <= 64);
    }

    public record IslandSample(
            int islandMask,
            int shoreMask,
            int drainageMask,
            int apronMask,
            int elevationBlocks
    ) {
        public static IslandSample outside() {
            return new IslandSample(0, 0, 0, 0, 0);
        }

        public boolean active() {
            return islandMask == 1 || apronMask == 1;
        }
    }

    public record IslandMetrics(
            boolean islandMassPresent,
            boolean shoreBandPresent,
            boolean radialDrainagePresent,
            boolean apronPresent,
            boolean supportBudgetOk
    ) {
    }
}
