package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalSlopePlanV2;

import java.util.Map;
import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-08 continental-slope foundation profile. */
public final class ContinentalSlopeGeneratorV2 {
    public static final String VERSION = "foundation-continental-slope-fixed-v1";

    private final ContinentalSlopePlanV2 plan;
    private final long axisMin;
    private final long axisMax;
    private final boolean seawardIncreasing;

    public ContinentalSlopeGeneratorV2(ContinentalSlopePlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        long[] extent = axisExtent(plan);
        axisMin = extent[0];
        axisMax = extent[1];
        // Default transect: landward west → seaward east (depth increases with X).
        seawardIncreasing = true;
    }

    public ContinentalSlopePlanV2 plan() {
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
            throw new FoundationSliceException("v2.continental-slope-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        if (!BathymetryFixedMathV2.contains(plan.rings(), px, pz)) {
            return BathymetrySampleV2.outside(plan.waterLevel());
        }
        long span = Math.max(1L, Math.subtractExact(axisMax, axisMin));
        long fromLandward = seawardIncreasing
                ? Math.subtractExact(px, axisMin)
                : Math.subtractExact(axisMax, px);
        long tMillionths = Math.floorDiv(
                Math.multiplyExact(Math.max(0L, fromLandward), TerrainIntentV2.FIXED_SCALE), span);
        if (tMillionths > TerrainIntentV2.FIXED_SCALE) {
            tMillionths = TerrainIntentV2.FIXED_SCALE;
        }
        int depthSpan = plan.selectedLowerDepthBlocksBelowSea() - plan.selectedUpperDepthBlocksBelowSea();
        int depth = plan.selectedUpperDepthBlocksBelowSea()
                + Math.toIntExact(Math.floorDiv(
                        Math.multiplyExact((long) depthSpan, tMillionths),
                        TerrainIntentV2.FIXED_SCALE));
        long slopeMillionths = Math.floorDiv(
                Math.multiplyExact((long) depthSpan, TerrainIntentV2.FIXED_SCALE),
                Math.max(1L, plan.selectedSlopeWidthBlocks()));
        int coast = Math.toIntExact(Math.floorDiv(Math.max(0L, fromLandward), TerrainIntentV2.FIXED_SCALE));
        int floorY = plan.waterLevel() - depth;
        int fluidTop = floorY < plan.waterLevel() ? plan.waterLevel() : floorY;
        return new BathymetrySampleV2(
                depth,
                Math.toIntExact(Math.min(Integer.MAX_VALUE, slopeMillionths)),
                coast,
                1,
                floorY,
                fluidTop);
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

    public ContinentalSlopeMetrics evaluate() {
        long owned = 0L;
        boolean depthFinite = true;
        boolean fluidSolidOk = true;
        boolean monotoneOk = true;
        int previousDepth = -1;
        int previousX = -1;
        for (int z = 0; z < length(); z++) {
            previousDepth = -1;
            previousX = -1;
            for (int x = 0; x < width(); x++) {
                BathymetrySampleV2 sample = sampleAt(x, z);
                if (!sample.owned()) {
                    continue;
                }
                owned++;
                if (sample.depthBlocksBelowSea() < plan.selectedUpperDepthBlocksBelowSea()
                        || sample.depthBlocksBelowSea() > plan.selectedLowerDepthBlocksBelowSea()) {
                    depthFinite = false;
                }
                if (!BathymetryChecksumSupportV2.fluidSolidConflictFree(sample, plan.waterLevel())) {
                    fluidSolidOk = false;
                }
                if (previousX >= 0 && x == previousX + 1
                        && sample.depthBlocksBelowSea() < previousDepth) {
                    monotoneOk = false;
                }
                previousDepth = sample.depthBlocksBelowSea();
                previousX = x;
            }
        }
        boolean widthOk = owned > 0;
        return new ContinentalSlopeMetrics(
                depthFinite && owned > 0,
                monotoneOk && owned > 0,
                widthOk,
                fluidSolidOk,
                plan.supportRadiusXZ() <= 64
                        && plan.estimatedRasterWorkUnits()
                        <= ContinentalSlopePlanV2.MAXIMUM_RASTER_WORK_UNITS);
    }

    /** Force non-monotone sampling for negative tests without allocating 3D arrays. */
    public BathymetrySampleV2 sampleAtInverted(int x, int z) {
        BathymetrySampleV2 sample = sampleAt(x, z);
        if (!sample.owned()) {
            return sample;
        }
        int inverted = plan.selectedLowerDepthBlocksBelowSea()
                + plan.selectedUpperDepthBlocksBelowSea() - sample.depthBlocksBelowSea();
        int floorY = plan.waterLevel() - inverted;
        return new BathymetrySampleV2(
                inverted,
                sample.slopeMillionths(),
                sample.coastDistanceBlocks(),
                1,
                floorY,
                floorY < plan.waterLevel() ? plan.waterLevel() : floorY);
    }

    private static long[] axisExtent(ContinentalSlopePlanV2 plan) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (var ring : plan.rings()) {
            for (var vertex : ring.vertices()) {
                min = Math.min(min, vertex.xMillionths());
                max = Math.max(max, vertex.xMillionths());
            }
        }
        return new long[] {min, max};
    }

    public record ContinentalSlopeMetrics(
            boolean depthFinite,
            boolean monotoneOk,
            boolean widthOk,
            boolean fluidSolidConflictFree,
            boolean budgetOk
    ) {
    }
}
