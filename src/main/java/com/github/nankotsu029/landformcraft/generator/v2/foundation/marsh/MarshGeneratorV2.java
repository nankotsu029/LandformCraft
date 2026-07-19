package com.github.nankotsu029.landformcraft.generator.v2.foundation.marsh;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MarshPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-05 marsh foundation profile. */
public final class MarshGeneratorV2 {
    public static final String VERSION = "foundation-marsh-fixed-v1";

    private final MarshPlanV2 plan;
    private final String seedNamespace;

    public MarshGeneratorV2(MarshPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.seedNamespace = "marsh:" + plan.featureId();
    }

    public MarshPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public MarshSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.marsh-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        if (!MarshFixedMathV2.contains(plan.rings(), px, pz)) {
            return MarshSample.outside();
        }
        long hash = MarshFixedMathV2.cellHash(seedNamespace, x, z);
        boolean openWater = hash < plan.selectedOpenWaterShareMillionths();
        int micro = Math.toIntExact(
                1L + MarshFixedMathV2.cellHash(seedNamespace + ":relief", x, z)
                        * plan.selectedMicroReliefBlocks() / 1_000_000L);
        if (micro > plan.selectedMicroReliefBlocks()) {
            micro = plan.selectedMicroReliefBlocks();
        }
        // Fluid owns open water; solid owns vegetated marsh cells. Never both.
        return new MarshSample(
                1,
                openWater ? 1 : 0,
                micro,
                plan.selectedWetnessMillionths(),
                plan.selectedHydroperiodBlocks(),
                openWater ? 1 : 0,
                openWater ? 0 : 1);
    }

    public MarshMetrics evaluate() {
        long active = 0L;
        long openWater = 0L;
        long solid = 0L;
        long fluid = 0L;
        long both = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                MarshSample sample = sampleAt(x, z);
                if (!sample.active()) {
                    continue;
                }
                active++;
                if (sample.openWaterMask() == 1) {
                    openWater++;
                }
                if (sample.solidOwnershipMask() == 1) {
                    solid++;
                }
                if (sample.fluidOwnershipMask() == 1) {
                    fluid++;
                }
                if (sample.solidOwnershipMask() == 1 && sample.fluidOwnershipMask() == 1) {
                    both++;
                }
            }
        }
        boolean wetnessOk = plan.selectedWetnessMillionths() >= 200_000L && active > 0;
        boolean openWaterOk = active > 0
                && openWater * 1_000_000L <= plan.selectedOpenWaterShareMillionths() * active
                        + active;
        boolean ownershipOk = both == 0L && solid + fluid == active && active > 0;
        return new MarshMetrics(wetnessOk, openWaterOk && openWater >= 0, ownershipOk, active, openWater);
    }

    public SurfaceFoundationMergeCompilerV2.OwnerLayer toOwnerLayer(
            SurfaceFoundationPlanV2.OwnerDescriptor owner
    ) {
        Objects.requireNonNull(owner, "owner");
        return new SurfaceFoundationMergeCompilerV2.OwnerLayer(
                owner,
                packed -> {
                    long value = packed;
                    int px = (int) value;
                    int pz = (int) (value >>> 32);
                    return sampleAt(px, pz).active();
                },
                (px, pz) -> {
                    MarshSample sample = sampleAt(px, pz);
                    if (!sample.active()) {
                        return 0;
                    }
                    return Math.toIntExact(sample.wetnessMillionths());
                });
    }

    public record MarshSample(
            int activeMask,
            int openWaterMask,
            int microReliefBlocks,
            long wetnessMillionths,
            int hydroperiodBlocks,
            int fluidOwnershipMask,
            int solidOwnershipMask
    ) {
        public static MarshSample outside() {
            return new MarshSample(0, 0, 0, 0L, 0, 0, 0);
        }

        public boolean active() {
            return activeMask == 1;
        }
    }

    public record MarshMetrics(
            boolean wetnessOk,
            boolean openWaterTransitionOk,
            boolean fluidSolidOwnershipOk,
            long activeCellCount,
            long openWaterCellCount
    ) {
    }
}
