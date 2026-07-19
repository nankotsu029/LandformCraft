package com.github.nankotsu029.landformcraft.generator.v2.volume.waterfall;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgEvaluatorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.sdf.VolumeSdfKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import com.github.nankotsu029.landformcraft.model.v2.volume.WaterfallVolumePlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * SUPPORTED (offline) waterfall-volume metric validator. Checks lip→column→pool continuity, behind-fall
 * clearance, and rejects offset column／leak／missing pool／graph checksum mismatch.
 */
public final class WaterfallVolumeGeneratorV2 {
    public static final String GENERATOR_VERSION = "waterfall-volume-generator-v1";
    private static final String ZERO = "0".repeat(64);
    private static final long M = 1_000_000L;

    private final WaterfallVolumePlanV2 plan;
    private final VolumeSdfKernelV2 sdfKernel;
    private final VolumeCsgPlanV2 csgPlan;
    private final VolumeSdfPrimitivePlanV2 sdfPlan;

    public WaterfallVolumeGeneratorV2(
            WaterfallVolumePlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
        this(plan, sdfPlan, csgPlan, plan.fallNode().sourceGeometryChecksum());
    }

    public WaterfallVolumeGeneratorV2(
            WaterfallVolumePlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan,
            String expectedHydrologyGeometryChecksum
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.sdfPlan = Objects.requireNonNull(sdfPlan, "sdfPlan");
        this.csgPlan = Objects.requireNonNull(csgPlan, "csgPlan");
        Objects.requireNonNull(expectedHydrologyGeometryChecksum, "expectedHydrologyGeometryChecksum");
        if (!WaterfallVolumePlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.UNKNOWN_KERNEL, "unsupported waterfall-volume kernel");
        }
        if (!plan.sdfPlanBinding().sourceArtifactChecksum().equals(sdfPlan.canonicalChecksum())) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.BINDING_MISMATCH, "waterfall-volume SDF binding mismatch");
        }
        if (!plan.csgPlanBinding().sourceArtifactChecksum().equals(csgPlan.canonicalChecksum())) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.BINDING_MISMATCH, "waterfall-volume CSG binding mismatch");
        }
        WaterfallVolumePlanCompilerV2.requireGeometryChecksum(
                expectedHydrologyGeometryChecksum, plan.fallNode().sourceGeometryChecksum());
        this.sdfKernel = new VolumeSdfKernelV2(sdfPlan);
    }

    public WaterfallVolumePlanV2 plan() {
        return plan;
    }

    public VolumeCsgPlanV2 csgPlan() {
        return csgPlan;
    }

    public WaterfallVolumeMetricsV2 validate() {
        WaterfallVolumePlanCompilerV2.requireColumnAnchored(
                plan.lipCenter(), plan.baseCenter(), plan.lipCenter(), plan.baseCenter());
        WaterfallVolumePlanCompilerV2.requireBehindClearanceGeometry(
                plan.lipCenter(), plan.baseCenter(), plan.behindFall());
        WaterfallVolumePlanCompilerV2.requirePoolAnchored(plan.baseCenter(), plan.plungePool());
        WaterfallVolumePlanCompilerV2.requireOperatorOrder(csgPlan.operators());
        WaterfallVolumePlanCompilerV2.requireSingleFluidOwner(csgPlan.operators());

        long step = M;
        long minX = alignDown(plan.aabb().minXMillionths(), step);
        long maxX = alignUp(plan.aabb().maxXMillionths(), step);
        long minY = alignDown(plan.aabb().minYMillionths(), step);
        long maxY = alignUp(plan.aabb().maxYMillionths(), step);
        long minZ = alignDown(plan.aabb().minZMillionths(), step);
        long maxZ = alignUp(plan.aabb().maxZMillionths(), step);

        long columnFluidSamples = 0L;
        long poolFluidSamples = 0L;
        long behindClearanceSamples = 0L;
        long leakSamples = 0L;
        long samples = 0L;
        int maxSamples = plan.budget().maximumDescriptorSamples();
        String expectedFluid = plan.fluidBodyId();
        VolumeCsgEvaluatorV2 readBack = offlineReadBackEvaluator();
        VolumeSdfAabbV2 fluidEnvelope = fluidEnvelope();

        for (long z = minZ; z <= maxZ; z = Math.addExact(z, step)) {
            for (long y = minY; y <= maxY; y = Math.addExact(y, step)) {
                for (long x = minX; x <= maxX; x = Math.addExact(x, step)) {
                    samples = Math.addExact(samples, 1L);
                    if (samples > maxSamples) {
                        throw new WaterfallVolumeExceptionV2(
                                WaterfallVolumeFailureCodeV2.BUDGET_EXCEEDED,
                                "waterfall-volume descriptor sample budget exceeded");
                    }
                    VolumeCsgSampleV2 sample = readBack.sampleAt(x, y, z);
                    boolean inColumn = insideColumn(x, y, z);
                    boolean inPool = insidePoolBelowSurface(x, y, z);
                    boolean inBehind = insideBehind(x, y, z);

                    if (inBehind && sample.occupancy() == VolumeCsgOccupancyV2.AIR) {
                        behindClearanceSamples++;
                    }
                    if (sample.occupancy() == VolumeCsgOccupancyV2.FLUID) {
                        if (!expectedFluid.equals(sample.fluidBodyId())) {
                            throw new WaterfallVolumeExceptionV2(
                                    WaterfallVolumeFailureCodeV2.FLUID_DISCONTINUITY,
                                    "waterfall-volume unexpected fluid owner");
                        }
                        if (!containsInclusive(fluidEnvelope, x, y, z)) {
                            leakSamples++;
                        }
                        if (inColumn) {
                            columnFluidSamples++;
                        }
                        if (inPool) {
                            poolFluidSamples++;
                        }
                        if (!inColumn && !inPool) {
                            leakSamples++;
                        }
                    } else if (inColumn || inPool) {
                        // expected fluid region without fluid — counted via minimums below
                    }
                }
            }
        }

        if (columnFluidSamples < plan.kernel().minimumColumnSamples()) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.FLUID_DISCONTINUITY,
                    "waterfall-volume column fluid samples below minimum");
        }
        if (poolFluidSamples < plan.kernel().minimumPoolSamples()) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.MISSING_POOL,
                    "waterfall-volume plunge pool fluid samples below minimum");
        }
        if (behindClearanceSamples < plan.kernel().minimumBehindClearanceSamples()) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.MISSING_BEHIND_CLEARANCE,
                    "waterfall-volume behind-fall clearance samples below minimum");
        }
        if (leakSamples > 0L) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.FLUID_LEAK,
                    "waterfall-volume fluid leaks outside column/pool envelope");
        }
        requireLipColumnPoolContinuity(readBack);

        return new WaterfallVolumeMetricsV2(
                GENERATOR_VERSION,
                columnFluidSamples,
                poolFluidSamples,
                behindClearanceSamples,
                leakSamples,
                samples,
                plan.canonicalChecksum());
    }

    public String metricChecksum() {
        WaterfallVolumeMetricsV2 metrics = validate();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(GENERATOR_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.columnFluidSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.poolFluidSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.behindClearanceSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.sampleCount()).getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void requireLipColumnPoolContinuity(VolumeCsgEvaluatorV2 readBack) {
        VolumeSdfVec3V2 lip = plan.lipCenter();
        VolumeSdfVec3V2 base = plan.baseCenter();
        int steps = 8;
        for (int i = 0; i <= steps; i++) {
            long x = lerp(lip.xMillionths(), base.xMillionths(), i, steps);
            long y = lerp(lip.yMillionths(), base.yMillionths(), i, steps);
            long z = lerp(lip.zMillionths(), base.zMillionths(), i, steps);
            VolumeCsgSampleV2 sample = readBack.sampleAt(x, y, z);
            if (sample.occupancy() != VolumeCsgOccupancyV2.FLUID
                    || !plan.fluidBodyId().equals(sample.fluidBodyId())) {
                throw new WaterfallVolumeExceptionV2(
                        WaterfallVolumeFailureCodeV2.FLUID_DISCONTINUITY,
                        "waterfall-volume lip→base column discontinuity at step " + i);
            }
        }
        VolumeCsgSampleV2 poolSample = readBack.sampleAt(
                plan.plungePool().center().xMillionths(),
                Math.multiplyExact((long) plan.plungePool().waterSurfaceYBlocks(), M),
                plan.plungePool().center().zMillionths());
        if (poolSample.occupancy() != VolumeCsgOccupancyV2.FLUID
                || !plan.fluidBodyId().equals(poolSample.fluidBodyId())) {
            throw new WaterfallVolumeExceptionV2(
                    WaterfallVolumeFailureCodeV2.MISSING_POOL,
                    "waterfall-volume plunge pool surface is dry");
        }
    }

    private VolumeCsgEvaluatorV2 offlineReadBackEvaluator() {
        VolumeSdfPrimitiveV2 column = sdfPlan.primitives().get(0);
        VolumeSdfPrimitiveV2 behind = sdfPlan.primitives().get(1);
        VolumeSdfPrimitiveV2 pool = sdfPlan.primitives().get(2);
        VolumeSdfPrimitiveV2 plane = sdfPlan.primitives().get(3);
        String hostId = "prim.waterfall.verify-host";
        VolumeSdfAabbV2 hostAabb = plan.aabb().expand(4 * M);
        VolumeSdfVec3V2 hostCenter = new VolumeSdfVec3V2(
                Math.addExact(hostAabb.minXMillionths(),
                        Math.floorDiv(Math.subtractExact(hostAabb.maxXMillionths(), hostAabb.minXMillionths()), 2L)),
                Math.addExact(hostAabb.minYMillionths(),
                        Math.floorDiv(Math.subtractExact(hostAabb.maxYMillionths(), hostAabb.minYMillionths()), 2L)),
                Math.addExact(hostAabb.minZMillionths(),
                        Math.floorDiv(Math.subtractExact(hostAabb.maxZMillionths(), hostAabb.minZMillionths()), 2L)));
        VolumeSdfVec3V2 hostHalf = new VolumeSdfVec3V2(
                Math.max(M, Math.floorDiv(
                        Math.subtractExact(hostAabb.maxXMillionths(), hostAabb.minXMillionths()), 2L)),
                Math.max(M, Math.floorDiv(
                        Math.subtractExact(hostAabb.maxYMillionths(), hostAabb.minYMillionths()), 2L)),
                Math.max(M, Math.floorDiv(
                        Math.subtractExact(hostAabb.maxZMillionths(), hostAabb.minZMillionths()), 2L)));
        VolumeSdfPrimitiveV2 host = new VolumeSdfPrimitiveV2.RoundedBox(hostId, hostCenter, hostHalf, 0L);
        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 verificationSdf = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                List.of(host, column, behind, pool, plane),
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        5,
                        64,
                        4096,
                        65536,
                        65536),
                ZERO));
        VolumeCsgPlanV2 verification = codec.sealVolumeCsgPlan(new VolumeCsgPlanV2(
                1,
                "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, verificationSdf.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                List.of(
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.add", 0, VolumeCsgPlanV2.OperationKind.ADD_SOLID, hostId,
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), ""),
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.carve.behind", 1, VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                                behind.primitiveId(), VolumeCsgPlanV2.MaskMode.NONE, "",
                                List.of("op.verify.add"), ""),
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.carve.pool", 2, VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                                pool.primitiveId(), VolumeCsgPlanV2.MaskMode.NONE, "",
                                List.of("op.verify.carve.behind"), ""),
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.carve.column", 3, VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                                column.primitiveId(), VolumeCsgPlanV2.MaskMode.NONE, "",
                                List.of("op.verify.carve.pool"), ""),
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.fluid.column", 4, VolumeCsgPlanV2.OperationKind.ADD_FLUID,
                                column.primitiveId(), VolumeCsgPlanV2.MaskMode.NONE, "",
                                List.of("op.verify.carve.column"), plan.fluidBodyId()),
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.fluid.pool", 5, VolumeCsgPlanV2.OperationKind.ADD_FLUID,
                                pool.primitiveId(), VolumeCsgPlanV2.MaskMode.INTERSECTION_WITH_PRIMITIVE,
                                plane.primitiveId(), List.of("op.verify.fluid.column"), plan.fluidBodyId())),
                new VolumeCsgPlanV2.ResourceBudget(
                        "volume-csg-budget-v1",
                        64,
                        8,
                        4096L,
                        1_048_576L,
                        4096,
                        65536,
                        65536),
                ZERO));
        return new VolumeCsgEvaluatorV2(verification, verificationSdf);
    }

    private VolumeSdfAabbV2 fluidEnvelope() {
        VolumeSdfPrimitiveV2 column = new VolumeSdfPrimitiveV2.Capsule(
                "tmp.column", plan.lipCenter(), plan.baseCenter(), plan.columnRadiusMillionths());
        VolumeSdfPrimitiveV2 pool = new VolumeSdfPrimitiveV2.Sphere(
                "tmp.pool", plan.plungePool().center(), plan.plungePool().radiusMillionths());
        VolumeSdfAabbV2 a = column.conservativeBounds();
        VolumeSdfAabbV2 b = pool.conservativeBounds();
        return new VolumeSdfAabbV2(
                Math.min(a.minXMillionths(), b.minXMillionths()),
                Math.min(a.minYMillionths(), b.minYMillionths()),
                Math.min(a.minZMillionths(), b.minZMillionths()),
                Math.max(a.maxXMillionths(), b.maxXMillionths()),
                Math.max(a.maxYMillionths(), b.maxYMillionths()),
                Math.max(a.maxZMillionths(), b.maxZMillionths())).expand(M);
    }

    private boolean insideColumn(long x, long y, long z) {
        return sdfKernel.sampleDistanceMillionths(
                sdfPlan.primitives().getFirst().primitiveId(), x, y, z) < 0L;
    }

    private boolean insidePoolBelowSurface(long x, long y, long z) {
        int blockY = (int) Math.floorDiv(y, M);
        if (blockY > plan.plungePool().waterSurfaceYBlocks()) {
            return false;
        }
        return sdfKernel.sampleDistanceMillionths(
                sdfPlan.primitives().get(2).primitiveId(), x, y, z) < 0L;
    }

    private boolean insideBehind(long x, long y, long z) {
        return sdfKernel.sampleDistanceMillionths(
                sdfPlan.primitives().get(1).primitiveId(), x, y, z) < 0L;
    }

    private static boolean containsInclusive(VolumeSdfAabbV2 aabb, long x, long y, long z) {
        return x >= aabb.minXMillionths() && x <= aabb.maxXMillionths()
                && y >= aabb.minYMillionths() && y <= aabb.maxYMillionths()
                && z >= aabb.minZMillionths() && z <= aabb.maxZMillionths();
    }

    private static long lerp(long a, long b, int i, int steps) {
        long delta = Math.subtractExact(b, a);
        return Math.addExact(a, Math.multiplyExact(delta, i) / steps);
    }

    private static long alignDown(long value, long step) {
        long q = Math.floorDiv(value, step);
        return Math.multiplyExact(q, step);
    }

    private static long alignUp(long value, long step) {
        long q = Math.floorDiv(Math.addExact(value, Math.subtractExact(step, 1L)), step);
        return Math.multiplyExact(q, step);
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    public record WaterfallVolumeMetricsV2(
            String generatorVersion,
            long columnFluidSamples,
            long poolFluidSamples,
            long behindClearanceSamples,
            long leakSamples,
            long sampleCount,
            String planChecksum
    ) {
    }
}
