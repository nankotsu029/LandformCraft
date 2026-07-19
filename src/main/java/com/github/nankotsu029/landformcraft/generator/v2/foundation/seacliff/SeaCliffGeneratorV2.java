package com.github.nankotsu029.landformcraft.generator.v2.foundation.seacliff;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeaCliffPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-06 sea-cliff foundation profile. */
public final class SeaCliffGeneratorV2 {
    public static final String VERSION = "foundation-sea-cliff-fixed-v1";

    private final SeaCliffPlanV2 plan;
    private final long faceRadius;
    private final long talusOuterRadius;
    private final long notchOuterRadius;

    public SeaCliffGeneratorV2(SeaCliffPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        faceRadius = Math.multiplyExact(
                (long) plan.selectedSupportHalfExtentXZBlocks(), TerrainIntentV2.FIXED_SCALE);
        talusOuterRadius = Math.multiplyExact(
                (long) plan.selectedSupportHalfExtentXZBlocks() + plan.selectedTalusWidthBlocks(),
                TerrainIntentV2.FIXED_SCALE);
        notchOuterRadius = Math.multiplyExact(
                (long) plan.selectedSupportHalfExtentXZBlocks() + plan.selectedNotchDepthBlocks(),
                TerrainIntentV2.FIXED_SCALE);
    }

    public SeaCliffPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public SeaCliffSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.sea-cliff-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long distance = nearestCenterlineDistance(px, pz);
        boolean face = distance <= faceRadius;
        boolean talus = distance > faceRadius && distance <= talusOuterRadius;
        boolean notch = isSeawardOfFace(px, pz) && distance > faceRadius
                && distance <= notchOuterRadius;
        if (!face && !talus) {
            return SeaCliffSample.outside();
        }
        int elevation = plan.waterLevel()
                + (face ? plan.selectedCliffHeightBlocks() : plan.selectedCliffHeightBlocks() / 3);
        return new SeaCliffSample(face ? 1 : 0, talus ? 1 : 0, notch ? 1 : 0, elevation);
    }

    public SeaCliffMetrics evaluate() {
        long faceCells = 0L;
        long talusCells = 0L;
        long notchCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                SeaCliffSample sample = sampleAt(x, z);
                faceCells += sample.faceMask();
                talusCells += sample.talusMask();
                notchCells += sample.notchMask();
            }
        }
        return new SeaCliffMetrics(faceCells > 0, talusCells > 0, notchCells > 0);
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
                    SeaCliffSample sample = sampleAt(px, pz);
                    if (!sample.active()) {
                        return 0;
                    }
                    return Math.toIntExact(Math.multiplyExact(
                            (long) sample.elevationBlocks(), TerrainIntentV2.FIXED_SCALE));
                });
    }

    private boolean isSeawardOfFace(long px, long pz) {
        SeaCliffPlanV2.CenterlinePoint mid = plan.centerline().get(plan.centerline().size() / 2);
        return switch (plan.seawardSide()) {
            case WEST -> px < mid.xMillionths();
            case EAST -> px > mid.xMillionths();
            case NORTH -> pz < mid.zMillionths();
            case SOUTH -> pz > mid.zMillionths();
        };
    }

    private long nearestCenterlineDistance(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (int i = 1; i < plan.centerline().size(); i++) {
            SeaCliffPlanV2.CenterlinePoint a = plan.centerline().get(i - 1);
            SeaCliffPlanV2.CenterlinePoint b = plan.centerline().get(i);
            best = Math.min(best, distanceToSegment(px, pz,
                    a.xMillionths(), a.zMillionths(), b.xMillionths(), b.zMillionths()));
        }
        return best;
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = bx - ax;
        long dz = bz - az;
        if (dx == 0L && dz == 0L) {
            return SeaCliffFixedMathV2.hypot(px - ax, pz - az);
        }
        long tNumerator = Math.addExact(
                Math.multiplyExact(px - ax, dx),
                Math.multiplyExact(pz - az, dz));
        long tDenominator = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long t = Math.max(0L, Math.min(tDenominator, tNumerator));
        long projX = ax + dx * t / tDenominator;
        long projZ = az + dz * t / tDenominator;
        return SeaCliffFixedMathV2.hypot(px - projX, pz - projZ);
    }

    public record SeaCliffSample(
            int faceMask,
            int talusMask,
            int notchMask,
            int elevationBlocks
    ) {
        public static SeaCliffSample outside() {
            return new SeaCliffSample(0, 0, 0, 0);
        }

        public boolean active() {
            return faceMask == 1 || talusMask == 1;
        }
    }

    public record SeaCliffMetrics(
            boolean facePresent,
            boolean talusPresent,
            boolean notchPresent
    ) {
    }
}
