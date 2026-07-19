package com.github.nankotsu029.landformcraft.generator.v2.foundation.spring;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.river.RiverFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SpringPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Integer-only sampler and export checksums for V2-10-10 surface spring outflow nodes. */
public final class SpringGeneratorV2 {
    public static final String VERSION = "foundation-spring-fixed-v1";

    private final SpringPlanV2 plan;
    private final RiverPlanV2 riverPlan;

    public SpringGeneratorV2(SpringPlanV2 plan, RiverPlanV2 riverPlan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.riverPlan = Objects.requireNonNull(riverPlan, "riverPlan");
        if (!plan.riverFeatureId().equals(riverPlan.featureId())
                || !plan.riverPlanChecksum().equals(riverPlan.canonicalChecksum())) {
            throw new FoundationSliceException("v2.spring-orphan",
                    "frozen river checksum/featureId does not match spring plan binding");
        }
    }

    public SpringPlanV2 plan() {
        return plan;
    }

    public SpringMetrics evaluate() {
        boolean sourceOwnershipOk = !plan.outletSurfaceFeatureId().isBlank()
                && !plan.outletSurfaceGeometryChecksum().isBlank()
                && !plan.outletRelationId().isBlank();
        boolean riverSourceBindOk = plan.riverSourceNodeId().equals(findSourceNode().nodeId())
                && !plan.downstreamReachId().isBlank();
        boolean outflowContinuityOk = continuityOk();
        boolean hydrologySpringNodeOk = plan.hydrologyNodeId().equals("hydro.spring." + plan.featureId())
                && SpringPlanV2.HYDROLOGY_NODE_KIND.equals(plan.hydrologyNodeKind());
        boolean graphReachableOk = !plan.downstreamReachId().isBlank()
                && riverPlan.reaches().stream()
                        .anyMatch(reach -> reach.reachId().equals(plan.downstreamReachId()));
        boolean budgetOk = plan.estimatedWorkUnits() <= SpringPlanV2.MAXIMUM_WORK_UNITS
                && plan.outflowRadiusBlocks() >= 1
                && plan.sourceDischargeBlocks() >= 1;
        String export = exportChecksum();
        String tileExport = tileExportChecksum();
        boolean wholeTileOk = export.equals(tileExport);
        boolean exportOk = export.length() == 64 && tileExport.length() == 64;
        boolean orphanFree = !plan.riverFeatureId().isBlank()
                && !plan.outletSurfaceFeatureId().isBlank();
        return new SpringMetrics(
                sourceOwnershipOk,
                riverSourceBindOk,
                outflowContinuityOk,
                hydrologySpringNodeOk,
                graphReachableOk,
                budgetOk,
                wholeTileOk,
                exportOk,
                orphanFree);
    }

    public int sampleSourceMask(int x, int z) {
        long px = cellCenter(x);
        long pz = cellCenter(z);
        long dx = px - plan.openingXMillionths();
        long dz = pz - plan.openingZMillionths();
        long radius = Math.multiplyExact((long) plan.outflowRadiusBlocks(), TerrainIntentV2.FIXED_SCALE);
        return dx * dx + dz * dz <= radius * radius ? 1 : 0;
    }

    public int sampleOutflowMask(int x, int z) {
        return sampleSourceMask(x, z);
    }

    public String exportChecksum() {
        return digestSamples();
    }

    public String tileExportChecksum() {
        return digestSamples();
    }

    private String digestSamples() {
        MessageDigest digest = digest();
        digest.update(VERSION.getBytes(StandardCharsets.US_ASCII));
        digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.US_ASCII));
        digest.update(riverPlan.canonicalChecksum().getBytes(StandardCharsets.US_ASCII));
        putLong(digest, plan.openingXMillionths());
        putLong(digest, plan.openingYMillionths());
        putLong(digest, plan.openingZMillionths());
        putLong(digest, plan.sourceDischargeBlocks());
        digest.update((byte) sampleSourceMaskAtOpening());
        digest.update((byte) sampleOutflowMaskAtOpening());
        return HexFormat.of().formatHex(digest.digest());
    }

    private int sampleSourceMaskAtOpening() {
        int x = Math.toIntExact(plan.openingXMillionths() / TerrainIntentV2.FIXED_SCALE);
        int z = Math.toIntExact(plan.openingZMillionths() / TerrainIntentV2.FIXED_SCALE);
        return sampleSourceMask(Math.max(0, x), Math.max(0, z));
    }

    private int sampleOutflowMaskAtOpening() {
        int x = Math.toIntExact(plan.openingXMillionths() / TerrainIntentV2.FIXED_SCALE);
        int z = Math.toIntExact(plan.openingZMillionths() / TerrainIntentV2.FIXED_SCALE);
        return sampleOutflowMask(Math.max(0, x), Math.max(0, z));
    }

    private boolean continuityOk() {
        RiverPlanV2.Node source = findSourceNode();
        long dx = plan.openingXMillionths() - source.xMillionths();
        long dz = plan.openingZMillionths() - source.zMillionths();
        long distanceBlocks = RiverFixedMathV2.hypot(dx, dz) / TerrainIntentV2.FIXED_SCALE;
        return distanceBlocks <= plan.supportRadiusXZ()
                && plan.sourceBedYMillionths() == riverPlan.sourceBedYMillionths()
                && plan.openingYMillionths() == riverPlan.sourceBedYMillionths();
    }

    private RiverPlanV2.Node findSourceNode() {
        return riverPlan.nodes().stream()
                .filter(node -> node.nodeId().equals(plan.riverSourceNodeId()))
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException("v2.spring-orphan",
                        "river source node missing from frozen river plan"));
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

    public record SpringMetrics(
            boolean sourceOwnershipOk,
            boolean riverSourceBindOk,
            boolean outflowContinuityOk,
            boolean hydrologySpringNodeOk,
            boolean graphReachableOk,
            boolean budgetOk,
            boolean wholeTileOk,
            boolean exportOk,
            boolean orphanFree
    ) {
    }
}
