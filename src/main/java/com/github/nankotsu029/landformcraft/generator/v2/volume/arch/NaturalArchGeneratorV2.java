package com.github.nankotsu029.landformcraft.generator.v2.volume.arch;

import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgEvaluatorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.sdf.VolumeSdfKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.NaturalArchPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * SUPPORTED (offline) natural-arch metric validator. Checks two piers, crown thickness, span,
 * clearance corridor, and through opening; rejects one-pier／thin／closed corruption.
 */
public final class NaturalArchGeneratorV2 {
    public static final String GENERATOR_VERSION = "natural-arch-generator-v1";
    private static final long M = 1_000_000L;

    private final NaturalArchPlanV2 plan;
    private final VolumeSdfKernelV2 sdfKernel;
    private final VolumeCsgPlanV2 csgPlan;
    private final VolumeSdfPrimitivePlanV2 sdfPlan;
    private final VolumeCsgEvaluatorV2 evaluator;

    public NaturalArchGeneratorV2(
            NaturalArchPlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.sdfPlan = Objects.requireNonNull(sdfPlan, "sdfPlan");
        this.csgPlan = Objects.requireNonNull(csgPlan, "csgPlan");
        if (!NaturalArchPlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.UNKNOWN_KERNEL, "unsupported natural-arch kernel");
        }
        if (!plan.sdfPlanBinding().sourceArtifactChecksum().equals(sdfPlan.canonicalChecksum())) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.BINDING_MISMATCH, "natural-arch SDF binding mismatch");
        }
        if (!plan.csgPlanBinding().sourceArtifactChecksum().equals(csgPlan.canonicalChecksum())) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.BINDING_MISMATCH, "natural-arch CSG binding mismatch");
        }
        this.sdfKernel = new VolumeSdfKernelV2(sdfPlan);
        this.evaluator = new VolumeCsgEvaluatorV2(csgPlan, sdfPlan);
    }

    public NaturalArchPlanV2 plan() {
        return plan;
    }

    public VolumeCsgPlanV2 csgPlan() {
        return csgPlan;
    }

    public NaturalArchMetricsV2 validate() {
        NaturalArchPlanCompilerV2.requireThroughOpening(
                plan.passageAxis(), plan.mass(), plan.opening());
        NaturalArchPlanCompilerV2.requireTwoPiers(
                plan.passageAxis(), plan.mass(), plan.opening(), plan.kernel().minimumPierBlocks());
        NaturalArchPlanCompilerV2.requireCrown(
                plan.mass(), plan.opening(), plan.kernel().minimumCrownBlocks());
        NaturalArchPlanCompilerV2.requireSpan(
                plan.passageAxis(), plan.mass(), plan.opening(), plan.kernel().minimumSpanBlocks());
        NaturalArchPlanCompilerV2.requireAddThenCarve(csgPlan.operators());

        long step = M;
        long minX = alignDown(plan.aabb().minXMillionths(), step);
        long maxX = alignUp(plan.aabb().maxXMillionths(), step);
        long minY = alignDown(plan.aabb().minYMillionths(), step);
        long maxY = alignUp(plan.aabb().maxYMillionths(), step);
        long minZ = alignDown(plan.aabb().minZMillionths(), step);
        long maxZ = alignUp(plan.aabb().maxZMillionths(), step);

        long solidSamples = 0L;
        long leftPierSamples = 0L;
        long rightPierSamples = 0L;
        long crownSamples = 0L;
        long clearanceSamples = 0L;
        long openingFaceA = 0L;
        long openingFaceB = 0L;
        long samples = 0L;
        int maxSamples = plan.budget().maximumDescriptorSamples();
        String massId = sdfPlan.primitives().getFirst().primitiveId();
        String openingId = sdfPlan.primitives().get(1).primitiveId();

        long massMinX = Math.subtractExact(
                plan.mass().center().xMillionths(), plan.mass().halfExtentsMillionths().xMillionths());
        long massMaxX = Math.addExact(
                plan.mass().center().xMillionths(), plan.mass().halfExtentsMillionths().xMillionths());
        long massMinZ = Math.subtractExact(
                plan.mass().center().zMillionths(), plan.mass().halfExtentsMillionths().zMillionths());
        long massMaxZ = Math.addExact(
                plan.mass().center().zMillionths(), plan.mass().halfExtentsMillionths().zMillionths());
        long openMinX = Math.subtractExact(
                plan.opening().center().xMillionths(),
                plan.opening().halfExtentsMillionths().xMillionths());
        long openMaxX = Math.addExact(
                plan.opening().center().xMillionths(),
                plan.opening().halfExtentsMillionths().xMillionths());
        long openMinZ = Math.subtractExact(
                plan.opening().center().zMillionths(),
                plan.opening().halfExtentsMillionths().zMillionths());
        long openMaxZ = Math.addExact(
                plan.opening().center().zMillionths(),
                plan.opening().halfExtentsMillionths().zMillionths());
        long openMaxY = Math.addExact(
                plan.opening().center().yMillionths(),
                plan.opening().halfExtentsMillionths().yMillionths());
        long massMaxY = Math.addExact(
                plan.mass().center().yMillionths(), plan.mass().halfExtentsMillionths().yMillionths());
        long crownThicknessBlocks = Math.floorDiv(Math.subtractExact(massMaxY, openMaxY), M);
        long spanBlocks = switch (plan.passageAxis()) {
            case Z -> Math.floorDiv(
                    Math.multiplyExact(2L, plan.opening().halfExtentsMillionths().xMillionths()), M);
            case X -> Math.floorDiv(
                    Math.multiplyExact(2L, plan.opening().halfExtentsMillionths().zMillionths()), M);
        };

        for (long z = minZ; z <= maxZ; z = Math.addExact(z, step)) {
            for (long y = minY; y <= maxY; y = Math.addExact(y, step)) {
                for (long x = minX; x <= maxX; x = Math.addExact(x, step)) {
                    samples = Math.addExact(samples, 1L);
                    if (samples > maxSamples) {
                        throw new NaturalArchExceptionV2(
                                NaturalArchFailureCodeV2.BUDGET_EXCEEDED,
                                "natural-arch descriptor sample budget exceeded");
                    }
                    boolean inMass = sdfKernel.sampleDistanceMillionths(massId, x, y, z) < 0L;
                    boolean inOpening = sdfKernel.sampleDistanceMillionths(openingId, x, y, z) < 0L;
                    if (!inMass && !inOpening) {
                        continue;
                    }
                    VolumeCsgSampleV2 sample = evaluator.sampleAt(x, y, z);
                    if (sample.occupancy() == VolumeCsgOccupancyV2.SOLID) {
                        solidSamples++;
                        if (y >= openMaxY) {
                            crownSamples++;
                        }
                        if (plan.passageAxis() == NaturalArchPlanV2.PassageAxis.Z) {
                            if (x < openMinX && x >= massMinX) {
                                leftPierSamples++;
                            } else if (x > openMaxX && x <= massMaxX) {
                                rightPierSamples++;
                            }
                        } else {
                            if (z < openMinZ && z >= massMinZ) {
                                leftPierSamples++;
                            } else if (z > openMaxZ && z <= massMaxZ) {
                                rightPierSamples++;
                            }
                        }
                    } else if (inOpening) {
                        clearanceSamples++;
                        if (plan.passageAxis() == NaturalArchPlanV2.PassageAxis.Z) {
                            if (z <= massMinZ) {
                                openingFaceA++;
                            }
                            if (z >= massMaxZ) {
                                openingFaceB++;
                            }
                        } else {
                            if (x <= massMinX) {
                                openingFaceA++;
                            }
                            if (x >= massMaxX) {
                                openingFaceB++;
                            }
                        }
                    }
                }
            }
        }

        if (solidSamples == 0L) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.ONE_PIER, "natural-arch solid mass is empty");
        }
        if (leftPierSamples == 0L || rightPierSamples == 0L) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.ONE_PIER, "natural-arch missing a pier solid");
        }
        if (crownSamples == 0L || crownThicknessBlocks < plan.kernel().minimumCrownBlocks()) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.THIN_CROWN, "natural-arch crown missing or thin");
        }
        if (clearanceSamples < plan.kernel().minimumClearanceSamples()) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.CLOSED_OPENING,
                    "natural-arch clearance samples below minimum");
        }
        if (openingFaceA == 0L || openingFaceB == 0L) {
            throw new NaturalArchExceptionV2(
                    NaturalArchFailureCodeV2.CLOSED_OPENING,
                    "natural-arch opening is closed on a passage face");
        }

        return new NaturalArchMetricsV2(
                GENERATOR_VERSION,
                solidSamples,
                leftPierSamples,
                rightPierSamples,
                crownSamples,
                clearanceSamples,
                spanBlocks,
                crownThicknessBlocks,
                samples,
                plan.canonicalChecksum());
    }

    public String metricChecksum() {
        NaturalArchMetricsV2 metrics = validate();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(GENERATOR_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.leftPierSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.rightPierSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.clearanceSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.sampleCount()).getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
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

    public record NaturalArchMetricsV2(
            String generatorVersion,
            long solidSamples,
            long leftPierSamples,
            long rightPierSamples,
            long crownSamples,
            long clearanceSamples,
            long spanBlocks,
            long crownThicknessBlocks,
            long sampleCount,
            String planChecksum
    ) {
    }
}
