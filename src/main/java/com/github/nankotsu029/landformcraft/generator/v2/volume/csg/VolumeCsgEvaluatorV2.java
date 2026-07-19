package com.github.nankotsu029.landformcraft.generator.v2.volume.csg;

import com.github.nankotsu029.landformcraft.generator.v2.volume.sdf.VolumeSdfKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Ordered CSG evaluator for V2-5-02. Applies {@code ADD_SOLID} / {@code CARVE_SOLID} /
 * {@code ADD_FLUID} in explicit ordinal order against V2-5-01 SDF primitives. Starts from AIR.
 * Float is never the source of truth.
 */
public final class VolumeCsgEvaluatorV2 {
    public static final String KERNEL_VERSION = VolumeCsgPlanV2.Kernel.VERSION;

    private final VolumeCsgPlanV2 plan;
    private final VolumeSdfKernelV2 sdfKernel;

    public VolumeCsgEvaluatorV2(VolumeCsgPlanV2 plan, VolumeSdfPrimitivePlanV2 primitivePlan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(primitivePlan, "primitivePlan");
        if (!KERNEL_VERSION.equals(plan.kernel().kernelVersion())) {
            throw new VolumeCsgExceptionV2(
                    VolumeCsgFailureCodeV2.UNKNOWN_KERNEL, "unsupported volume-csg kernel");
        }
        try {
            plan.requirePrimitivePlan(primitivePlan);
        } catch (IllegalArgumentException exception) {
            throw new VolumeCsgExceptionV2(
                    VolumeCsgFailureCodeV2.BINDING_MISMATCH, exception.getMessage());
        }
        this.sdfKernel = new VolumeSdfKernelV2(primitivePlan);
    }

    public VolumeCsgPlanV2 plan() {
        return plan;
    }

    public VolumeCsgSampleV2 sampleAt(long xMillionths, long yMillionths, long zMillionths) {
        VolumeCsgSampleV2 state = VolumeCsgSampleV2.air();
        long workUnits = 0L;
        for (VolumeCsgPlanV2.Operator operator : plan.operators()) {
            workUnits = Math.addExact(workUnits, sampleCost(operator));
            if (workUnits > plan.kernel().maximumCpuWorkUnits()) {
                throw new VolumeCsgExceptionV2(
                        VolumeCsgFailureCodeV2.BUDGET_EXCEEDED, "volume-csg CPU work budget exceeded");
            }
            if (!inside(operator, xMillionths, yMillionths, zMillionths)) {
                continue;
            }
            state = apply(operator, state);
        }
        return state;
    }

    public String goldenChecksum(long stepMillionths, int halfExtentSteps) {
        if (stepMillionths <= 0L || halfExtentSteps < 1 || halfExtentSteps > 16) {
            throw new VolumeCsgExceptionV2(
                    VolumeCsgFailureCodeV2.BUDGET_EXCEEDED, "golden lattice budget rejected");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KERNEL_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.UTF_8));
            for (int iz = -halfExtentSteps; iz <= halfExtentSteps; iz++) {
                for (int iy = -halfExtentSteps; iy <= halfExtentSteps; iy++) {
                    for (int ix = -halfExtentSteps; ix <= halfExtentSteps; ix++) {
                        long x = Math.multiplyExact(ix, stepMillionths);
                        long y = Math.multiplyExact(iy, stepMillionths);
                        long z = Math.multiplyExact(iz, stepMillionths);
                        VolumeCsgSampleV2 sample = sampleAt(x, y, z);
                        digest.update((byte) sample.occupancy().ordinal());
                        digest.update(sample.fluidBodyId().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                    }
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean inside(
            VolumeCsgPlanV2.Operator operator,
            long xMillionths,
            long yMillionths,
            long zMillionths
    ) {
        boolean primary = sdfKernel.sampleDistanceMillionths(
                operator.primitiveId(), xMillionths, yMillionths, zMillionths) < 0L;
        if (!primary) {
            return false;
        }
        if (operator.mask() == VolumeCsgPlanV2.MaskMode.NONE) {
            return true;
        }
        return sdfKernel.sampleDistanceMillionths(
                operator.maskPrimitiveId(), xMillionths, yMillionths, zMillionths) < 0L;
    }

    private static VolumeCsgSampleV2 apply(VolumeCsgPlanV2.Operator operator, VolumeCsgSampleV2 state) {
        return switch (operator.kind()) {
            case ADD_SOLID -> VolumeCsgSampleV2.solid();
            case CARVE_SOLID -> {
                // CARVE owns solid occupancy only; existing fluid is left untouched.
                if (state.occupancy() == VolumeCsgOccupancyV2.SOLID) {
                    yield VolumeCsgSampleV2.air();
                }
                yield state;
            }
            case ADD_FLUID -> {
                if (state.occupancy() == VolumeCsgOccupancyV2.AIR) {
                    yield VolumeCsgSampleV2.fluid(operator.fluidBodyId());
                }
                yield state;
            }
        };
    }

    private static long sampleCost(VolumeCsgPlanV2.Operator operator) {
        return operator.mask() == VolumeCsgPlanV2.MaskMode.NONE ? 1L : 2L;
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
