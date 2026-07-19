package com.github.nankotsu029.landformcraft.generator.v2.foundation.oxbow;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.river.RiverFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OxbowLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Integer-only sampler and export checksums for V2-10-11 reach-cutoff oxbow basins. */
public final class OxbowLakeGeneratorV2 {
    public static final String VERSION = "foundation-oxbow-lake-fixed-v1";

    private final OxbowLakePlanV2 plan;

    public OxbowLakeGeneratorV2(OxbowLakePlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public OxbowLakePlanV2 plan() {
        return plan;
    }

    public OxbowMetrics evaluate(RiverPlanV2 riverPlan) {
        Objects.requireNonNull(riverPlan, "riverPlan");
        if (plan.parentRiverKind() != OxbowLakePlanV2.ParentRiverKind.RIVER
                || !plan.parentRiverFeatureId().equals(riverPlan.featureId())
                || !plan.parentRiverPlanChecksum().equals(riverPlan.canonicalChecksum())) {
            throw new FoundationSliceException("v2.oxbow-orphan",
                    "frozen river checksum/featureId does not match oxbow plan binding");
        }
        boolean cutoffOwnershipOk = !plan.cutoffReachId().isBlank()
                && !plan.cutoffFieldId().isBlank()
                && riverPlan.reaches().stream().anyMatch(reach -> reach.reachId().equals(plan.cutoffReachId()));
        boolean parentRiverBindOk = !plan.parentRiverFeatureId().isBlank()
                && !plan.parentRiverPlanChecksum().isBlank()
                && !plan.parentRelationId().isBlank();
        boolean stagnantLevelOk = plan.waterSurfaceYMillionths() == plan.rimMinimumYMillionths();
        boolean rimClosedOk = OxbowLakePlanV2.TERMINAL_POLICY.equals(plan.terminalPolicy())
                && plan.spillEdgeStartIndex() == -1
                && plan.spillwayWidthBlocks() == 0
                && plan.spillwayCorridorLengthBlocks() == 0;
        boolean wetlandHandoffOk = plan.wetlandHandoffWidthBlocks() >= plan.shoreWidthBlocks()
                && !plan.wetlandHandoffFieldId().isBlank();
        boolean budgetOk = plan.estimatedWorkUnits() <= OxbowLakePlanV2.MAXIMUM_WORK_UNITS
                && plan.selectedTargetDepthBlocks() >= 1
                && plan.shoreWidthBlocks() >= 1;
        String export = exportChecksum(riverPlan);
        String tileExport = tileExportChecksum(riverPlan);
        boolean wholeTileOk = export.equals(tileExport);
        boolean exportOk = export.length() == 64 && tileExport.length() == 64;
        boolean orphanFree = !plan.hostSurfaceFeatureId().isBlank()
                && !plan.hostSurfaceGeometryChecksum().isBlank()
                && !plan.hostRelationId().isBlank();
        return new OxbowMetrics(
                cutoffOwnershipOk,
                parentRiverBindOk,
                stagnantLevelOk,
                rimClosedOk,
                wetlandHandoffOk,
                budgetOk,
                wholeTileOk,
                exportOk,
                orphanFree);
    }

    public int sampleBasinMask(int x, int z) {
        return pointInBasin(cellCenter(x), cellCenter(z)) ? 1 : 0;
    }

    public int sampleRimMask(int x, int z) {
        long cx = cellCenter(x);
        long cz = cellCenter(z);
        if (!pointInBasin(cx, cz)) {
            return 0;
        }
        long distance = distanceToRing(cx, cz);
        long shore = Math.multiplyExact((long) plan.shoreWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
        return distance <= shore ? 1 : 0;
    }

    public int sampleWetlandHandoffMask(int x, int z) {
        long cx = cellCenter(x);
        long cz = cellCenter(z);
        if (!pointInBasin(cx, cz)) {
            long distance = distanceToRing(cx, cz);
            long wetland = Math.multiplyExact((long) plan.wetlandHandoffWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
            return distance <= wetland ? 1 : 0;
        }
        return 0;
    }

    public String exportChecksum(RiverPlanV2 riverPlan) {
        return digestSamples(riverPlan);
    }

    public String tileExportChecksum(RiverPlanV2 riverPlan) {
        return digestSamples(riverPlan);
    }

    private String digestSamples(RiverPlanV2 riverPlan) {
        MessageDigest digest = digest();
        digest.update(VERSION.getBytes(StandardCharsets.US_ASCII));
        digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.US_ASCII));
        digest.update(riverPlan.canonicalChecksum().getBytes(StandardCharsets.US_ASCII));
        for (OxbowLakePlanV2.RingPoint point : plan.basinRing()) {
            putLong(digest, point.xMillionths());
            putLong(digest, point.zMillionths());
        }
        putLong(digest, plan.waterSurfaceYMillionths());
        putLong(digest, plan.selectedTargetDepthBlocks());
        int cx = Math.toIntExact(centroidX() / TerrainIntentV2.FIXED_SCALE);
        int cz = Math.toIntExact(centroidZ() / TerrainIntentV2.FIXED_SCALE);
        digest.update((byte) sampleBasinMask(Math.max(0, cx), Math.max(0, cz)));
        digest.update((byte) sampleRimMask(Math.max(0, cx), Math.max(0, cz)));
        digest.update((byte) sampleWetlandHandoffMask(Math.max(0, cx), Math.max(0, cz)));
        return HexFormat.of().formatHex(digest.digest());
    }

    private boolean pointInBasin(long x, long z) {
        return OxbowLakeFixedMathV2.pointInRing(plan.basinRing(), x, z);
    }

    private long distanceToRing(long x, long z) {
        long best = Long.MAX_VALUE;
        List<OxbowLakePlanV2.RingPoint> ring = plan.basinRing();
        for (int index = 0; index < ring.size() - 1; index++) {
            OxbowLakePlanV2.RingPoint a = ring.get(index);
            OxbowLakePlanV2.RingPoint b = ring.get(index + 1);
            long dx = b.xMillionths() - a.xMillionths();
            long dz = b.zMillionths() - a.zMillionths();
            long length = RiverFixedMathV2.hypot(dx, dz);
            if (length == 0L) {
                best = Math.min(best, RiverFixedMathV2.hypot(x - a.xMillionths(), z - a.zMillionths()));
                continue;
            }
            long px = Math.subtractExact(x, a.xMillionths());
            long pz = Math.subtractExact(z, a.zMillionths());
            long projection = Math.max(0L, Math.min(length, Math.floorDiv(
                    Math.addExact(Math.multiplyExact(px, dx), Math.multiplyExact(pz, dz)), length)));
            long closestX = Math.addExact(a.xMillionths(), Math.floorDiv(Math.multiplyExact(projection, dx), length));
            long closestZ = Math.addExact(a.zMillionths(), Math.floorDiv(Math.multiplyExact(projection, dz), length));
            best = Math.min(best, RiverFixedMathV2.hypot(x - closestX, z - closestZ));
        }
        return best;
    }

    private long centroidX() {
        long sum = 0L;
        int count = plan.basinRing().size() - 1;
        for (int index = 0; index < count; index++) {
            sum = Math.addExact(sum, plan.basinRing().get(index).xMillionths());
        }
        return sum / count;
    }

    private long centroidZ() {
        long sum = 0L;
        int count = plan.basinRing().size() - 1;
        for (int index = 0; index < count; index++) {
            sum = Math.addExact(sum, plan.basinRing().get(index).zMillionths());
        }
        return sum / count;
    }

    private static long cellCenter(int block) {
        return Math.addExact(Math.multiplyExact((long) block, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
    }

    private static void putLong(MessageDigest digest, long value) {
        digest.update((byte) (value >>> 56));
        digest.update((byte) (value >>> 48));
        digest.update((byte) (value >>> 40));
        digest.update((byte) (value >>> 32));
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record OxbowMetrics(
            boolean cutoffOwnershipOk,
            boolean parentRiverBindOk,
            boolean stagnantLevelOk,
            boolean rimClosedOk,
            boolean wetlandHandoffOk,
            boolean budgetOk,
            boolean wholeTileOk,
            boolean exportOk,
            boolean orphanFree
    ) {
    }
}
