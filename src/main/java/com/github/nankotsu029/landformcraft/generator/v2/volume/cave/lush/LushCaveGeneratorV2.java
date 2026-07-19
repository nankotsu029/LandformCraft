package com.github.nankotsu029.landformcraft.generator.v2.volume.cave.lush;

import com.github.nankotsu029.landformcraft.generator.v2.volume.sdf.VolumeSdfKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.LushCavePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * SUPPORTED (offline) lush-cave metric validator. Checks WITHIN／REACHABLE_FROM, wet floor/wall/ceiling
 * eligibility, roof／breakthrough, and ecology-hook presence without running Stage 10 placement.
 */
public final class LushCaveGeneratorV2 {
    public static final String GENERATOR_VERSION = "lush-cave-generator-v1";

    private final LushCavePlanV2 plan;
    private final CaveNetworkPlanV2 hostNetwork;
    private final VolumeSdfKernelV2 lushSdf;
    private final VolumeSdfKernelV2 hostSdf;
    private final VolumeCsgPlanV2 csgPlan;

    public LushCaveGeneratorV2(
            LushCavePlanV2 plan,
            CaveNetworkPlanV2 hostNetwork,
            VolumeSdfPrimitivePlanV2 lushSdfPlan,
            VolumeSdfPrimitivePlanV2 hostSdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.hostNetwork = Objects.requireNonNull(hostNetwork, "hostNetwork");
        Objects.requireNonNull(lushSdfPlan, "lushSdfPlan");
        Objects.requireNonNull(hostSdfPlan, "hostSdfPlan");
        this.csgPlan = Objects.requireNonNull(csgPlan, "csgPlan");
        if (!LushCavePlanV2.Kernel.VERSION.equals(plan.kernel().kernelVersion())) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.UNKNOWN_KERNEL, "unsupported lush-cave kernel");
        }
        if (!plan.hostBinding().hostNetworkPlanChecksum().equals(hostNetwork.canonicalChecksum())
                || !plan.hostBinding().hostNetworkFeatureId().equals(hostNetwork.featureId())) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.HOST_MISMATCH, "lush host network binding mismatch");
        }
        if (!plan.sdfPlanBinding().sourceArtifactChecksum().equals(lushSdfPlan.canonicalChecksum())) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.BINDING_MISMATCH, "lush SDF plan binding mismatch");
        }
        if (!plan.csgPlanBinding().sourceArtifactChecksum().equals(csgPlan.canonicalChecksum())) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.BINDING_MISMATCH, "lush CSG plan binding mismatch");
        }
        this.lushSdf = new VolumeSdfKernelV2(lushSdfPlan);
        this.hostSdf = new VolumeSdfKernelV2(hostSdfPlan);
    }

    public LushCavePlanV2 plan() {
        return plan;
    }

    public VolumeCsgPlanV2 csgPlan() {
        return csgPlan;
    }

    public LushCaveMetricsV2 validate() {
        LushCavePlanCompilerV2.requireHostChamber(
                hostNetwork, plan.hostBinding().hostChamberNodeId());
        LushCavePlanCompilerV2.requireReachable(
                hostNetwork,
                plan.reachableFrom().entranceNodeId(),
                plan.hostBinding().hostChamberNodeId());

        if (plan.wetCondition().moistureMillionths() < plan.kernel().minimumMoistureMillionths()) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.TOO_DRY, "lush moisture below kernel minimum");
        }
        if (plan.ecologyHook().reservedAssemblageIds().isEmpty()) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.WET_SURFACE_INELIGIBLE, "ecology hooks missing");
        }

        VolumeSdfVec3V2 center = plan.chamber().center();
        if (!insideHost(center.xMillionths(), center.yMillionths(), center.zMillionths())) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.NETWORK_CONTAINMENT_FAILED,
                    "lush chamber center is outside host network carve");
        }

        long step = 1_000_000L;
        long minX = alignDown(plan.aabb().minXMillionths(), step);
        long maxX = alignUp(plan.aabb().maxXMillionths(), step);
        long minY = alignDown(plan.aabb().minYMillionths(), step);
        long maxY = alignUp(plan.aabb().maxYMillionths(), step);
        long minZ = alignDown(plan.aabb().minZMillionths(), step);
        long maxZ = alignUp(plan.aabb().maxZMillionths(), step);

        long carvedSamples = 0L;
        long containedSamples = 0L;
        long thinRoofSamples = 0L;
        long breakthroughSamples = 0L;
        long samples = 0L;
        Map<LushCavePlanV2.WetSurfaceClass, Long> wetCounts = new EnumMap<>(
                LushCavePlanV2.WetSurfaceClass.class);
        for (LushCavePlanV2.WetSurfaceClass surface : LushCavePlanV2.WetSurfaceClass.values()) {
            wetCounts.put(surface, 0L);
        }
        int minRoof = plan.kernel().minimumRoofBlocks();
        int surfaceY = plan.surfaceHeightBlocks();
        int maxSamples = plan.budget().maximumDescriptorSamples();

        for (long z = minZ; z <= maxZ; z = Math.addExact(z, step)) {
            for (long y = minY; y <= maxY; y = Math.addExact(y, step)) {
                for (long x = minX; x <= maxX; x = Math.addExact(x, step)) {
                    samples = Math.addExact(samples, 1L);
                    if (samples > maxSamples) {
                        throw new LushCaveExceptionV2(
                                LushCaveFailureCodeV2.BUDGET_EXCEEDED,
                                "lush descriptor sample budget exceeded");
                    }
                    if (!insideLush(x, y, z)) {
                        continue;
                    }
                    carvedSamples++;
                    if (insideHost(x, y, z)) {
                        containedSamples++;
                    }
                    int blockY = (int) Math.floorDiv(y, 1_000_000L);
                    if (blockY >= surfaceY) {
                        breakthroughSamples++;
                    } else if (surfaceY - blockY < minRoof) {
                        thinRoofSamples++;
                    }
                    classifyWetSurfaces(x, y, z, wetCounts);
                }
            }
        }
        if (carvedSamples == 0L) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.ORPHAN_CHAMBER, "lush chamber carve is empty");
        }
        if (containedSamples * 100L < carvedSamples * 80L) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.NETWORK_CONTAINMENT_FAILED,
                    "lush chamber is not sufficiently WITHIN host network");
        }
        if (thinRoofSamples > 0) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.THIN_ROOF,
                    "lush roof thinner than minimum at " + thinRoofSamples + " samples");
        }
        if (breakthroughSamples > 0) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.SURFACE_BREAKTHROUGH,
                    "lush surface breakthrough at " + breakthroughSamples + " samples");
        }

        Set<LushCavePlanV2.WetSurfaceClass> eligible = EnumSet.copyOf(
                plan.wetCondition().eligibleSurfaceClasses());
        for (LushCavePlanV2.WetSurfaceClass required : eligible) {
            if (wetCounts.getOrDefault(required, 0L) <= 0L) {
                throw new LushCaveExceptionV2(
                        LushCaveFailureCodeV2.WET_SURFACE_INELIGIBLE,
                        "missing wet surface class " + required.name());
            }
        }
        long ceilingSpan = Math.subtractExact(
                plan.aabb().maxYMillionths(), plan.aabb().minYMillionths());
        int clearance = (int) Math.floorDiv(ceilingSpan, 1_000_000L);
        if (clearance < plan.chamber().ceilingClearanceBlocks()) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.HARD_CLEARANCE_CONFLICT,
                    "lush chamber ceiling clearance metric failed");
        }

        return new LushCaveMetricsV2(
                GENERATOR_VERSION,
                carvedSamples,
                containedSamples,
                wetCounts.get(LushCavePlanV2.WetSurfaceClass.FLOOR),
                wetCounts.get(LushCavePlanV2.WetSurfaceClass.WALL),
                wetCounts.get(LushCavePlanV2.WetSurfaceClass.CEILING),
                thinRoofSamples,
                breakthroughSamples,
                samples,
                plan.canonicalChecksum());
    }

    public String metricChecksum() {
        LushCaveMetricsV2 metrics = validate();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(GENERATOR_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.carvedSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.floorSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.wallSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.ceilingSamples()).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(metrics.sampleCount()).getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void classifyWetSurfaces(
            long x,
            long y,
            long z,
            Map<LushCavePlanV2.WetSurfaceClass, Long> wetCounts
    ) {
        long step = 1_000_000L;
        boolean solidBelow = !insideLush(x, Math.subtractExact(y, step), z);
        boolean solidAbove = !insideLush(x, Math.addExact(y, step), z);
        boolean solidSide = !insideLush(Math.subtractExact(x, step), y, z)
                || !insideLush(Math.addExact(x, step), y, z)
                || !insideLush(x, y, Math.subtractExact(z, step))
                || !insideLush(x, y, Math.addExact(z, step));
        if (solidBelow) {
            wetCounts.merge(LushCavePlanV2.WetSurfaceClass.FLOOR, 1L, Long::sum);
        }
        if (solidAbove) {
            wetCounts.merge(LushCavePlanV2.WetSurfaceClass.CEILING, 1L, Long::sum);
        }
        if (solidSide) {
            wetCounts.merge(LushCavePlanV2.WetSurfaceClass.WALL, 1L, Long::sum);
        }
    }

    private boolean insideLush(long x, long y, long z) {
        for (var primitive : lushSdf.plan().primitives()) {
            if (lushSdf.sampleDistanceMillionths(primitive.primitiveId(), x, y, z) < 0L) {
                return true;
            }
        }
        return false;
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

    public record LushCaveMetricsV2(
            String generatorVersion,
            long carvedSamples,
            long containedSamples,
            long floorSamples,
            long wallSamples,
            long ceilingSamples,
            long thinRoofSamples,
            long breakthroughSamples,
            long sampleCount,
            String planChecksum
    ) {
    }
}
