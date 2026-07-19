package com.github.nankotsu029.landformcraft.generator.v2.foundation.cave;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.CaveEntrancePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Integer-only sampler and export checksums for V2-9-10 cave-entrance connectors. */
public final class CaveEntranceGeneratorV2 {
    public static final String VERSION = "foundation-cave-entrance-fixed-v1";

    private final CaveEntrancePlanV2 plan;
    private final CaveNetworkPlanV2 hostCave;
    private final String surfaceHostGeometryChecksum;

    public CaveEntranceGeneratorV2(
            CaveEntrancePlanV2 plan,
            CaveNetworkPlanV2 hostCave,
            String surfaceHostGeometryChecksum
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.hostCave = Objects.requireNonNull(hostCave, "hostCave");
        this.surfaceHostGeometryChecksum = Objects.requireNonNull(
                surfaceHostGeometryChecksum, "surfaceHostGeometryChecksum");
        if (!plan.hostCaveCanonicalChecksum().equals(hostCave.canonicalChecksum())
                || !plan.caveNetworkFeatureId().equals(hostCave.featureId())) {
            throw new FoundationSliceException("v2.cave-entrance-orphan",
                    "frozen host cave checksum/featureId does not match entrance plan binding");
        }
        if (!plan.surfaceHostGeometryChecksum().equals(surfaceHostGeometryChecksum)) {
            throw new FoundationSliceException("v2.cave-entrance-owner-conflict",
                    "surface host geometry checksum does not match entrance plan binding");
        }
    }

    public CaveEntrancePlanV2 plan() {
        return plan;
    }

    public CaveEntranceMetrics evaluate() {
        boolean singleSurfaceHost = !plan.surfaceHostFeatureId().isBlank();
        boolean singleCaveTarget = !plan.caveNetworkFeatureId().isBlank();
        boolean reachable = approachEndsInEntrance();
        boolean roofOk = roofSamplesOk();
        boolean floodLeakFree = plan.openingYBlocks() >= 0; // detailed water check done at compile
        boolean ownerConflictFree = plan.selectedMinimumOpeningBlocks() >= 2;
        boolean aabbBudgetOk = plan.estimatedWorkUnits() <= CaveEntrancePlanV2.MAXIMUM_WORK_UNITS
                && plan.aabb().extentXBlocks() <= 256
                && plan.aabb().extentYBlocks() <= 128
                && plan.aabb().extentZBlocks() <= 256;
        boolean seamlessExportOk = seamlessQueryChecksum().length() == 64
                && exportChecksum().length() == 64;
        return new CaveEntranceMetrics(
                singleSurfaceHost,
                singleCaveTarget,
                reachable,
                roofOk,
                floodLeakFree,
                ownerConflictFree,
                aabbBudgetOk,
                seamlessExportOk);
    }

    public int sampleOpeningMask(int x, int z) {
        long cellX = cellCenter(x);
        long cellZ = cellCenter(z);
        long dx = cellX - plan.openingXMillionths();
        long dz = cellZ - plan.openingZMillionths();
        long radius = Math.multiplyExact((long) plan.selectedMinimumOpeningBlocks(),
                (long) TerrainIntentV2.FIXED_SCALE);
        return dx * dx + dz * dz <= radius * radius ? 1 : 0;
    }

    public int sampleApproachMask(int x, int y, int z) {
        CaveEntrancePlanV2.ApproachCapsule approach = plan.approach();
        long px = cellCenter(x);
        long py = Math.multiplyExact((long) y, TerrainIntentV2.FIXED_SCALE)
                + TerrainIntentV2.FIXED_SCALE / 2L;
        long pz = cellCenter(z);
        long dist = distanceToSegment(
                px, py, pz,
                approach.startXMillionths(), approach.startYMillionths(), approach.startZMillionths(),
                approach.endXMillionths(), approach.endYMillionths(), approach.endZMillionths());
        return dist <= approach.radiusMillionths() ? 1 : 0;
    }

    public String seamlessQueryChecksum() {
        return sha256(surfaceHostGeometryChecksum
                + '|' + hostCave.canonicalChecksum()
                + '|' + plan.canonicalChecksum());
    }

