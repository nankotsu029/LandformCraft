package com.github.nankotsu029.landformcraft.generator.v2.foundation.cave;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.core.v2.foundation.UndergroundRiverPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.UndergroundRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.UndergroundLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Integer-only sampler and export checksums for V2-9-11 underground-river connectors. */
public final class UndergroundRiverGeneratorV2 {
    public static final String VERSION = "foundation-underground-river-fixed-v1";

    private final UndergroundRiverPlanV2 plan;
    private final CaveNetworkPlanV2 hostCave;
    private final UndergroundLakePlanV2 hostLake;

    public UndergroundRiverGeneratorV2(
            UndergroundRiverPlanV2 plan,
            CaveNetworkPlanV2 hostCave,
            UndergroundLakePlanV2 hostLake
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.hostCave = Objects.requireNonNull(hostCave, "hostCave");
        this.hostLake = Objects.requireNonNull(hostLake, "hostLake");
        if (!plan.hostCaveCanonicalChecksum().equals(hostCave.canonicalChecksum())
                || !plan.hostCaveFeatureId().equals(hostCave.featureId())) {
            throw new FoundationSliceException("v2.underground-river-missing-relation",
                    "frozen host cave checksum/featureId does not match river plan binding");
        }
        if (!plan.hostLakeCanonicalChecksum().equals(hostLake.canonicalChecksum())
                || !plan.hostLakeFeatureId().equals(hostLake.featureId())) {
            throw new FoundationSliceException("v2.underground-river-missing-relation",
                    "frozen host lake checksum/featureId does not match river plan binding");
        }
        if (!plan.fluidBodyId().equals(hostLake.fluidBody().fluidBodyId())) {
            throw new FoundationSliceException("v2.underground-river-fluid-owner-conflict",
                    "river fluidBodyId does not match lake fluid owner");
        }
    }

    public UndergroundRiverPlanV2 plan() {
        return plan;
    }

    public UndergroundRiverMetrics evaluate() {
        Map<String, CaveNetworkPlanV2.Node> nodes = new HashMap<>();
        for (CaveNetworkPlanV2.Node node : hostCave.nodes()) {
            nodes.put(node.nodeId(), node);
        }
        List<String> path = UndergroundRiverPlanCompilerV2.shortestPath(
                hostCave, plan.sourceNodeId(), plan.outletNodeId());
        boolean reachable = !path.isEmpty();
        boolean downGradientOk = reachable
                && UndergroundRiverPlanCompilerV2.downGradientOk(path, nodes);
        boolean singleFluidOwner = plan.fluidBodyId().equals(hostLake.fluidBody().fluidBodyId())
                && plan.orderedOps().stream()
                .filter(op -> UndergroundRiverPlanV2.OrderedVolumeOp.ADD_FLUID.equals(op.operationKind()))
                .count() == 1L;
        boolean fluidOrderOk = fluidOrderOk(plan.orderedOps());
        boolean leakFree = plan.aabb().intersects(hostCave.aabb())
                && plan.floodedCaveHook().waterSurfaceYBlocks() < hostCave.surfaceHeightBlocks();
        boolean airPocketOk = plan.selectedAirPocketBlocks() >= 1;
        boolean budgetOk = plan.estimatedWorkUnits() <= UndergroundRiverPlanV2.MAXIMUM_WORK_UNITS
                && plan.aabb().extentXBlocks() <= 256
                && plan.aabb().extentYBlocks() <= 128
                && plan.aabb().extentZBlocks() <= 256;
        String whole = exportChecksum();
        String tile = tileExportChecksum();
        boolean wholeTileOk = whole.equals(tile);
        boolean sceneExportOk = whole.length() == 64
                && sceneChecksum().length() == 64
                && wholeTileOk;
        return new UndergroundRiverMetrics(
                reachable,
                downGradientOk,
                singleFluidOwner,
                leakFree,
                airPocketOk,
                fluidOrderOk,
                wholeTileOk,
                budgetOk,
                sceneExportOk);
    }

