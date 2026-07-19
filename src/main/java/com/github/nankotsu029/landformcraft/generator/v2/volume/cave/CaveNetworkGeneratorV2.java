package com.github.nankotsu029.landformcraft.generator.v2.volume.cave;

import com.github.nankotsu029.landformcraft.generator.v2.volume.sdf.VolumeSdfKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * SUPPORTED (offline) cave-network metric validator. Uses SDF containment on a bounded lattice (not a
 * dense cellular grid) to check roof thickness and undeclared surface breakthrough.
 */
public final class CaveNetworkGeneratorV2 {
    public static final String GENERATOR_VERSION = "cave-network-generator-v1";

    private final CaveNetworkPlanV2 plan;
    private final VolumeSdfKernelV2 sdfKernel;
    private final VolumeCsgPlanV2 csgPlan;

    public CaveNetworkGeneratorV2(
            CaveNetworkPlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(sdfPlan, "sdfPlan");
        this.csgPlan = Objects.requireNonNull(csgPlan, "csgPlan");
        if (!CaveNetworkPlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new CaveNetworkExceptionV2(
                    CaveNetworkFailureCodeV2.UNKNOWN_KERNEL, "unsupported cave-network kernel");
        }
        if (!plan.sdfPlanBinding().sourceArtifactChecksum().equals(sdfPlan.canonicalChecksum())) {
            throw new CaveNetworkExceptionV2(
                    CaveNetworkFailureCodeV2.BINDING_MISMATCH, "cave SDF plan binding mismatch");
        }
        if (!plan.csgPlanBinding().sourceArtifactChecksum().equals(csgPlan.canonicalChecksum())) {
            throw new CaveNetworkExceptionV2(
                    CaveNetworkFailureCodeV2.BINDING_MISMATCH, "cave CSG plan binding mismatch");
        }
        this.sdfKernel = new VolumeSdfKernelV2(sdfPlan);
    }

    public CaveNetworkPlanV2 plan() {
        return plan;
    }

    public VolumeCsgPlanV2 csgPlan() {
        return csgPlan;
    }

    public CaveNetworkMetricsV2 validate() {
        long carvedSamples = 0L;
        long thinRoofSamples = 0L;
        long breakthroughSamples = 0L;
        int minRoof = plan.kernel().minimumRoofBlocks();
        int surfaceY = plan.surfaceHeightBlocks();
        if (plan.entranceNodeIds().isEmpty()) {
            throw new CaveNetworkExceptionV2(
                    CaveNetworkFailureCodeV2.MISSING_ENTRANCE, "no entrance nodes");
        }

        long step = 1_000_000L;
        long minX = alignDown(plan.aabb().minXMillionths(), step);
        long maxX = alignUp(plan.aabb().maxXMillionths(), step);
        long minY = alignDown(plan.aabb().minYMillionths(), step);
        long maxY = alignUp(plan.aabb().maxYMillionths(), step);
        long minZ = alignDown(plan.aabb().minZMillionths(), step);
        long maxZ = alignUp(plan.aabb().maxZMillionths(), step);
        long samples = 0L;
        final long maximumSamples = 250_000L;
        for (long z = minZ; z <= maxZ; z = Math.addExact(z, step)) {
            for (long y = minY; y <= maxY; y = Math.addExact(y, step)) {
                for (long x = minX; x <= maxX; x = Math.addExact(x, step)) {
                    samples = Math.addExact(samples, 1L);
                    if (samples > maximumSamples) {
                        throw new CaveNetworkExceptionV2(
                                CaveNetworkFailureCodeV2.BUDGET_EXCEEDED,
                                "cave metric sample budget exceeded");
                    }
                    if (!insideAnyPrimitive(x, y, z)) {
                        continue;
                    }
                    carvedSamples++;
                    int blockY = (int) Math.floorDiv(y, 1_000_000L);
                    boolean nearEntrance = nearEntrance(x, y, z);
                    int roof = surfaceY - blockY;
                    if (!nearEntrance && roof < minRoof) {
                        thinRoofSamples++;
                    }
                    if (!nearEntrance && blockY >= surfaceY) {
                        breakthroughSamples++;
                    }
                }
            }
        }
        if (thinRoofSamples > 0) {
            throw new CaveNetworkExceptionV2(
                    CaveNetworkFailureCodeV2.THIN_ROOF,
                    "cave roof thinner than minimumRoofBlocks at " + thinRoofSamples + " samples");
        }
        if (breakthroughSamples > 0) {
            throw new CaveNetworkExceptionV2(
                    CaveNetworkFailureCodeV2.SURFACE_BREAKTHROUGH,
                    "undeclared surface breakthrough at " + breakthroughSamples + " samples");
        }
        for (CaveNetworkPlanV2.Node node : plan.nodes()) {
            if (node.kind() != CaveNetworkPlanV2.NodeKind.ENTRANCE) {
                continue;
            }
            int ey = (int) Math.floorDiv(node.center().yMillionths(), 1_000_000L);
            if (ey > surfaceY || surfaceY - ey > minRoof + 8) {
                throw new CaveNetworkExceptionV2(
                        CaveNetworkFailureCodeV2.HARD_CLEARANCE_CONFLICT,
                        "entrance clearance relative to surface is invalid");
            }
        }
        return new CaveNetworkMetricsV2(
                GENERATOR_VERSION,
                carvedSamples,
                thinRoofSamples,
                breakthroughSamples,
                samples,
                plan.canonicalChecksum());
    }

    public String metricChecksum() {
        CaveNetworkMetricsV2 metrics = validate();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(GENERATOR_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.carvedSamples())
                    .getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.sampleCount())
                    .getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean insideAnyPrimitive(long x, long y, long z) {
        for (var primitive : sdfKernel.plan().primitives()) {
            if (sdfKernel.sampleDistanceMillionths(primitive.primitiveId(), x, y, z) < 0L) {
                return true;
            }
        }
        return false;
    }

    private boolean nearEntrance(long x, long y, long z) {
        for (CaveNetworkPlanV2.Node node : plan.nodes()) {
            if (node.kind() != CaveNetworkPlanV2.NodeKind.ENTRANCE) {
                continue;
            }
            VolumeSdfVec3V2 center = node.center();
            long dx = Math.subtractExact(x, center.xMillionths());
            long dy = Math.subtractExact(y, center.yMillionths());
            long dz = Math.subtractExact(z, center.zMillionths());
            long r = Math.addExact(node.radiusMillionths(), 1_000_000L);
            try {
                long dist2 = Math.addExact(
                        Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dy, dy)),
                        Math.multiplyExact(dz, dz));
                if (dist2 <= Math.multiplyExact(r, r)) {
                    return true;
                }
            } catch (ArithmeticException ignored) {
                // treat as far
            }
        }
        return false;
    }

    private static long alignDown(long value, long step) {
        return Math.multiplyExact(Math.floorDiv(value, step), step);
    }

    private static long alignUp(long value, long step) {
        return Math.multiplyExact(Math.floorDiv(Math.addExact(value, step - 1), step), step);
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    public record CaveNetworkMetricsV2(
            String generatorVersion,
            long carvedSamples,
            long thinRoofSamples,
            long breakthroughSamples,
            long sampleCount,
            String planChecksum
    ) {
    }
}
