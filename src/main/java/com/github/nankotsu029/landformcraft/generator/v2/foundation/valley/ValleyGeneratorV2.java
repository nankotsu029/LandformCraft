package com.github.nankotsu029.landformcraft.generator.v2.foundation.valley;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ValleyPlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-03 valley foundation profile. */
public final class ValleyGeneratorV2 {
    public static final String VERSION = "foundation-valley-fixed-v1";

    private final ValleyPlanV2 plan;
    private final long floorRadius;
    private final long shoulderOuter;

    public ValleyGeneratorV2(ValleyPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        floorRadius = Math.multiplyExact((long) plan.selectedFloorHalfWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
        shoulderOuter = Math.multiplyExact(
                (long) plan.selectedFloorHalfWidthBlocks() + plan.selectedShoulderWidthBlocks(),
                TerrainIntentV2.FIXED_SCALE);
    }

    public ValleyPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public ValleySample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.valley-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long distance = nearestThalwegDistance(px, pz);
        boolean floor = distance <= floorRadius;
        boolean shoulder = !floor && distance <= shoulderOuter;
        if (!floor && !shoulder) {
            return ValleySample.outside();
        }
        long depthMillionths = Math.multiplyExact(
                (long) plan.selectedMaxDepthBlocks(), TerrainIntentV2.FIXED_SCALE);
        long depth;
        if (floor) {
            depth = depthMillionths;
            if (plan.crossSection() == TerrainIntentV2.ValleyCrossSection.V_PROFILE && floorRadius > 0L) {
                depth = depthMillionths - depthMillionths * distance / floorRadius / 2L;
            }
        } else {
            long band = Math.max(1L, shoulderOuter - floorRadius);
            long fromFloor = distance - floorRadius;
            depth = depthMillionths / 2L - (depthMillionths / 2L) * fromFloor / band;
        }
        int elevationBlocks = plan.waterLevel()
                - Math.toIntExact(Math.max(0L, depth) / TerrainIntentV2.FIXED_SCALE);
        return new ValleySample(floor ? 1 : 0, shoulder ? 1 : 0, elevationBlocks);
    }

    public ValleyMetrics evaluate() {
        long floorCells = 0L;
        long shoulderCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                ValleySample sample = sampleAt(x, z);
                floorCells += sample.floorMask();
                shoulderCells += sample.shoulderMask();
            }
        }
        boolean thalwegOk = true;
        long previous = -1L;
        for (ValleyPlanV2.ThalwegPoint point : plan.thalwegPoints()) {
            if (point.arcLengthMillionths() <= previous) {
                thalwegOk = false;
                break;
            }
            previous = point.arcLengthMillionths();
        }
        return new ValleyMetrics(thalwegOk && floorCells > 0, shoulderCells > 0 || plan.selectedShoulderWidthBlocks() == 0);
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
                    ValleySample sample = sampleAt(px, pz);
                    if (!sample.active()) {
                        return 0;
                    }
                    return Math.toIntExact(Math.multiplyExact(
                            (long) sample.elevationBlocks(), TerrainIntentV2.FIXED_SCALE));
                });
    }

    private long nearestThalwegDistance(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (int i = 1; i < plan.thalwegPoints().size(); i++) {
            ValleyPlanV2.ThalwegPoint a = plan.thalwegPoints().get(i - 1);
            ValleyPlanV2.ThalwegPoint b = plan.thalwegPoints().get(i);
            best = Math.min(best, distanceToSegment(px, pz, a.xMillionths(), a.zMillionths(),
                    b.xMillionths(), b.zMillionths()));
        }
        return best;
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = bx - ax;
        long dz = bz - az;
        if (dx == 0L && dz == 0L) {
            return ValleyFixedMathV2.hypot(px - ax, pz - az);
        }
        long tNumerator = Math.addExact(
                Math.multiplyExact(px - ax, dx),
                Math.multiplyExact(pz - az, dz));
        long tDenominator = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long t = Math.max(0L, Math.min(tDenominator, tNumerator));
        long projX = ax + dx * t / tDenominator;
        long projZ = az + dz * t / tDenominator;
        return ValleyFixedMathV2.hypot(px - projX, pz - projZ);
    }

    public record ValleySample(int floorMask, int shoulderMask, int elevationBlocks) {
        public static ValleySample outside() {
            return new ValleySample(0, 0, 0);
        }

        public boolean active() {
            return floorMask == 1 || shoulderMask == 1;
        }
    }

    public record ValleyMetrics(boolean floorPresent, boolean shoulderPresent) {
    }
}
