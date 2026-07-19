package com.github.nankotsu029.landformcraft.generator.v2.volume.seacave;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgEvaluatorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.sdf.VolumeSdfKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.SeaCavePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * SUPPORTED (offline) sea-cave metric validator. Checks marine opening, static fluid continuity, roof,
 * and rejects landlocked／inland leak／unsupported host／land-water conflict.
 */
public final class SeaCaveGeneratorV2 {
    public static final String GENERATOR_VERSION = "sea-cave-generator-v1";
    private static final String ZERO = "0".repeat(64);
    private static final long M = 1_000_000L;

    private final SeaCavePlanV2 plan;
    private final VolumeSdfKernelV2 sdfKernel;
    private final VolumeCsgPlanV2 csgPlan;
    private final VolumeSdfPrimitivePlanV2 sdfPlan;

    public SeaCaveGeneratorV2(
            SeaCavePlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.sdfPlan = Objects.requireNonNull(sdfPlan, "sdfPlan");
        this.csgPlan = Objects.requireNonNull(csgPlan, "csgPlan");
        if (!SeaCavePlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.UNKNOWN_KERNEL, "unsupported sea-cave kernel");
        }
        if (!plan.sdfPlanBinding().sourceArtifactChecksum().equals(sdfPlan.canonicalChecksum())) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.BINDING_MISMATCH, "sea-cave SDF binding mismatch");
        }
        if (!plan.csgPlanBinding().sourceArtifactChecksum().equals(csgPlan.canonicalChecksum())) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.BINDING_MISMATCH, "sea-cave CSG binding mismatch");
        }
        this.sdfKernel = new VolumeSdfKernelV2(sdfPlan);
    }

    public SeaCavePlanV2 plan() {
        return plan;
    }

    public VolumeCsgPlanV2 csgPlan() {
        return csgPlan;
    }

    public SeaCaveMetricsV2 validate() {
        SeaCavePlanCompilerV2.requireOpeningOnSeawardFace(
                plan.hostCliff().hostAabb(),
                plan.hostCliff().seawardFace(),
                plan.chamber().openingCenter(),
                plan.chamber().radiusMillionths());
        SeaCavePlanCompilerV2.requireInlandInsideHost(
                plan.hostCliff().hostAabb(),
                plan.chamber().inlandCenter(),
                plan.chamber().radiusMillionths());
        SeaCavePlanCompilerV2.requireInlandFromOpening(
                plan.hostCliff().seawardFace(),
                plan.chamber().openingCenter(),
                plan.chamber().inlandCenter());
        SeaCavePlanCompilerV2.requireSingleFluidOwner(csgPlan.operators());
        SeaCavePlanCompilerV2.requireCarveThenFluid(csgPlan.operators());

        if (!plan.hostCliff().hostAabb().intersects(plan.aabb())) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.UNSUPPORTED_HOST, "sea-cave AABB misses host cliff");
        }

        long step = M;
        long minX = alignDown(plan.aabb().minXMillionths(), step);
        long maxX = alignUp(plan.aabb().maxXMillionths(), step);
        long minY = alignDown(plan.aabb().minYMillionths(), step);
        long maxY = alignUp(plan.aabb().maxYMillionths(), step);
        long minZ = alignDown(plan.aabb().minZMillionths(), step);
        long maxZ = alignUp(plan.aabb().maxZMillionths(), step);

        long carvedSamples = 0L;
        long openingSamples = 0L;
        long fluidSamples = 0L;
        long fluidAtOpening = 0L;
        long leakInlandSamples = 0L;
        long landWaterConflicts = 0L;
        long thinRoofSamples = 0L;
        long breakthroughSamples = 0L;
        long samples = 0L;
        Set<String> fluidOwners = new HashSet<>();
        int seaY = plan.marineBoundary().seaLevelYBlocks();
        int minRoof = plan.kernel().minimumRoofBlocks();
        int surfaceY = plan.surfaceHeightBlocks();
        int maxSamples = plan.budget().maximumDescriptorSamples();
        String expectedFluid = plan.fluidBody().fluidBodyId();
        VolumeCsgEvaluatorV2 readBack = offlineReadBackEvaluator();
        VolumeSdfAabbV2 host = plan.hostCliff().hostAabb();

        for (long z = minZ; z <= maxZ; z = Math.addExact(z, step)) {
            for (long y = minY; y <= maxY; y = Math.addExact(y, step)) {
                for (long x = minX; x <= maxX; x = Math.addExact(x, step)) {
                    samples = Math.addExact(samples, 1L);
                    if (samples > maxSamples) {
                        throw new SeaCaveExceptionV2(
                                SeaCaveFailureCodeV2.BUDGET_EXCEEDED,
                                "sea-cave descriptor sample budget exceeded");
                    }
                    if (!insideChamber(x, y, z)) {
                        continue;
                    }
                    carvedSamples++;
                    int blockY = (int) Math.floorDiv(y, M);
                    boolean nearOpening = nearOpening(x, y, z);
                    if (nearOpening) {
                        openingSamples++;
                    }
                    if (!nearOpening && blockY >= surfaceY) {
                        breakthroughSamples++;
                    } else if (!nearOpening && surfaceY - blockY < minRoof) {
                        thinRoofSamples++;
                    }

                    VolumeCsgSampleV2 sample = readBack.sampleAt(x, y, z);
                    boolean belowSea = blockY <= seaY;
                    if (belowSea) {
                        if (sample.occupancy() != VolumeCsgOccupancyV2.FLUID
                                || !expectedFluid.equals(sample.fluidBodyId())) {
                            throw new SeaCaveExceptionV2(
                                    SeaCaveFailureCodeV2.FLUID_DISCONTINUITY,
                                    "sea-cave offline fluid read-back failed");
                        }
                        fluidSamples++;
                        fluidOwners.add(sample.fluidBodyId());
                        if (nearOpening) {
                            fluidAtOpening++;
                        }
                        if (isInlandLeak(x, y, z, host)) {
                            leakInlandSamples++;
                        }
                    } else if (sample.occupancy() == VolumeCsgOccupancyV2.FLUID) {
                        landWaterConflicts++;
                    }
                }
            }
        }

        if (carvedSamples == 0L) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.UNSUPPORTED_HOST, "sea-cave carve is empty");
        }
        if (openingSamples < plan.kernel().minimumOpeningBlocks()) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.MISSING_SEA_OPENING,
                    "sea opening samples below minimum");
        }
        if (fluidSamples == 0L || fluidAtOpening == 0L) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.FLUID_DISCONTINUITY,
                    "sea-cave fluid does not reach the marine opening");
        }
        if (leakInlandSamples > 0L) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.LEAKING_INLAND,
                    "sea-cave fluid leaks inland beyond host cliff");
        }
        if (landWaterConflicts > 0L) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.HARD_LAND_WATER_CONFLICT,
                    "sea-cave fluid above sea level");
        }
        if (fluidOwners.size() != 1 || !fluidOwners.contains(expectedFluid)) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.DOUBLE_FLUID_OWNER, "sea-cave fluid ownership invalid");
        }
        if (thinRoofSamples > 0) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.THIN_ROOF,
                    "sea-cave roof thinner than minimum at " + thinRoofSamples + " samples");
        }
        if (breakthroughSamples > 0) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.SURFACE_BREAKTHROUGH,
                    "sea-cave undeclared surface breakthrough");
        }

        return new SeaCaveMetricsV2(
                GENERATOR_VERSION,
                carvedSamples,
                openingSamples,
                fluidSamples,
                fluidAtOpening,
                leakInlandSamples,
                thinRoofSamples,
                breakthroughSamples,
                samples,
                plan.canonicalChecksum());
    }

    public String metricChecksum() {
        SeaCaveMetricsV2 metrics = validate();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(GENERATOR_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.openingSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.fluidSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.sampleCount()).getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private VolumeCsgEvaluatorV2 offlineReadBackEvaluator() {
        String chamberId = sdfPlan.primitives().getFirst().primitiveId();
        String planeId = sdfPlan.primitives().get(1).primitiveId();
        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeCsgPlanV2 verification = codec.sealVolumeCsgPlan(new VolumeCsgPlanV2(
                1,
                "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, sdfPlan.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                List.of(
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.add", 0, VolumeCsgPlanV2.OperationKind.ADD_SOLID, chamberId,
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), ""),
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.carve", 1, VolumeCsgPlanV2.OperationKind.CARVE_SOLID, chamberId,
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of("op.verify.add"), ""),
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.fluid", 2, VolumeCsgPlanV2.OperationKind.ADD_FLUID, chamberId,
                                VolumeCsgPlanV2.MaskMode.INTERSECTION_WITH_PRIMITIVE, planeId,
                                List.of("op.verify.carve"), plan.fluidBody().fluidBodyId())),
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
        return new VolumeCsgEvaluatorV2(verification, sdfPlan);
    }

    private boolean nearOpening(long x, long y, long z) {
        VolumeSdfVec3V2 opening = plan.chamber().openingCenter();
        long r = Math.addExact(plan.chamber().radiusMillionths(), M);
        long dx = Math.subtractExact(x, opening.xMillionths());
        long dy = Math.subtractExact(y, opening.yMillionths());
        long dz = Math.subtractExact(z, opening.zMillionths());
        try {
            long dist2 = Math.addExact(
                    Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dy, dy)),
                    Math.multiplyExact(dz, dz));
            return dist2 <= Math.multiplyExact(r, r);
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    private boolean isInlandLeak(long x, long y, long z, VolumeSdfAabbV2 host) {
        // Fluid outside the host cliff inland of the seaward face is a leak.
        return switch (plan.hostCliff().seawardFace()) {
            case WEST -> x > host.maxXMillionths();
            case EAST -> x < host.minXMillionths();
            case NORTH -> z > host.maxZMillionths();
            case SOUTH -> z < host.minZMillionths();
        };
    }

    private boolean insideChamber(long x, long y, long z) {
        String chamberId = sdfPlan.primitives().getFirst().primitiveId();
        return sdfKernel.sampleDistanceMillionths(chamberId, x, y, z) < 0L;
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

    public record SeaCaveMetricsV2(
            String generatorVersion,
            long carvedSamples,
            long openingSamples,
            long fluidSamples,
            long fluidAtOpening,
            long leakInlandSamples,
            long thinRoofSamples,
            long breakthroughSamples,
            long sampleCount,
            String planChecksum
    ) {
    }
}
