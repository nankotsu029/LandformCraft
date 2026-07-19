package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;

import java.util.Map;
import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-08 ocean-basin foundation profile. */
public final class OceanBasinGeneratorV2 {
    public static final String VERSION = "foundation-ocean-basin-fixed-v1";

    private final OceanBasinPlanV2 plan;
    private final String seedNamespace;

    public OceanBasinGeneratorV2(OceanBasinPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.seedNamespace = "ocean-basin:" + plan.featureId();
    }

    public OceanBasinPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public BathymetrySampleV2 sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.ocean-basin-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        if (!BathymetryFixedMathV2.contains(plan.rings(), px, pz)) {
            return BathymetrySampleV2.outside(plan.waterLevel());
        }
        long relief = BathymetryFixedMathV2.cellHash(seedNamespace, 0, z)
                * plan.selectedFloorReliefBlocks() / 1_000_000L;
        int depth = Math.max(0, plan.selectedMaxDepthBlocksBelowSea() - Math.toIntExact(relief));
        // Coast distance remains X/Z aware; floor relief varies only with Z so seaward (X) depth stays monotone.
        int coast = BathymetryFixedMathV2.coastDistanceBlocks(plan.rings(), px, pz);
        int floorY = plan.waterLevel() - depth;
        int fluidTop = floorY < plan.waterLevel() ? plan.waterLevel() : floorY;
        return new BathymetrySampleV2(depth, 0, coast, 1, floorY, fluidTop);
    }

    public Map<BathymetrySampleV2.BathymetryField, String> fieldChecksums() {
        return BathymetryChecksumSupportV2.fieldChecksumsFrom(VERSION, width(), length(), this::sampleAt);
    }

    public Map<BathymetrySampleV2.BathymetryField, String> fieldChecksumsFrom(
            BathymetryChecksumSupportV2.CellSource source
    ) {
        return BathymetryChecksumSupportV2.fieldChecksumsFrom(VERSION, width(), length(), source);
    }

    public String underwaterColumnExportChecksum() {
        return BathymetryChecksumSupportV2.underwaterColumnExportChecksum(
                VERSION, width(), length(), plan.waterLevel(), plan.minY(), this::sampleAt);
    }

    public OceanBasinMetrics evaluate() {
        long owned = 0L;
        boolean depthFinite = true;
        boolean fluidSolidOk = true;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                BathymetrySampleV2 sample = sampleAt(x, z);
                if (!sample.owned()) {
                    continue;
                }
                owned++;
                if (sample.depthBlocksBelowSea() < 0
                        || sample.depthBlocksBelowSea() > plan.selectedMaxDepthBlocksBelowSea()) {
                    depthFinite = false;
                }
                if (!BathymetryChecksumSupportV2.fluidSolidConflictFree(sample, plan.waterLevel())) {
                    fluidSolidOk = false;
                }
            }
        }
        return new OceanBasinMetrics(
                depthFinite && owned > 0,
                owned > 0,
                fluidSolidOk,
                plan.supportRadiusXZ() <= 64
                        && plan.estimatedRasterWorkUnits() <= OceanBasinPlanV2.MAXIMUM_RASTER_WORK_UNITS);
    }

    public record OceanBasinMetrics(
            boolean depthFinite,
            boolean widthOk,
            boolean fluidSolidConflictFree,
            boolean budgetOk
    ) {
    }
}
