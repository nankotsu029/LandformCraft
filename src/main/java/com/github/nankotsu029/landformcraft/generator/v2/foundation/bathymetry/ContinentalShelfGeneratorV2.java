package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalShelfPlanV2;

import java.util.Map;
import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-08 continental-shelf foundation profile. */
public final class ContinentalShelfGeneratorV2 {
    public static final String VERSION = "foundation-continental-shelf-fixed-v1";

    private final ContinentalShelfPlanV2 plan;
    private final long landwardMin;
    private final long landwardMax;
    private final boolean alongX;

    public ContinentalShelfGeneratorV2(ContinentalShelfPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        alongX = plan.seawardSide() == TerrainIntentV2.Edge.EAST
                || plan.seawardSide() == TerrainIntentV2.Edge.WEST;
        long[] extent = axisExtent(plan);
        landwardMin = extent[0];
        landwardMax = extent[1];
    }

    public ContinentalShelfPlanV2 plan() {
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
            throw new FoundationSliceException("v2.continental-shelf-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        if (!BathymetryFixedMathV2.contains(plan.rings(), px, pz)) {
            return BathymetrySampleV2.outside(plan.waterLevel());
        }
        long axis = alongX ? px : pz;
        long span = Math.max(1L, Math.subtractExact(landwardMax, landwardMin));
        long fromLandward;
        if (plan.seawardSide() == TerrainIntentV2.Edge.EAST
                || plan.seawardSide() == TerrainIntentV2.Edge.SOUTH) {
            fromLandward = Math.subtractExact(axis, landwardMin);
        } else {
            fromLandward = Math.subtractExact(landwardMax, axis);
        }
        int coastDistance = Math.toIntExact(Math.floorDiv(Math.max(0L, fromLandward),
                TerrainIntentV2.FIXED_SCALE));
        int depth = Math.max(1, Math.min(plan.selectedShelfDepthBlocksBelowSea(),
                1 + coastDistance * plan.selectedShelfDepthBlocksBelowSea()
                        / Math.max(1, plan.selectedShelfWidthBlocks())));
        long slopeMillionths = Math.floorDiv(
                Math.multiplyExact((long) plan.selectedShelfDepthBlocksBelowSea(),
                        TerrainIntentV2.FIXED_SCALE),
                Math.max(1L, plan.selectedShelfWidthBlocks()));
        int floorY = plan.waterLevel() - depth;
        int fluidTop = floorY < plan.waterLevel() ? plan.waterLevel() : floorY;
        return new BathymetrySampleV2(
                depth,
                Math.toIntExact(Math.min(Integer.MAX_VALUE, slopeMillionths)),
                coastDistance,
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

    public ContinentalShelfMetrics evaluate() {
        long owned = 0L;
        int maxCoast = 0;
        boolean depthFinite = true;
        boolean fluidSolidOk = true;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                BathymetrySampleV2 sample = sampleAt(x, z);
                if (!sample.owned()) {
                    continue;
                }
                owned++;
                maxCoast = Math.max(maxCoast, sample.coastDistanceBlocks());
                if (sample.depthBlocksBelowSea() < 0
                        || sample.depthBlocksBelowSea() > plan.selectedShelfDepthBlocksBelowSea()) {
                    depthFinite = false;
                }
                if (!BathymetryChecksumSupportV2.fluidSolidConflictFree(sample, plan.waterLevel())) {
                    fluidSolidOk = false;
                }
            }
        }
        boolean widthOk = owned > 0 && maxCoast + 1 >= plan.selectedShelfWidthBlocks() / 4;
        return new ContinentalShelfMetrics(
                depthFinite && owned > 0,
                widthOk,
                fluidSolidOk,
                plan.supportRadiusXZ() <= 64
                        && plan.estimatedRasterWorkUnits()
                        <= ContinentalShelfPlanV2.MAXIMUM_RASTER_WORK_UNITS);
    }

    private static long[] axisExtent(ContinentalShelfPlanV2 plan) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        boolean alongX = plan.seawardSide() == TerrainIntentV2.Edge.EAST
                || plan.seawardSide() == TerrainIntentV2.Edge.WEST;
        for (var ring : plan.rings()) {
            for (var vertex : ring.vertices()) {
                long value = alongX ? vertex.xMillionths() : vertex.zMillionths();
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        return new long[] {min, max};
    }

    public record ContinentalShelfMetrics(
            boolean depthFinite,
            boolean widthOk,
            boolean fluidSolidConflictFree,
            boolean budgetOk
    ) {
    }
}
