package com.github.nankotsu029.landformcraft.generator.v2.volume.skyisland;

import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgEvaluatorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.sdf.VolumeSdfKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.SkyIslandGroupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * SUPPORTED (offline) sky-island-group metric validator. Checks component count, ground clearance, gap,
 * top/edge/underside classes, and rejects merged／ground／out-of-Y／dense-fill corruption.
 */
public final class SkyIslandGroupGeneratorV2 {
    public static final String GENERATOR_VERSION = "sky-island-group-generator-v1";
    private static final long M = 1_000_000L;

    private final SkyIslandGroupPlanV2 plan;
    private final VolumeSdfKernelV2 sdfKernel;
    private final VolumeCsgPlanV2 csgPlan;
    private final VolumeSdfPrimitivePlanV2 sdfPlan;
    private final VolumeCsgEvaluatorV2 evaluator;

    public SkyIslandGroupGeneratorV2(
            SkyIslandGroupPlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.sdfPlan = Objects.requireNonNull(sdfPlan, "sdfPlan");
        this.csgPlan = Objects.requireNonNull(csgPlan, "csgPlan");
        if (!SkyIslandGroupPlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.UNKNOWN_KERNEL, "unsupported sky-island-group kernel");
        }
        if (!plan.sdfPlanBinding().sourceArtifactChecksum().equals(sdfPlan.canonicalChecksum())) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.BINDING_MISMATCH,
                    "sky-island-group SDF binding mismatch");
        }
        if (!plan.csgPlanBinding().sourceArtifactChecksum().equals(csgPlan.canonicalChecksum())) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.BINDING_MISMATCH,
                    "sky-island-group CSG binding mismatch");
        }
        this.sdfKernel = new VolumeSdfKernelV2(sdfPlan);
        this.evaluator = new VolumeCsgEvaluatorV2(csgPlan, sdfPlan);
    }

    public SkyIslandGroupPlanV2 plan() {
        return plan;
    }

    public VolumeCsgPlanV2 csgPlan() {
        return csgPlan;
    }

    public SkyIslandGroupMetricsV2 validate() {
        for (SkyIslandGroupPlanV2.IslandComponent component : plan.components()) {
            SkyIslandGroupPlanCompilerV2.requireComponentGeometry(
                    component,
                    plan.groundReferenceYBlocks(),
                    plan.minimumAllowedYBlocks(),
                    plan.maximumAllowedYBlocks(),
                    plan.kernel());
        }
        SkyIslandGroupPlanCompilerV2.requireGaps(
                plan.components(), plan.kernel().minimumInterIslandGapBlocks());
        SkyIslandGroupPlanCompilerV2.requireNoDenseAirFill(
                plan.components(), plan.groundReferenceYBlocks());
        SkyIslandGroupPlanCompilerV2.requireAddThenCarvePairs(csgPlan.operators());

        long step = M;
        long minX = alignDown(plan.aabb().minXMillionths(), step);
        long maxX = alignUp(plan.aabb().maxXMillionths(), step);
        long minY = alignDown(plan.aabb().minYMillionths(), step);
        long maxY = alignUp(plan.aabb().maxYMillionths(), step);
        long minZ = alignDown(plan.aabb().minZMillionths(), step);
        long maxZ = alignUp(plan.aabb().maxZMillionths(), step);

        long solidSamples = 0L;
        long topSamples = 0L;
        long edgeSamples = 0L;
        long undersideSamples = 0L;
        long samples = 0L;
        int maxSamples = plan.budget().maximumDescriptorSamples();
        long minClearanceBlocks = Long.MAX_VALUE;

        for (SkyIslandGroupPlanV2.IslandComponent component : plan.components()) {
            long lobeMinY = Math.subtractExact(
                    component.lobe().center().yMillionths(),
                    component.lobe().halfExtentsMillionths().yMillionths());
            long clearance = Math.floorDiv(
                    Math.subtractExact(lobeMinY, Math.multiplyExact((long) plan.groundReferenceYBlocks(), M)),
                    M);
            minClearanceBlocks = Math.min(minClearanceBlocks, clearance);
        }

        for (long z = minZ; z <= maxZ; z = Math.addExact(z, step)) {
            for (long y = minY; y <= maxY; y = Math.addExact(y, step)) {
                for (long x = minX; x <= maxX; x = Math.addExact(x, step)) {
                    samples = Math.addExact(samples, 1L);
                    if (samples > maxSamples) {
                        throw new SkyIslandGroupExceptionV2(
                                SkyIslandGroupFailureCodeV2.BUDGET_EXCEEDED,
                                "sky-island-group descriptor sample budget exceeded");
                    }
                    boolean inAnyLobe = false;
                    boolean inAnyUnderside = false;
                    long lobeMaxY = Long.MIN_VALUE;
                    long underMaxY = Long.MIN_VALUE;
                    for (int i = 0; i < plan.components().size(); i++) {
                        String lobeId = sdfPlan.primitives().get(i * 2).primitiveId();
                        String underId = sdfPlan.primitives().get(i * 2 + 1).primitiveId();
                        boolean inLobe = sdfKernel.sampleDistanceMillionths(lobeId, x, y, z) < 0L;
                        boolean inUnder = sdfKernel.sampleDistanceMillionths(underId, x, y, z) < 0L;
                        if (inLobe) {
                            inAnyLobe = true;
                            SkyIslandGroupPlanV2.IslandComponent component = plan.components().get(i);
                            lobeMaxY = Math.max(lobeMaxY, Math.addExact(
                                    component.lobe().center().yMillionths(),
                                    component.lobe().halfExtentsMillionths().yMillionths()));
                            underMaxY = Math.max(underMaxY, Math.addExact(
                                    component.underside().center().yMillionths(),
                                    component.underside().halfExtentsMillionths().yMillionths()));
                        }
                        if (inUnder) {
                            inAnyUnderside = true;
                        }
                    }
                    if (!inAnyLobe && !inAnyUnderside) {
                        continue;
                    }
                    VolumeCsgSampleV2 sample = evaluator.sampleAt(x, y, z);
                    if (sample.occupancy() == VolumeCsgOccupancyV2.SOLID) {
                        solidSamples++;
                        if (y >= underMaxY) {
                            topSamples++;
                        }
                        // Edge: solid near horizontal perimeter of any lobe AABB.
                        if (nearHorizontalEdge(x, z)) {
                            edgeSamples++;
                        }
                    } else if (inAnyUnderside) {
                        undersideSamples++;
                    }
                }
            }
        }

        if (plan.components().size() < plan.kernel().minimumComponentCount()) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.BUDGET_EXCEEDED,
                    "sky-island-group component count below minimum");
        }
        if (solidSamples == 0L || topSamples == 0L) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.THIN_COMPONENT,
                    "sky-island-group missing solid top class");
        }
        if (undersideSamples == 0L) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.OPERATOR_ORDER_CORRUPTION,
                    "sky-island-group missing underside carve class");
        }
        if (minClearanceBlocks < plan.kernel().minimumGroundClearanceBlocks()) {
            throw new SkyIslandGroupExceptionV2(
                    SkyIslandGroupFailureCodeV2.TOUCHING_GROUND,
                    "sky-island-group ground clearance below minimum");
        }

        long minGapBlocks = minimumGapBlocks();
        return new SkyIslandGroupMetricsV2(
                GENERATOR_VERSION,
                plan.components().size(),
                solidSamples,
                topSamples,
                edgeSamples,
                undersideSamples,
                minClearanceBlocks,
                minGapBlocks,
                samples,
                plan.canonicalChecksum());
    }

    public String metricChecksum() {
        SkyIslandGroupMetricsV2 metrics = validate();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(GENERATOR_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.UTF_8));
            digest.update(Integer.toString(metrics.componentCount()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.minGroundClearanceBlocks())
                    .getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.minInterIslandGapBlocks())
                    .getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.sampleCount()).getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean nearHorizontalEdge(long x, long z) {
        long tolerance = M;
        for (SkyIslandGroupPlanV2.IslandComponent component : plan.components()) {
            long minX = Math.subtractExact(
                    component.lobe().center().xMillionths(),
                    component.lobe().halfExtentsMillionths().xMillionths());
            long maxX = Math.addExact(
                    component.lobe().center().xMillionths(),
                    component.lobe().halfExtentsMillionths().xMillionths());
            long minZ = Math.subtractExact(
                    component.lobe().center().zMillionths(),
                    component.lobe().halfExtentsMillionths().zMillionths());
            long maxZ = Math.addExact(
                    component.lobe().center().zMillionths(),
                    component.lobe().halfExtentsMillionths().zMillionths());
            boolean inFootprint = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
            if (!inFootprint) {
                continue;
            }
            if (Math.abs(x - minX) <= tolerance
                    || Math.abs(x - maxX) <= tolerance
                    || Math.abs(z - minZ) <= tolerance
                    || Math.abs(z - maxZ) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    private long minimumGapBlocks() {
        if (plan.components().size() < 2) {
            return Long.MAX_VALUE;
        }
        long minGap = Long.MAX_VALUE;
        for (int i = 0; i < plan.components().size(); i++) {
            for (int j = i + 1; j < plan.components().size(); j++) {
                long gap = horizontalGapBlocks(plan.components().get(i), plan.components().get(j));
                minGap = Math.min(minGap, gap);
            }
        }
        return minGap;
    }

    private static long horizontalGapBlocks(
            SkyIslandGroupPlanV2.IslandComponent a,
            SkyIslandGroupPlanV2.IslandComponent b
    ) {
        long aMinX = Math.subtractExact(
                a.lobe().center().xMillionths(), a.lobe().halfExtentsMillionths().xMillionths());
        long aMaxX = Math.addExact(
                a.lobe().center().xMillionths(), a.lobe().halfExtentsMillionths().xMillionths());
        long aMinZ = Math.subtractExact(
                a.lobe().center().zMillionths(), a.lobe().halfExtentsMillionths().zMillionths());
        long aMaxZ = Math.addExact(
                a.lobe().center().zMillionths(), a.lobe().halfExtentsMillionths().zMillionths());
        long bMinX = Math.subtractExact(
                b.lobe().center().xMillionths(), b.lobe().halfExtentsMillionths().xMillionths());
        long bMaxX = Math.addExact(
                b.lobe().center().xMillionths(), b.lobe().halfExtentsMillionths().xMillionths());
        long bMinZ = Math.subtractExact(
                b.lobe().center().zMillionths(), b.lobe().halfExtentsMillionths().zMillionths());
        long bMaxZ = Math.addExact(
                b.lobe().center().zMillionths(), b.lobe().halfExtentsMillionths().zMillionths());
        long dx = 0L;
        if (aMaxX < bMinX) {
            dx = Math.subtractExact(bMinX, aMaxX);
        } else if (bMaxX < aMinX) {
            dx = Math.subtractExact(aMinX, bMaxX);
        }
        long dz = 0L;
        if (aMaxZ < bMinZ) {
            dz = Math.subtractExact(bMinZ, aMaxZ);
        } else if (bMaxZ < aMinZ) {
            dz = Math.subtractExact(aMinZ, bMaxZ);
        }
        long gap;
        if (dx == 0L && dz == 0L) {
            gap = 0L;
        } else if (dx == 0L) {
            gap = dz;
        } else if (dz == 0L) {
            gap = dx;
        } else {
            gap = Math.min(dx, dz);
        }
        return Math.floorDiv(gap, M);
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

    public record SkyIslandGroupMetricsV2(
            String generatorVersion,
            int componentCount,
            long solidSamples,
            long topSamples,
            long edgeSamples,
            long undersideSamples,
            long minGroundClearanceBlocks,
            long minInterIslandGapBlocks,
            long sampleCount,
            String planChecksum
    ) {
    }
}
