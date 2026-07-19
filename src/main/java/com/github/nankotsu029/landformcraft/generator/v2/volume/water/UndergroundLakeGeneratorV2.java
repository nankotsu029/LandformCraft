package com.github.nankotsu029.landformcraft.generator.v2.volume.water;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgEvaluatorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.sdf.VolumeSdfKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.UndergroundLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
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
 * SUPPORTED (offline) underground-lake metric validator. Checks WITHIN／REACHABLE_FROM, single
 * {@code ADD_FLUID} ownership, contained fluid, air cavity, rim, and offline CSG read-back.
 */
public final class UndergroundLakeGeneratorV2 {
    public static final String GENERATOR_VERSION = "underground-lake-generator-v1";
    private static final String ZERO = "0".repeat(64);
    private static final long M = 1_000_000L;

    private final UndergroundLakePlanV2 plan;
    private final CaveNetworkPlanV2 hostNetwork;
    private final VolumeSdfKernelV2 lakeSdf;
    private final VolumeSdfKernelV2 hostSdf;
    private final VolumeCsgPlanV2 csgPlan;
    private final VolumeSdfPrimitivePlanV2 lakeSdfPlan;

    public UndergroundLakeGeneratorV2(
            UndergroundLakePlanV2 plan,
            CaveNetworkPlanV2 hostNetwork,
            VolumeSdfPrimitivePlanV2 lakeSdfPlan,
            VolumeSdfPrimitivePlanV2 hostSdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.hostNetwork = Objects.requireNonNull(hostNetwork, "hostNetwork");
        this.lakeSdfPlan = Objects.requireNonNull(lakeSdfPlan, "lakeSdfPlan");
        Objects.requireNonNull(hostSdfPlan, "hostSdfPlan");
        this.csgPlan = Objects.requireNonNull(csgPlan, "csgPlan");
        if (!UndergroundLakePlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.UNKNOWN_KERNEL, "unsupported underground-lake kernel");
        }
        if (!plan.hostBinding().hostNetworkPlanChecksum().equals(hostNetwork.canonicalChecksum())
                || !plan.hostBinding().hostNetworkFeatureId().equals(hostNetwork.featureId())) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.HOST_MISMATCH, "lake host network binding mismatch");
        }
        if (!plan.sdfPlanBinding().sourceArtifactChecksum().equals(lakeSdfPlan.canonicalChecksum())) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.BINDING_MISMATCH, "lake SDF plan binding mismatch");
        }
        if (!plan.csgPlanBinding().sourceArtifactChecksum().equals(csgPlan.canonicalChecksum())) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.BINDING_MISMATCH, "lake CSG plan binding mismatch");
        }
        this.lakeSdf = new VolumeSdfKernelV2(lakeSdfPlan);
        this.hostSdf = new VolumeSdfKernelV2(hostSdfPlan);
    }

    public UndergroundLakePlanV2 plan() {
        return plan;
    }

    public VolumeCsgPlanV2 csgPlan() {
        return csgPlan;
    }

    public UndergroundLakeMetricsV2 validate() {
        UndergroundLakePlanCompilerV2.requireHostChamber(
                hostNetwork, plan.hostBinding().hostChamberNodeId());
        UndergroundLakePlanCompilerV2.requireReachable(
                hostNetwork,
                plan.caveAccess().entranceNodeId(),
                plan.hostBinding().hostChamberNodeId());
        UndergroundLakePlanCompilerV2.requireSingleFluidOwner(csgPlan.operators());
        UndergroundLakePlanCompilerV2.requireCarveThenFluid(csgPlan.operators());

        VolumeSdfVec3V2 center = plan.basin().center();
        if (!insideHost(center.xMillionths(), center.yMillionths(), center.zMillionths())) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.NETWORK_CONTAINMENT_FAILED,
                    "lake basin center is outside host network carve");
        }

        long step = M;
        long minX = alignDown(plan.aabb().minXMillionths(), step);
        long maxX = alignUp(plan.aabb().maxXMillionths(), step);
        long minY = alignDown(plan.aabb().minYMillionths(), step);
        long maxY = alignUp(plan.aabb().maxYMillionths(), step);
        long minZ = alignDown(plan.aabb().minZMillionths(), step);
        long maxZ = alignUp(plan.aabb().maxZMillionths(), step);

        long cavitySamples = 0L;
        long fluidSamples = 0L;
        long airCavitySamples = 0L;
        long containedFluidSamples = 0L;
        long leakSamples = 0L;
        long rimSamples = 0L;
        long thinRoofSamples = 0L;
        long breakthroughSamples = 0L;
        long samples = 0L;
        Set<String> fluidOwners = new HashSet<>();
        int waterY = plan.fluidBody().waterSurfaceYBlocks();
        int minRoof = plan.kernel().minimumRoofBlocks();
        int surfaceY = plan.surfaceHeightBlocks();
        int maxSamples = plan.budget().maximumDescriptorSamples();
        String expectedFluid = plan.fluidBody().fluidBodyId();

        VolumeCsgEvaluatorV2 readBack = offlineReadBackEvaluator();

        for (long z = minZ; z <= maxZ; z = Math.addExact(z, step)) {
            for (long y = minY; y <= maxY; y = Math.addExact(y, step)) {
                for (long x = minX; x <= maxX; x = Math.addExact(x, step)) {
                    samples = Math.addExact(samples, 1L);
                    if (samples > maxSamples) {
                        throw new UndergroundLakeExceptionV2(
                                UndergroundLakeFailureCodeV2.BUDGET_EXCEEDED,
                                "lake descriptor sample budget exceeded");
                    }
                    if (!insideBasin(x, y, z)) {
                        continue;
                    }
                    cavitySamples++;
                    int blockY = (int) Math.floorDiv(y, M);
                    if (blockY >= surfaceY) {
                        breakthroughSamples++;
                    } else if (surfaceY - blockY < minRoof) {
                        thinRoofSamples++;
                    }

                    VolumeCsgSampleV2 sample = readBack.sampleAt(x, y, z);
                    boolean belowWater = blockY <= waterY;
                    if (belowWater) {
                        fluidSamples++;
                        if (sample.occupancy() != VolumeCsgOccupancyV2.FLUID
                                || !expectedFluid.equals(sample.fluidBodyId())) {
                            throw new UndergroundLakeExceptionV2(
                                    UndergroundLakeFailureCodeV2.UNCONTAINED_FLUID,
                                    "offline read-back missing contained fluid");
                        }
                        fluidOwners.add(sample.fluidBodyId());
                        if (insideHost(x, y, z)) {
                            containedFluidSamples++;
                        } else {
                            leakSamples++;
                        }
                        if (isRim(x, y, z)) {
                            rimSamples++;
                        }
                    } else {
                        airCavitySamples++;
                        if (sample.occupancy() != VolumeCsgOccupancyV2.AIR) {
                            throw new UndergroundLakeExceptionV2(
                                    UndergroundLakeFailureCodeV2.MISSING_AIR_CAVITY,
                                    "offline read-back missing air cavity above water");
                        }
                    }
                }
            }
        }

        if (cavitySamples == 0L || fluidSamples == 0L) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.ORPHAN_BASIN, "lake basin carve/fluid empty");
        }
        if (airCavitySamples < plan.basin().minimumAirCavityBlocks()) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.MISSING_AIR_CAVITY,
                    "air cavity samples below minimum");
        }
        if (containedFluidSamples * 100L < fluidSamples * 80L || leakSamples > 0L) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.LEAKING_FLUID,
                    "lake fluid leaks outside host network");
        }
        if (fluidOwners.size() != 1 || !fluidOwners.contains(expectedFluid)) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.DOUBLE_FLUID_OWNER,
                    "lake fluid ownership is not unique");
        }
        if (rimSamples <= 0L) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.RIM_FAILURE, "lake rim samples missing");
        }
        if (thinRoofSamples > 0) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.THIN_ROOF,
                    "lake roof thinner than minimum at " + thinRoofSamples + " samples");
        }
        if (breakthroughSamples > 0) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.SURFACE_BREAKTHROUGH,
                    "lake surface breakthrough at " + breakthroughSamples + " samples");
        }

        return new UndergroundLakeMetricsV2(
                GENERATOR_VERSION,
                cavitySamples,
                fluidSamples,
                airCavitySamples,
                containedFluidSamples,
                rimSamples,
                leakSamples,
                thinRoofSamples,
                breakthroughSamples,
                samples,
                plan.canonicalChecksum());
    }

    public String metricChecksum() {
        UndergroundLakeMetricsV2 metrics = validate();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(GENERATOR_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.fluidSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.airCavitySamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.rimSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.sampleCount()).getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private VolumeCsgEvaluatorV2 offlineReadBackEvaluator() {
        String basinId = lakeSdfPlan.primitives().getFirst().primitiveId();
        String planeId = lakeSdfPlan.primitives().get(1).primitiveId();
        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeCsgPlanV2 verification = codec.sealVolumeCsgPlan(new VolumeCsgPlanV2(
                1,
                "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, lakeSdfPlan.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                List.of(
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.add", 0, VolumeCsgPlanV2.OperationKind.ADD_SOLID, basinId,
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), ""),
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.carve", 1, VolumeCsgPlanV2.OperationKind.CARVE_SOLID, basinId,
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of("op.verify.add"), ""),
                        new VolumeCsgPlanV2.Operator(
                                "op.verify.fluid", 2, VolumeCsgPlanV2.OperationKind.ADD_FLUID, basinId,
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
        return new VolumeCsgEvaluatorV2(verification, lakeSdfPlan);
    }

    private boolean isRim(long x, long y, long z) {
        long step = M;
        return !insideBasin(Math.subtractExact(x, step), y, z)
                || !insideBasin(Math.addExact(x, step), y, z)
                || !insideBasin(x, y, Math.subtractExact(z, step))
                || !insideBasin(x, y, Math.addExact(z, step));
    }

    private boolean insideBasin(long x, long y, long z) {
        String basinId = lakeSdfPlan.primitives().getFirst().primitiveId();
        return lakeSdf.sampleDistanceMillionths(basinId, x, y, z) < 0L;
    }

    private boolean insideHost(long x, long y, long z) {
        for (var primitive : hostSdf.plan().primitives()) {
            if (hostSdf.sampleDistanceMillionths(primitive.primitiveId(), x, y, z) < 0L) {
                return true;
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

    public record UndergroundLakeMetricsV2(
            String generatorVersion,
            long cavitySamples,
            long fluidSamples,
            long airCavitySamples,
            long containedFluidSamples,
            long rimSamples,
            long leakSamples,
            long thinRoofSamples,
            long breakthroughSamples,
            long sampleCount,
            String planChecksum
    ) {
    }
}
