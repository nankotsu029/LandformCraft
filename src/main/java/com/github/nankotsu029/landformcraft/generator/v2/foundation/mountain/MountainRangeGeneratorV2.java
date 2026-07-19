package com.github.nankotsu029.landformcraft.generator.v2.foundation.mountain;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MountainRangePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-03 mountain-range foundation profile. */
public final class MountainRangeGeneratorV2 {
    public static final String VERSION = "foundation-mountain-range-fixed-v1";

    private final MountainRangePlanV2 plan;
    private final long ridgeRadius;
    private final long bodyRadius;

    public MountainRangeGeneratorV2(MountainRangePlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        ridgeRadius = Math.multiplyExact((long) plan.selectedRidgeHalfWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
        bodyRadius = Math.multiplyExact((long) plan.supportRadiusXZ(), TerrainIntentV2.FIXED_SCALE);
    }

    public MountainRangePlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public MountainSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.mountain-range-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        NearestRidge nearest = nearestRidge(px, pz);
        boolean ridge = nearest.distance() <= ridgeRadius;
        boolean peak = nearestPeakDistance(px, pz) <= ridgeRadius / 2L;
        boolean saddle = nearestSaddleDistance(px, pz) <= ridgeRadius / 2L;
        boolean spur = nearestSpurDistance(px, pz) <= ridgeRadius;
        boolean pass = nearestPassDistance(px, pz) <= ridgeRadius / 2L;
        boolean foothillBody = nearest.distance() <= bodyRadius;
        if (!ridge && !peak && !saddle && !spur && !pass && !foothillBody) {
            return MountainSample.outside();
        }
        long falloff = bodyRadius == 0L ? 0L : nearest.distance() * TerrainIntentV2.FIXED_SCALE / bodyRadius;
        long relief = Math.multiplyExact((long) plan.selectedMaxReliefBlocks(), TerrainIntentV2.FIXED_SCALE);
        long delta = relief - relief * falloff / TerrainIntentV2.FIXED_SCALE;
        int elevationBlocks = plan.waterLevel()
                + Math.toIntExact(delta / TerrainIntentV2.FIXED_SCALE);
        return new MountainSample(
                ridge || foothillBody ? 1 : 0, peak ? 1 : 0, saddle ? 1 : 0, spur ? 1 : 0, pass ? 1 : 0,
                elevationBlocks);
    }

    public MountainMetrics evaluate() {
        long ridgeCells = 0L;
        long peakCells = 0L;
        long passCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                MountainSample sample = sampleAt(x, z);
                ridgeCells += sample.ridgeMask();
                peakCells += sample.peakMask();
                passCells += sample.passMask();
            }
        }
        boolean ridgeGraphOk = true;
        long previous = -1L;
        for (MountainRangePlanV2.RidgePoint point : plan.ridgePoints()) {
            if (point.arcLengthMillionths() <= previous) {
                ridgeGraphOk = false;
                break;
            }
            previous = point.arcLengthMillionths();
        }
        boolean peakPassBudgetOk = plan.peaks().size() >= 2
                && plan.saddles().size() == plan.peaks().size() - 1
                && plan.passes().size() <= plan.peaks().size() - 1
                && (plan.passes().isEmpty() || passCells > 0)
                && peakCells > 0;
        return new MountainMetrics(ridgeGraphOk && ridgeCells > 0, peakPassBudgetOk);
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
                    MountainSample sample = sampleAt(px, pz);
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
            MountainRangePlanV2.RidgePoint a = plan.ridgePoints().get(i - 1);
            MountainRangePlanV2.RidgePoint b = plan.ridgePoints().get(i);
            long distance = distanceToSegment(px, pz, a.xMillionths(), a.zMillionths(),
                    b.xMillionths(), b.zMillionths());
            best = Math.min(best, distance);
        }
        return new NearestRidge(best);
    }

    private long nearestPeakDistance(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (MountainRangePlanV2.Peak peak : plan.peaks()) {
            best = Math.min(best, MountainRangeFixedMathV2.hypot(px - peak.xMillionths(), pz - peak.zMillionths()));
        }
        return best;
    }

    private long nearestSaddleDistance(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (MountainRangePlanV2.Saddle saddle : plan.saddles()) {
            best = Math.min(best,
                    MountainRangeFixedMathV2.hypot(px - saddle.xMillionths(), pz - saddle.zMillionths()));
        }
        return best;
    }

    private long nearestSpurDistance(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (MountainRangePlanV2.Spur spur : plan.spurs()) {
            best = Math.min(best,
                    MountainRangeFixedMathV2.hypot(px - spur.xMillionths(), pz - spur.zMillionths()));
        }
        return best;
    }

    private long nearestPassDistance(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (MountainRangePlanV2.Pass pass : plan.passes()) {
            best = Math.min(best,
                    MountainRangeFixedMathV2.hypot(px - pass.xMillionths(), pz - pass.zMillionths()));
        }
        return best;
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = bx - ax;
        long dz = bz - az;
        if (dx == 0L && dz == 0L) {
            return MountainRangeFixedMathV2.hypot(px - ax, pz - az);
        }
        long tNumerator = Math.addExact(
                Math.multiplyExact(px - ax, dx),
                Math.multiplyExact(pz - az, dz));
        long tDenominator = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long t = Math.max(0L, Math.min(tDenominator, tNumerator));
        long projX = ax + dx * t / tDenominator;
        long projZ = az + dz * t / tDenominator;
        return MountainRangeFixedMathV2.hypot(px - projX, pz - projZ);
    }

    public record MountainSample(
            int ridgeMask,
            int peakMask,
            int saddleMask,
            int spurMask,
            int passMask,
            int elevationBlocks
    ) {
        public static MountainSample outside() {
            return new MountainSample(0, 0, 0, 0, 0, 0);
        }

        public boolean active() {
            return ridgeMask == 1 || peakMask == 1 || saddleMask == 1 || spurMask == 1 || passMask == 1;
        }
    }

    public record MountainMetrics(boolean ridgeGraphOk, boolean peakPassBudgetOk) {
    }

    private record NearestRidge(long distance) {
    }
}
