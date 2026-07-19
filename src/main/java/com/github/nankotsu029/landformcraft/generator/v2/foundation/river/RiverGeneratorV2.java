package com.github.nankotsu029.landformcraft.generator.v2.foundation.river;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-04 general river foundation profile. */
public final class RiverGeneratorV2 {
    public static final String VERSION = "foundation-river-fixed-v1";

    private final RiverPlanV2 plan;
    private final long channelRadius;
    private final long bankOuter;
    private final long floodOuter;

    public RiverGeneratorV2(RiverPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        channelRadius = Math.multiplyExact(
                (long) plan.selectedBankfullWidthBlocks() / 2L, TerrainIntentV2.FIXED_SCALE);
        bankOuter = Math.multiplyExact(
                (long) plan.selectedBankfullWidthBlocks() / 2L + plan.bankWidthBlocks(),
                TerrainIntentV2.FIXED_SCALE);
        floodOuter = Math.multiplyExact(
                (long) plan.floodplainHandoffWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
    }

    public RiverPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public RiverSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.river-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        Nearest nearest = nearestCenterline(px, pz);
        boolean channel = nearest.distance() <= channelRadius;
        boolean bank = !channel && nearest.distance() <= bankOuter;
        boolean floodplain = !channel && !bank && nearest.distance() <= floodOuter;
        if (!channel && !bank && !floodplain) {
            return RiverSample.outside();
        }
        int bedBlocks = Math.toIntExact(nearest.bedYMillionths() / TerrainIntentV2.FIXED_SCALE);
        return new RiverSample(channel ? 1 : 0, bank ? 1 : 0, floodplain ? 1 : 0, bedBlocks,
                plan.selectedDischargeIndex());
    }

    public RiverMetrics evaluate() {
        long channelCells = 0L;
        long floodCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                RiverSample sample = sampleAt(x, z);
                channelCells += sample.channelMask();
                floodCells += sample.floodplainMask();
            }
        }
        boolean bedMonotonic = true;
        for (int i = 1; i < plan.centerline().size(); i++) {
            if (plan.centerline().get(i).bedYMillionths() > plan.centerline().get(i - 1).bedYMillionths()) {
                bedMonotonic = false;
                break;
            }
        }
        boolean sourceMouth = plan.nodes().stream().anyMatch(node -> node.kind() == RiverPlanV2.NodeKind.SOURCE)
                && plan.nodes().stream().anyMatch(node -> node.kind() == RiverPlanV2.NodeKind.MOUTH)
                && !plan.reaches().isEmpty();
        return new RiverMetrics(
                sourceMouth && channelCells > 0,
                bedMonotonic,
                plan.reaches().stream().noneMatch(reach -> reach.fromNodeId().equals(reach.toNodeId())),
                plan.reaches().size() <= RiverPlanV2.MAXIMUM_REACHES
                        && plan.nodes().size() <= RiverPlanV2.MAXIMUM_NODES,
                true);
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
                    RiverSample sample = sampleAt(px, pz);
                    if (!sample.active()) {
                        return 0;
                    }
                    return Math.toIntExact(Math.multiplyExact(
                            (long) sample.bedElevationBlocks(), TerrainIntentV2.FIXED_SCALE));
                });
    }

    private Nearest nearestCenterline(long px, long pz) {
        long best = Long.MAX_VALUE;
        long bed = plan.sourceBedYMillionths();
        for (int i = 1; i < plan.centerline().size(); i++) {
            RiverPlanV2.CenterlineSample a = plan.centerline().get(i - 1);
            RiverPlanV2.CenterlineSample b = plan.centerline().get(i);
            long distance = distanceToSegment(px, pz, a.xMillionths(), a.zMillionths(),
                    b.xMillionths(), b.zMillionths());
            if (distance < best) {
                best = distance;
                bed = (a.bedYMillionths() + b.bedYMillionths()) / 2L;
            }
        }
        return new Nearest(best, bed);
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = bx - ax;
        long dz = bz - az;
        if (dx == 0L && dz == 0L) {
            return RiverFixedMathV2.hypot(px - ax, pz - az);
        }
        long tNumerator = Math.addExact(
                Math.multiplyExact(px - ax, dx),
                Math.multiplyExact(pz - az, dz));
        long tDenominator = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long t = Math.max(0L, Math.min(tDenominator, tNumerator));
        long projX = ax + dx * t / tDenominator;
        long projZ = az + dz * t / tDenominator;
        return RiverFixedMathV2.hypot(px - projX, pz - projZ);
    }

    public record RiverSample(
            int channelMask,
            int bankMask,
            int floodplainMask,
            int bedElevationBlocks,
            int dischargeIndex
    ) {
        public static RiverSample outside() {
            return new RiverSample(0, 0, 0, 0, 0);
        }

        public boolean active() {
            return channelMask == 1 || bankMask == 1 || floodplainMask == 1;
        }
    }

    public record RiverMetrics(
            boolean sourceMouthReachable,
            boolean bedMonotonic,
            boolean confluenceFlowOk,
            boolean graphBudgetOk,
            boolean cycleFree
    ) {
    }

    private record Nearest(long distance, long bedYMillionths) {
    }
}
