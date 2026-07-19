package com.github.nankotsu029.landformcraft.generator.v2.foundation.plain;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.plain.PlainFixedMathV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-02 plain foundation profile. */
public final class PlainGeneratorV2 {
    public static final String VERSION = "foundation-plain-fixed-v1";

    private final PlainPlanV2 plan;
    private final String seedNamespace;

    public PlainGeneratorV2(PlainPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.seedNamespace = "plain:" + plan.featureId();
    }

    public PlainPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public PlainSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.plain-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        if (!PlainFixedMathV2.contains(plan.rings(), px, pz)) {
            return PlainSample.outside();
        }
        int microVariation = microReliefVariationBlocks(x, z);
        int elevationBlocks = plan.baseElevationBlocks() + microVariation;
        return new PlainSample(
                1,
                elevationBlocks,
                microVariation,
                plan.groundwaterHandoffDepthBlocks());
    }

    public PlainMetrics evaluate() {
        long activeCells = 0L;
        long microReliefCells = 0L;
        boolean groundwater = plan.groundwaterHandoffDepthBlocks() >= 1;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                PlainSample sample = sampleAt(x, z);
                if (sample.active()) {
                    activeCells++;
                    if (sample.microReliefBlocks() > 0) {
                        microReliefCells++;
                    }
                }
            }
        }
        return new PlainMetrics(
                activeCells > 0 && microReliefCells > 0,
                groundwater && activeCells > 0,
                activeCells);
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
                    PlainSample sample = sampleAt(px, pz);
                    if (!sample.active()) {
                        return 0;
                    }
                    return Math.toIntExact(Math.multiplyExact(
                            (long) sample.elevationBlocks(), TerrainIntentV2.FIXED_SCALE));
                });
    }

    private int microReliefVariationBlocks(int x, int z) {
        long hash = PlainFixedMathV2.cellHash(seedNamespace, x, z);
        int span = plan.maximumMicroReliefBlocks() - plan.minimumMicroReliefBlocks();
        if (span <= 0) {
            return plan.selectedMicroReliefBlocks();
        }
        return plan.minimumMicroReliefBlocks()
                + Math.toIntExact(hash * (span + 1L) / 1_000_000L);
    }

    public record PlainSample(
            int activeMask,
            int elevationBlocks,
            int microReliefBlocks,
            int groundwaterHandoffDepthBlocks
    ) {
        public static PlainSample outside() {
            return new PlainSample(0, 0, 0, 0);
        }

        public boolean active() {
            return activeMask == 1;
        }
    }

    public record PlainMetrics(
            boolean microReliefPresent,
            boolean groundwaterHandoffPresent,
            long activeCellCount
    ) {
    }
}