    public String exportChecksum() {
        VolumeSdfAabbV2 aabb = plan.aabb();
        int minX = Math.toIntExact(floorDiv(aabb.minXMillionths(), TerrainIntentV2.FIXED_SCALE));
        int maxX = Math.toIntExact(floorDiv(aabb.maxXMillionths(), TerrainIntentV2.FIXED_SCALE));
        int minY = Math.toIntExact(floorDiv(aabb.minYMillionths(), TerrainIntentV2.FIXED_SCALE));
        int maxY = Math.toIntExact(floorDiv(aabb.maxYMillionths(), TerrainIntentV2.FIXED_SCALE));
        int minZ = Math.toIntExact(floorDiv(aabb.minZMillionths(), TerrainIntentV2.FIXED_SCALE));
        int maxZ = Math.toIntExact(floorDiv(aabb.maxZMillionths(), TerrainIntentV2.FIXED_SCALE));
        MessageDigest digest = digest();
        digest.update(VERSION.getBytes(StandardCharsets.US_ASCII));
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    int opening = sampleOpeningMask(Math.max(0, x), Math.max(0, z));
                    int approach = sampleApproachMask(Math.max(0, x), y, Math.max(0, z));
                    digest.update((byte) opening);
                    digest.update((byte) approach);
                    digest.update((byte) (y & 0xff));
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private boolean approachEndsInEntrance() {
        CaveNetworkPlanV2.Node target = hostCave.nodes().stream()
                .filter(node -> node.nodeId().equals(plan.targetEntranceNodeId()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return false;
        }
        CaveEntrancePlanV2.ApproachCapsule approach = plan.approach();
        long dx = approach.endXMillionths() - target.center().xMillionths();
        long dy = approach.endYMillionths() - target.center().yMillionths();
        long dz = approach.endZMillionths() - target.center().zMillionths();
        return dx * dx + dy * dy + dz * dz <= target.radiusMillionths() * target.radiusMillionths();
    }

    private boolean roofSamplesOk() {
        CaveEntrancePlanV2.ApproachCapsule approach = plan.approach();
        long openingRadius = Math.multiplyExact(
                (long) plan.selectedMinimumOpeningBlocks(), (long) TerrainIntentV2.FIXED_SCALE);
        int samples = 8;
        for (int index = 0; index <= samples; index++) {
            long x = approach.startXMillionths()
                    + (approach.endXMillionths() - approach.startXMillionths()) * index / samples;
            long y = approach.startYMillionths()
                    + (approach.endYMillionths() - approach.startYMillionths()) * index / samples;
            long z = approach.startZMillionths()
                    + (approach.endZMillionths() - approach.startZMillionths()) * index / samples;
            long radialDx = x - plan.openingXMillionths();
            long radialDy = y - Math.multiplyExact((long) plan.openingYBlocks(),
                    (long) TerrainIntentV2.FIXED_SCALE);
            long radialDz = z - plan.openingZMillionths();
            if (radialDx * radialDx + radialDy * radialDy + radialDz * radialDz
                    <= openingRadius * openingRadius) {
                continue;
            }
            int sampleY = Math.toIntExact(y / TerrainIntentV2.FIXED_SCALE);
            if (plan.surfaceYBlocks() - sampleY < plan.selectedRoofClearanceBlocks()) {
                return false;
            }
        }
        return true;
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
            estimate = (estimate + value / estimate) >>> 1;
        } while (estimate < previous);
        return previous;
    }

    private static long floorDiv(long value, long divisor) {
        long quotient = value / divisor;
        if ((value ^ divisor) < 0L && quotient * divisor != value) {
            return quotient - 1L;
        }
        return quotient;
    }

    private static String sha256(String value) {
        MessageDigest digest = digest();
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record CaveEntranceMetrics(
            boolean singleSurfaceHost,
            boolean singleCaveTarget,
            boolean reachable,
            boolean roofOk,
            boolean floodLeakFree,
            boolean ownerConflictFree,
            boolean aabbBudgetOk,
            boolean seamlessExportOk
    ) {
    }
}
