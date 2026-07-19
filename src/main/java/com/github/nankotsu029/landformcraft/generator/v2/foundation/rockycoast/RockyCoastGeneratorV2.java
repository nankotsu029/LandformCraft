package com.github.nankotsu029.landformcraft.generator.v2.foundation.rockycoast;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RockyCoastPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-06 rocky-coast foundation profile. */
public final class RockyCoastGeneratorV2 {
    public static final String VERSION = "foundation-rocky-coast-fixed-v1";

    private final RockyCoastPlanV2 plan;
    private final long shelfRadius;
    private final String seedNamespace;
    private final List<Long> channelArcs;

    public RockyCoastGeneratorV2(RockyCoastPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        shelfRadius = Math.multiplyExact(
                (long) plan.selectedRockShelfWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
        seedNamespace = "rocky-coast:" + plan.featureId();
        channelArcs = placeChannelArcs(plan);
    }

    public RockyCoastPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public RockyCoastSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.rocky-coast-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long distance = nearestCenterlineDistance(px, pz);
        if (distance > shelfRadius) {
            return RockyCoastSample.outside();
        }
        boolean channel = isChannelCell(px, pz, distance);
        boolean talusHandoff = distance > Math.subtractExact(shelfRadius,
                Math.multiplyExact((long) plan.talusHandoffDepthBlocks(), TerrainIntentV2.FIXED_SCALE))
                && distance <= shelfRadius;
        long hash = RockyCoastFixedMathV2.cellHash(seedNamespace, x, z);
        boolean exposure = hash < plan.selectedRockExposureMillionths();
        int elevation = plan.waterLevel() + (exposure ? 2 : 1);
        return new RockyCoastSample(1, exposure ? 1 : 0, channel ? 1 : 0, talusHandoff ? 1 : 0, elevation);
    }

    public RockyCoastMetrics evaluate() {
        long shelfCells = 0L;
        long exposureCells = 0L;
        long channelCells = 0L;
        long talusCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                RockyCoastSample sample = sampleAt(x, z);
                if (!sample.active()) {
                    continue;
                }
                shelfCells++;
                exposureCells += sample.exposureMask();
                channelCells += sample.channelMask();
                talusCells += sample.talusHandoffMask();
            }
        }
        return new RockyCoastMetrics(
                shelfCells > 0,
                exposureCells > 0 && plan.selectedRockExposureMillionths() >= 50_000L,
                channelCells > 0 && plan.selectedChannelCount() >= 1,
                talusCells > 0);
    }

    public SurfaceFoundationMergeCompilerV2.OwnerLayer toOwnerLayer(
            SurfaceFoundationPlanV2.OwnerDescriptor owner
    ) {
        Objects.requireNonNull(owner, "owner");
        return new SurfaceFoundationMergeCompilerV2.OwnerLayer(
                owner,
                packed -> true,
                (px, pz) -> {
                    RockyCoastSample sample = sampleAt(px, pz);
                    int elevation = sample.active()
                            ? sample.elevationBlocks()
                            : plan.waterLevel();
                    return Math.toIntExact(Math.multiplyExact(
                            (long) elevation, TerrainIntentV2.FIXED_SCALE));
                });
    }

    private long nearestCenterlineDistance(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (int i = 1; i < plan.centerline().size(); i++) {
            RockyCoastPlanV2.CenterlinePoint a = plan.centerline().get(i - 1);
            RockyCoastPlanV2.CenterlinePoint b = plan.centerline().get(i);
            best = Math.min(best, distanceToSegment(px, pz,
                    a.xMillionths(), a.zMillionths(), b.xMillionths(), b.zMillionths()));
        }
        return best;
    }

    private boolean isChannelCell(long px, long pz, long distance) {
        if (distance > shelfRadius / 2L) {
            return false;
        }
        long nearestArc = nearestArc(px, pz);
        long channelHalf = Math.multiplyExact(2L, TerrainIntentV2.FIXED_SCALE);
        for (long arc : channelArcs) {
            if (Math.abs(nearestArc - arc) <= channelHalf) {
                return true;
            }
        }
        return RockyCoastFixedMathV2.cellHash(seedNamespace + ":channel",
                (int) (px / TerrainIntentV2.FIXED_SCALE),
                (int) (pz / TerrainIntentV2.FIXED_SCALE)) < 40_000L;
    }

    private long nearestArc(long px, long pz) {
        long bestDistance = Long.MAX_VALUE;
        long bestArc = 0L;
        for (int i = 1; i < plan.centerline().size(); i++) {
            RockyCoastPlanV2.CenterlinePoint a = plan.centerline().get(i - 1);
            RockyCoastPlanV2.CenterlinePoint b = plan.centerline().get(i);
            long distance = distanceToSegment(px, pz,
                    a.xMillionths(), a.zMillionths(), b.xMillionths(), b.zMillionths());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestArc = (a.arcLengthMillionths() + b.arcLengthMillionths()) / 2L;
            }
        }
        return bestArc;
    }

    private static List<Long> placeChannelArcs(RockyCoastPlanV2 plan) {
        long total = plan.centerline().getLast().arcLengthMillionths();
        int count = plan.selectedChannelCount();
        List<Long> arcs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            arcs.add(total * (index + 1L) / (count + 1L));
        }
        return List.copyOf(arcs);
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = bx - ax;
        long dz = bz - az;
        if (dx == 0L && dz == 0L) {
            return RockyCoastFixedMathV2.hypot(px - ax, pz - az);
        }
        long tNumerator = Math.addExact(
                Math.multiplyExact(px - ax, dx),
                Math.multiplyExact(pz - az, dz));
        long tDenominator = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long t = Math.max(0L, Math.min(tDenominator, tNumerator));
        long projX = ax + dx * t / tDenominator;
        long projZ = az + dz * t / tDenominator;
        return RockyCoastFixedMathV2.hypot(px - projX, pz - projZ);
    }

    public record RockyCoastSample(
            int shelfMask,
            int exposureMask,
            int channelMask,
            int talusHandoffMask,
            int elevationBlocks
    ) {
        public static RockyCoastSample outside() {
            return new RockyCoastSample(0, 0, 0, 0, 0);
        }

        public boolean active() {
            return shelfMask == 1;
        }
    }

    public record RockyCoastMetrics(
            boolean shelfPresent,
            boolean exposureOk,
            boolean channelPresent,
            boolean talusHandoffPresent
    ) {
    }
}
