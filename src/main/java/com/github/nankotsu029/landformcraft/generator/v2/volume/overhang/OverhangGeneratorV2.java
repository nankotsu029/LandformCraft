package com.github.nankotsu029.landformcraft.generator.v2.volume.overhang;

import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgEvaluatorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.sdf.VolumeSdfKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.OverhangPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * SUPPORTED (offline) overhang metric validator. Checks support, roof thickness, projection,
 * underside clearance, and seaward opening; rejects floating／thin／blocked corruption.
 */
public final class OverhangGeneratorV2 {
    public static final String GENERATOR_VERSION = "overhang-generator-v1";
    private static final long M = 1_000_000L;

    private final OverhangPlanV2 plan;
    private final VolumeSdfKernelV2 sdfKernel;
    private final VolumeCsgPlanV2 csgPlan;
    private final VolumeSdfPrimitivePlanV2 sdfPlan;
    private final VolumeCsgEvaluatorV2 evaluator;

    public OverhangGeneratorV2(
            OverhangPlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.sdfPlan = Objects.requireNonNull(sdfPlan, "sdfPlan");
        this.csgPlan = Objects.requireNonNull(csgPlan, "csgPlan");
        if (!OverhangPlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.UNKNOWN_KERNEL, "unsupported overhang kernel");
        }
        if (!plan.sdfPlanBinding().sourceArtifactChecksum().equals(sdfPlan.canonicalChecksum())) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.BINDING_MISMATCH, "overhang SDF binding mismatch");
        }
        if (!plan.csgPlanBinding().sourceArtifactChecksum().equals(csgPlan.canonicalChecksum())) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.BINDING_MISMATCH, "overhang CSG binding mismatch");
        }
        this.sdfKernel = new VolumeSdfKernelV2(sdfPlan);
        this.evaluator = new VolumeCsgEvaluatorV2(csgPlan, sdfPlan);
    }

    public OverhangPlanV2 plan() {
        return plan;
    }

    public VolumeCsgPlanV2 csgPlan() {
        return csgPlan;
    }

    public OverhangMetricsV2 validate() {
        OverhangPlanCompilerV2.requireLobeTouchesHostFace(
                plan.hostCliff().hostAabb(), plan.hostCliff().seawardFace(), plan.lobe());
        OverhangPlanCompilerV2.requireLobeProjectsSeaward(
                plan.hostCliff().hostAabb(),
                plan.hostCliff().seawardFace(),
                plan.lobe(),
                plan.kernel().minimumProjectionBlocks());
        OverhangPlanCompilerV2.requireRecessUnderLobe(
                plan.lobe(), plan.recess(), plan.kernel().minimumRoofBlocks());
        OverhangPlanCompilerV2.requireRecessOpensSeaward(
                plan.hostCliff().hostAabb(), plan.hostCliff().seawardFace(), plan.recess());
        OverhangPlanCompilerV2.requireAddThenCarve(csgPlan.operators());

        if (!plan.hostCliff().hostAabb().intersects(plan.aabb())
                && !touchesHostFace(plan.hostCliff().hostAabb(), plan.aabb(),
                plan.hostCliff().seawardFace())) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.UNSUPPORTED_HOST, "overhang AABB misses host cliff");
        }

        long step = M;
        long minX = alignDown(plan.aabb().minXMillionths(), step);
        long maxX = alignUp(plan.aabb().maxXMillionths(), step);
        long minY = alignDown(plan.aabb().minYMillionths(), step);
        long maxY = alignUp(plan.aabb().maxYMillionths(), step);
        long minZ = alignDown(plan.aabb().minZMillionths(), step);
        long maxZ = alignUp(plan.aabb().maxZMillionths(), step);

        long solidSamples = 0L;
        long supportSamples = 0L;
        long roofSamples = 0L;
        long clearanceSamples = 0L;
        long seawardOpeningSamples = 0L;
        long thinRoofSamples = 0L;
        long samples = 0L;
        int minRoof = plan.kernel().minimumRoofBlocks();
        int maxSamples = plan.budget().maximumDescriptorSamples();
        String lobeId = sdfPlan.primitives().getFirst().primitiveId();
        String recessId = sdfPlan.primitives().get(1).primitiveId();
        VolumeSdfAabbV2 host = plan.hostCliff().hostAabb();
        long lobeMaxY = Math.addExact(
                plan.lobe().center().yMillionths(),
                plan.lobe().halfExtentsMillionths().yMillionths());
        long recessMaxY = Math.addExact(
                plan.recess().center().yMillionths(),
                plan.recess().halfExtentsMillionths().yMillionths());
        long roofThicknessBlocks = Math.floorDiv(Math.subtractExact(lobeMaxY, recessMaxY), M);
        if (roofThicknessBlocks < minRoof) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.THIN_ROOF,
                    "overhang roof thinner than minimum");
        }

        for (long z = minZ; z <= maxZ; z = Math.addExact(z, step)) {
            for (long y = minY; y <= maxY; y = Math.addExact(y, step)) {
                for (long x = minX; x <= maxX; x = Math.addExact(x, step)) {
                    samples = Math.addExact(samples, 1L);
                    if (samples > maxSamples) {
                        throw new OverhangExceptionV2(
                                OverhangFailureCodeV2.BUDGET_EXCEEDED,
                                "overhang descriptor sample budget exceeded");
                    }
                    boolean inLobe = sdfKernel.sampleDistanceMillionths(lobeId, x, y, z) < 0L;
                    boolean inRecess = sdfKernel.sampleDistanceMillionths(recessId, x, y, z) < 0L;
                    if (!inLobe && !inRecess) {
                        continue;
                    }
                    VolumeCsgSampleV2 sample = evaluator.sampleAt(x, y, z);
                    if (sample.occupancy() == VolumeCsgOccupancyV2.SOLID) {
                        solidSamples++;
                        if (nearHostSupport(x, y, z, host)) {
                            supportSamples++;
                        }
                        if (inLobe && y >= recessMaxY) {
                            roofSamples++;
                        }
                    } else if (inRecess) {
                        clearanceSamples++;
                        if (nearSeawardOpening(x, y, z, host)) {
                            seawardOpeningSamples++;
                        }
                    }
                    if (inLobe && !inRecess && y >= recessMaxY
                            && Math.floorDiv(Math.subtractExact(lobeMaxY, y), M) < minRoof
                            && sample.occupancy() != VolumeCsgOccupancyV2.SOLID) {
                        thinRoofSamples++;
                    }
                }
            }
        }

        if (solidSamples == 0L) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.UNSUPPORTED_HOST, "overhang solid lobe is empty");
        }
        if (supportSamples < plan.kernel().minimumSupportSamples()) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.FLOATING_SLAB,
                    "overhang support samples below minimum");
        }
        if (roofSamples == 0L) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.THIN_ROOF, "overhang roof slab missing after carve");
        }
        if (thinRoofSamples > 0L) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.THIN_ROOF,
                    "overhang roof thinner than minimum at " + thinRoofSamples + " samples");
        }
        if (clearanceSamples < plan.kernel().minimumClearanceSamples()) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.BLOCKED_CORRIDOR,
                    "overhang underside clearance samples below minimum");
        }
        if (seawardOpeningSamples == 0L) {
            throw new OverhangExceptionV2(
                    OverhangFailureCodeV2.MISSING_SEAWARD_OPENING,
                    "overhang recess has no seaward opening samples");
        }

        long projectionBlocks = projectionBlocks(host, plan.hostCliff().seawardFace(), plan.lobe());
        return new OverhangMetricsV2(
                GENERATOR_VERSION,
                solidSamples,
                supportSamples,
                roofSamples,
                clearanceSamples,
                seawardOpeningSamples,
                projectionBlocks,
                roofThicknessBlocks,
                samples,
                plan.canonicalChecksum());
    }

    public String metricChecksum() {
        OverhangMetricsV2 metrics = validate();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(GENERATOR_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.supportSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.clearanceSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.sampleCount()).getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean nearHostSupport(long x, long y, long z, VolumeSdfAabbV2 host) {
        long tolerance = M;
        return switch (plan.hostCliff().seawardFace()) {
            case WEST -> Math.abs(x - host.minXMillionths()) <= tolerance
                    && inRange(y, host.minYMillionths(), host.maxYMillionths())
                    && inRange(z, host.minZMillionths(), host.maxZMillionths());
            case EAST -> Math.abs(x - host.maxXMillionths()) <= tolerance
                    && inRange(y, host.minYMillionths(), host.maxYMillionths())
                    && inRange(z, host.minZMillionths(), host.maxZMillionths());
            case NORTH -> Math.abs(z - host.minZMillionths()) <= tolerance
                    && inRange(x, host.minXMillionths(), host.maxXMillionths())
                    && inRange(y, host.minYMillionths(), host.maxYMillionths());
            case SOUTH -> Math.abs(z - host.maxZMillionths()) <= tolerance
                    && inRange(x, host.minXMillionths(), host.maxXMillionths())
                    && inRange(y, host.minYMillionths(), host.maxYMillionths());
        };
    }

    private boolean nearSeawardOpening(long x, long y, long z, VolumeSdfAabbV2 host) {
        long tipTolerance = Math.multiplyExact(2L, M);
        return switch (plan.hostCliff().seawardFace()) {
            case WEST -> x <= Math.subtractExact(host.minXMillionths(), M)
                    && Math.abs(x - plan.aabb().minXMillionths()) <= tipTolerance;
            case EAST -> x >= Math.addExact(host.maxXMillionths(), M)
                    && Math.abs(x - plan.aabb().maxXMillionths()) <= tipTolerance;
            case NORTH -> z <= Math.subtractExact(host.minZMillionths(), M)
                    && Math.abs(z - plan.aabb().minZMillionths()) <= tipTolerance;
            case SOUTH -> z >= Math.addExact(host.maxZMillionths(), M)
                    && Math.abs(z - plan.aabb().maxZMillionths()) <= tipTolerance;
        };
    }

    private static boolean touchesHostFace(
            VolumeSdfAabbV2 host,
            VolumeSdfAabbV2 feature,
            OverhangPlanV2.CardinalFace face
    ) {
        long tolerance = M;
        return switch (face) {
            case WEST -> Math.abs(feature.maxXMillionths() - host.minXMillionths()) <= tolerance;
            case EAST -> Math.abs(feature.minXMillionths() - host.maxXMillionths()) <= tolerance;
            case NORTH -> Math.abs(feature.maxZMillionths() - host.minZMillionths()) <= tolerance;
            case SOUTH -> Math.abs(feature.minZMillionths() - host.maxZMillionths()) <= tolerance;
        };
    }

    private static long projectionBlocks(
            VolumeSdfAabbV2 host,
            OverhangPlanV2.CardinalFace face,
            OverhangPlanV2.LobeSpec lobe
    ) {
        long projection = switch (face) {
            case WEST -> Math.subtractExact(host.minXMillionths(),
                    Math.subtractExact(lobe.center().xMillionths(),
                            lobe.halfExtentsMillionths().xMillionths()));
            case EAST -> Math.subtractExact(
                    Math.addExact(lobe.center().xMillionths(),
                            lobe.halfExtentsMillionths().xMillionths()),
                    host.maxXMillionths());
            case NORTH -> Math.subtractExact(host.minZMillionths(),
                    Math.subtractExact(lobe.center().zMillionths(),
                            lobe.halfExtentsMillionths().zMillionths()));
            case SOUTH -> Math.subtractExact(
                    Math.addExact(lobe.center().zMillionths(),
                            lobe.halfExtentsMillionths().zMillionths()),
                    host.maxZMillionths());
        };
        return Math.floorDiv(projection, M);
    }

    private static boolean inRange(long value, long min, long max) {
        return value >= min && value <= max;
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

    public record OverhangMetricsV2(
            String generatorVersion,
            long solidSamples,
            long supportSamples,
            long roofSamples,
            long clearanceSamples,
            long seawardOpeningSamples,
            long projectionBlocks,
            long roofThicknessBlocks,
            long sampleCount,
            String planChecksum
    ) {
    }
}
