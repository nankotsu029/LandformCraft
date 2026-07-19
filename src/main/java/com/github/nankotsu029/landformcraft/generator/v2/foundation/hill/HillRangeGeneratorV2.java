package com.github.nankotsu029.landformcraft.generator.v2.foundation.hill;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.hill.HillRangeFixedMathV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.HillRangePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-02 hill-range foundation profile. */
public final class HillRangeGeneratorV2 {
    public static final String VERSION = "foundation-hill-range-fixed-v1";

    private final HillRangePlanV2 plan;
    private final long ridgeRadius;

    public HillRangeGeneratorV2(HillRangePlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        ridgeRadius = Math.multiplyExact((long) plan.selectedRidgeHalfWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
    }

    public HillRangePlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public HillSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.hill-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        NearestRidge nearest = nearestRidge(px, pz);
        boolean ridge = nearest.distance() <= ridgeRadius;
        boolean saddle = nearestSaddleDistance(px, pz) <= ridgeRadius / 2L;
        if (!ridge && !saddle) {
            return HillSample.outside();
        }
        long falloff = ridgeRadius == 0L ? 0L : nearest.distance() * TerrainIntentV2.FIXED_SCALE / ridgeRadius;
        long relief = Math.multiplyExact((long) plan.selectedMaxReliefBlocks(), TerrainIntentV2.FIXED_SCALE);
        long delta = relief - relief * falloff / TerrainIntentV2.FIXED_SCALE;
        int elevationBlocks = plan.waterLevel()
                + Math.toIntExact(delta / TerrainIntentV2.FIXED_SCALE);
        return new HillSample(ridge ? 1 : 0, saddle ? 1 : 0, elevationBlocks);
    }

    public HillMetrics evaluate() {
        long ridgeCells = 0L;
        long saddleCells = 0L;
        long reliefCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                HillSample sample = sampleAt(x, z);
                ridgeCells += sample.ridgeMask();
                saddleCells += sample.saddleMask();
                if (sample.active() && sample.elevationBlocks() > plan.waterLevel()) {
                    reliefCells++;
                }
            }
        }
        boolean ridgeContinuous = true;
        long previous = -1L;
        for (HillRangePlanV2.RidgePoint point : plan.ridgePoints()) {
            if (point.arcLengthMillionths() <= previous) {
                ridgeContinuous = false;
                break;
            }
            previous = point.arcLengthMillionths();
        }
        boolean saddleBudgetOk = plan.saddles().size() == plan.ridgeStations().size() - 1;
        return new HillMetrics(
                ridgeContinuous && ridgeCells > 0,
                saddleBudgetOk && saddleCells > 0,
                plan.selectedMaxReliefBlocks() > 0 && reliefCells > 0);
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
                    HillSample sample = sampleAt(px, pz);
                    if (!sample.active()) {
                        return 0;
                    }
                    return Math.toIntExact(Math.multiplyExact(
                            (long) sample.elevationBlocks(), TerrainIntentV2.FIXED_SCALE));
                });
    }

    private NearestRidge nearestRidge(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (int i = 1; i < plan.ridgePoints().size(); i++) {
            HillRangePlanV2.RidgePoint a = plan.ridgePoints().get(i - 1);
            HillRangePlanV2.RidgePoint b = plan.ridgePoints().get(i);
            long distance = distanceToSegment(px, pz, a.xMillionths(), a.zMillionths(),
                    b.xMillionths(), b.zMillionths());
            best = Math.min(best, distance);
        }
        return new NearestRidge(best);
    }

    private long nearestSaddleDistance(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (HillRangePlanV2.Saddle saddle : plan.saddles()) {
            long dx = px - saddle.xMillionths();
            long dz = pz - saddle.zMillionths();
            best = Math.min(best, HillRangeFixedMathV2.hypot(dx, dz));
        }
        return best;
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = bx - ax;
        long dz = bz - az;
        if (dx == 0L && dz == 0L) {
            return HillRangeFixedMathV2.hypot(px - ax, pz - az);
        }
        long tNumerator = Math.addExact(
                Math.multiplyExact(px - ax, dx),
                Math.multiplyExact(pz - az, dz));
        long tDenominator = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long t = Math.max(0L, Math.min(tDenominator, tNumerator));
        long projX = ax + dx * t / tDenominator;
        long projZ = az + dz * t / tDenominator;
        return HillRangeFixedMathV2.hypot(px - projX, pz - projZ);
    }

    public record HillSample(int ridgeMask, int saddleMask, int elevationBlocks) {
        public static HillSample outside() {
            return new HillSample(0, 0, 0);
        }

        public boolean active() {
            return ridgeMask == 1 || saddleMask == 1;
        }
    }

    public record HillMetrics(
            boolean ridgeContinuous,
            boolean saddleBudgetOk,
            boolean reliefOk
    ) {
    }

    private record NearestRidge(long distance) {
    }
}