    public int sampleChannelMask(int x, int y, int z) {
        long px = cellCenter(x);
        long py = Math.multiplyExact((long) y, TerrainIntentV2.FIXED_SCALE)
                + TerrainIntentV2.FIXED_SCALE / 2L;
        long pz = cellCenter(z);
        long radius = Math.multiplyExact((long) plan.selectedChannelRadiusBlocks(),
                (long) TerrainIntentV2.FIXED_SCALE);
        for (UndergroundRiverPlanV2.ReachSample reach : plan.reaches()) {
            CaveNetworkPlanV2.Node from = node(reach.fromNodeId());
            CaveNetworkPlanV2.Node to = node(reach.toNodeId());
            long dist = distanceToSegment(
                    px, py, pz,
                    from.center().xMillionths(), from.center().yMillionths(), from.center().zMillionths(),
                    to.center().xMillionths(), to.center().yMillionths(), to.center().zMillionths());
            if (dist <= radius) {
                return 1;
            }
        }
        return 0;
    }

    public int sampleFluidMask(int x, int y, int z) {
        if (sampleChannelMask(x, y, z) == 0) {
            return 0;
        }
        int waterY = plan.floodedCaveHook().waterSurfaceYBlocks();
        int fluidBottom = waterY - plan.selectedFluidDepthBlocks();
        return y <= waterY && y >= fluidBottom ? 1 : 0;
    }

    public String sceneChecksum() {
        return sha256(hostCave.canonicalChecksum()
                + '|' + hostLake.canonicalChecksum()
                + '|' + plan.canonicalChecksum());
    }

    public String exportChecksum() {
        return streamExport(plan.aabb());
    }

