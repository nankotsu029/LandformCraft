package com.github.nankotsu029.landformcraft.generator.v2.foundation.cone;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.island.IslandFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.VolcanicConePlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-07 volcanic-cone foundation profile. */
public final class VolcanicConeGeneratorV2 {
    public static final String VERSION = "foundation-volcanic-cone-fixed-v1";

    private final VolcanicConePlanV2 plan;
    private final long baseRadius;
    private final long craterRadius;
    private final String seedNamespace;

    public VolcanicConeGeneratorV2(VolcanicConePlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        baseRadius = Math.multiplyExact((long) plan.selectedBaseRadiusBlocks(), TerrainIntentV2.FIXED_SCALE);
        craterRadius = Math.multiplyExact((long) plan.selectedCraterRadiusBlocks(), TerrainIntentV2.FIXED_SCALE);
        seedNamespace = "volcanic-cone:" + plan.featureId();
    }

    public VolcanicConePlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public ConeSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.volcanic-cone-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long dx = px - plan.centerXMillionths();
        long dz = pz - plan.centerZMillionths();
        long distance = IslandFixedMathV2.hypot(dx, dz);
        if (distance > baseRadius) {
            return ConeSample.outside();
        }
        boolean crater = distance <= craterRadius;
        boolean drainage = !crater && IslandFixedMathV2.radialHash(seedNamespace, dx, dz)
                < plan.selectedRadialDrainageMillionths();
        int elevation;
        if (crater) {
            elevation = plan.waterLevel() + plan.selectedSummitHeightBlocksAboveSea()
                    - plan.selectedCraterFloorDepthBlocks();
        } else {
            long remain = Math.max(0L, baseRadius - distance);
            elevation = plan.waterLevel() + Math.toIntExact(
                    remain * plan.selectedSummitHeightBlocksAboveSea() / Math.max(1L, baseRadius));
        }
        return new ConeSample(1, crater ? 1 : 0, drainage ? 1 : 0, elevation);
    }

    public ConeMetrics evaluate() {
        long coneCells = 0L;
        long craterCells = 0L;
        long drainageCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                ConeSample sample = sampleAt(x, z);
                coneCells += sample.coneMask();
                craterCells += sample.craterMask();
                drainageCells += sample.drainageMask();
            }
        }
        return new ConeMetrics(
                coneCells > 0,
                craterCells > 0 && plan.selectedCraterRadiusBlocks() < plan.selectedBaseRadiusBlocks(),
                drainageCells > 0 || plan.selectedRadialDrainageMillionths() == 0L,
                plan.supportRadiusXZ() <= 64);
    }

    public record ConeSample(
            int coneMask,
            int craterMask,
            int drainageMask,
            int elevationBlocks
    ) {
        public static ConeSample outside() {
            return new ConeSample(0, 0, 0, 0);
        }

        public boolean active() {
            return coneMask == 1;
        }
    }

    public record ConeMetrics(
            boolean coneMassPresent,
            boolean craterContained,
            boolean radialDrainagePresent,
            boolean supportBudgetOk
    ) {
    }
}
