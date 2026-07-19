package com.github.nankotsu029.landformcraft.generator.v2.foundation.floodplain;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FloodplainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-05 floodplain foundation profile. */
public final class FloodplainGeneratorV2 {
    public static final String VERSION = "foundation-floodplain-fixed-v1";

    private final FloodplainPlanV2 plan;
    private final String seedNamespace;

    public FloodplainGeneratorV2(FloodplainPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.seedNamespace = "floodplain:" + plan.featureId();
    }

    public FloodplainPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public FloodplainSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.floodplain-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        if (!FloodplainFixedMathV2.contains(plan.rings(), px, pz)) {
            return FloodplainSample.outside();
        }
        int micro = Math.toIntExact(
                1L + FloodplainFixedMathV2.cellHash(seedNamespace, x, z)
                        * plan.selectedMicroReliefBlocks() / 1_000_000L);
        if (micro > plan.selectedMicroReliefBlocks()) {
            micro = plan.selectedMicroReliefBlocks();
        }
        return new FloodplainSample(1, micro, plan.groundwaterHandoffDepthBlocks(), 1);
    }

    public FloodplainMetrics evaluate() {
        long active = 0L;
        long microRelief = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                FloodplainSample sample = sampleAt(x, z);
                if (sample.active()) {
                    active++;
                    if (sample.microReliefBlocks() > 0) {
                        microRelief++;
                    }
                }
            }
        }
        return new FloodplainMetrics(
                active > 0 && microRelief > 0,
                plan.groundwaterHandoffDepthBlocks() >= 1 && active > 0,
                active);
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
                    FloodplainSample sample = sampleAt(px, pz);
                    if (!sample.active()) {
                        return 0;
                    }
                    return Math.toIntExact(Math.multiplyExact(
                            (long) sample.microReliefBlocks(), TerrainIntentV2.FIXED_SCALE));
                });
    }

    public record FloodplainSample(
            int activeMask,
            int microReliefBlocks,
            int groundwaterHandoffDepthBlocks,
            int solidOwnershipMask
    ) {
        public static FloodplainSample outside() {
            return new FloodplainSample(0, 0, 0, 0);
        }

        public boolean active() {
            return activeMask == 1;
        }
    }

    public record FloodplainMetrics(
            boolean microReliefPresent,
            boolean groundwaterHandoffPresent,
            long activeCellCount
    ) {
    }
}