    public String tileExportChecksum() {
        // Two X-band tiles; nested loops keep the same global Z→X→Y order as whole export.
        VolumeSdfAabbV2 aabb = plan.aabb();
        int minX = Math.toIntExact(floorDiv(aabb.minXMillionths(), TerrainIntentV2.FIXED_SCALE));
        int maxX = Math.toIntExact(floorDiv(aabb.maxXMillionths(), TerrainIntentV2.FIXED_SCALE));
        int midX = minX + (maxX - minX) / 2;
        int minY = Math.toIntExact(floorDiv(aabb.minYMillionths(), TerrainIntentV2.FIXED_SCALE));
        int maxY = Math.toIntExact(floorDiv(aabb.maxYMillionths(), TerrainIntentV2.FIXED_SCALE));
        int minZ = Math.toIntExact(floorDiv(aabb.minZMillionths(), TerrainIntentV2.FIXED_SCALE));
        int maxZ = Math.toIntExact(floorDiv(aabb.maxZMillionths(), TerrainIntentV2.FIXED_SCALE));
        MessageDigest digest = digest();
        digest.update(VERSION.getBytes(StandardCharsets.US_ASCII));
        digest.update("whole".getBytes(StandardCharsets.US_ASCII));
        for (int z = minZ; z <= maxZ; z++) {
            streamColumnBand(digest, minX, midX, minY, maxY, z);
            streamColumnBand(digest, midX + 1, maxX, minY, maxY, z);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String streamExport(VolumeSdfAabbV2 aabb) {
        MessageDigest digest = digest();
        digest.update(VERSION.getBytes(StandardCharsets.US_ASCII));
        digest.update("whole".getBytes(StandardCharsets.US_ASCII));
        int minX = Math.toIntExact(floorDiv(aabb.minXMillionths(), TerrainIntentV2.FIXED_SCALE));
        int maxX = Math.toIntExact(floorDiv(aabb.maxXMillionths(), TerrainIntentV2.FIXED_SCALE));
        int minY = Math.toIntExact(floorDiv(aabb.minYMillionths(), TerrainIntentV2.FIXED_SCALE));
        int maxY = Math.toIntExact(floorDiv(aabb.maxYMillionths(), TerrainIntentV2.FIXED_SCALE));
        int minZ = Math.toIntExact(floorDiv(aabb.minZMillionths(), TerrainIntentV2.FIXED_SCALE));
        int maxZ = Math.toIntExact(floorDiv(aabb.maxZMillionths(), TerrainIntentV2.FIXED_SCALE));
        for (int z = minZ; z <= maxZ; z++) {
            streamColumnBand(digest, minX, maxX, minY, maxY, z);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void streamColumnBand(
            MessageDigest digest,
            int fromX,
            int toX,
            int minY,
            int maxY,
            int z
    ) {
        if (fromX > toX) {
            return;
        }
        for (int x = fromX; x <= toX; x++) {
            for (int y = minY; y <= maxY; y++) {
                int channel = sampleChannelMask(x, y, z);
                int fluid = sampleFluidMask(x, y, z);
                byte tag;
                if (fluid == 1) {
                    tag = 'F';
                } else if (channel == 1) {
                    tag = 'A';
                } else {
                    tag = 'S';
                }
                digest.update(tag);
                digest.update((byte) (y & 0xff));
            }
        }
    }

    private static boolean fluidOrderOk(List<UndergroundRiverPlanV2.OrderedVolumeOp> ops) {
        boolean sawCarve = false;
        boolean sawFluid = false;
        for (UndergroundRiverPlanV2.OrderedVolumeOp op : ops) {
            if (UndergroundRiverPlanV2.OrderedVolumeOp.CARVE_SOLID.equals(op.operationKind())) {
                if (sawFluid || !op.fluidBodyId().isEmpty()) {
                    return false;
                }
                sawCarve = true;
            } else if (UndergroundRiverPlanV2.OrderedVolumeOp.ADD_FLUID.equals(op.operationKind())) {
                if (!sawCarve || sawFluid) {
                    return false;
                }
                sawFluid = true;
            }
        }
        return sawCarve && sawFluid;
    }

    private CaveNetworkPlanV2.Node node(String nodeId) {
        return hostCave.nodes().stream()
                .filter(item -> item.nodeId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException(
                        "v2.underground-river-unreachable", "reach node missing: " + nodeId));
    }

    private static long cellCenter(int block) {
        return Math.addExact(Math.multiplyExact((long) block, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
    }

    private static long distanceToSegment(
            long px, long py, long pz,
            long ax, long ay, long az,
            long bx, long by, long bz
    ) {
        long dx = bx - ax;
        long dy = by - ay;
        long dz = bz - az;
        long lengthSq = dx * dx + dy * dy + dz * dz;
        if (lengthSq == 0L) {
            return hypot3(px - ax, py - ay, pz - az);
        }
        long tNum = (px - ax) * dx + (py - ay) * dy + (pz - az) * dz;
        long t = Math.max(0L, Math.min(lengthSq, tNum));
        long projX = ax + dx * t / lengthSq;
        long projY = ay + dy * t / lengthSq;
        long projZ = az + dz * t / lengthSq;
        return hypot3(px - projX, py - projY, pz - projZ);
    }

    private static long hypot3(long dx, long dy, long dz) {
        long value = dx * dx + dy * dy + dz * dz;
        long estimate = value;
        if (estimate <= 0L) {
            return 0L;
        }
        long previous;
        do {
            previous = estimate;
            estimate = (estimate + value / estimate) / 2L;
        } while (estimate < previous);
        return previous;
    }

    private static long floorDiv(long numerator, long denominator) {
        return Math.floorDiv(numerator, denominator);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String sha256(String value) {
        MessageDigest digest = digest();
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    public record UndergroundRiverMetrics(
            boolean reachable,
            boolean downGradientOk,
            boolean singleFluidOwner,
            boolean leakFree,
            boolean airPocketOk,
            boolean fluidOrderOk,
            boolean wholeTileOk,
            boolean budgetOk,
            boolean sceneExportOk
    ) {
    }
}
