package com.github.nankotsu029.landformcraft.generator.v2.volume.query;

import com.github.nankotsu029.landformcraft.generator.v2.TerrainQuery;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.sdf.VolumeSdfKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;

import java.util.Objects;

/**
 * Composes a base {@link TerrainQuery} column sample with ordered CSG operators. Starts from the
 * base occupancy (not AIR) and applies intersecting operators in explicit ordinal order.
 */
public final class VolumeTerrainCompositionKernelV2 {
    public static final String KERNEL_VERSION = "volume-terrain-composition-v1";
    private static final long MILLIONTHS = 1_000_000L;
    private static final String BASE_WATER_BODY = "fluid.base-water";

    private final VolumeCsgPlanV2 csgPlan;
    private final VolumeSdfKernelV2 sdfKernel;

    public VolumeTerrainCompositionKernelV2(
            VolumeCsgPlanV2 csgPlan,
            VolumeSdfPrimitivePlanV2 primitivePlan
    ) {
        this.csgPlan = Objects.requireNonNull(csgPlan, "csgPlan");
        Objects.requireNonNull(primitivePlan, "primitivePlan");
        if (!VolumeCsgPlanV2.Kernel.VERSION.equals(csgPlan.kernel().kernelVersion())) {
            throw new VolumeTerrainQueryExceptionV2(
                    VolumeTerrainQueryFailureCodeV2.UNKNOWN_KERNEL, "unsupported volume-csg kernel");
        }
        try {
            csgPlan.requirePrimitivePlan(primitivePlan);
        } catch (IllegalArgumentException exception) {
            throw new VolumeTerrainQueryExceptionV2(
                    VolumeTerrainQueryFailureCodeV2.BINDING_MISMATCH, exception.getMessage());
        }
        this.sdfKernel = new VolumeSdfKernelV2(primitivePlan);
    }

    public VolumeCsgPlanV2 csgPlan() {
        return csgPlan;
    }

    public ComposedSample compose(TerrainQuery base, int x, int y, int z) {
        Objects.requireNonNull(base, "base");
        VolumeCsgSampleV2 state = fromBase(base, x, y, z);
        TerrainQuery.SemanticMaterial material = base.semanticMaterialAt(x, y, z);
        long workUnits = 0L;
        for (VolumeCsgPlanV2.Operator operator : csgPlan.operators()) {
            workUnits = Math.addExact(workUnits, sampleCost(operator));
            if (workUnits > csgPlan.kernel().maximumCpuWorkUnits()) {
                throw new VolumeTerrainQueryExceptionV2(
                        VolumeTerrainQueryFailureCodeV2.BUDGET_EXCEEDED,
                        "volume composition CPU budget exceeded");
            }
            if (!inside(operator, x, y, z)) {
                continue;
            }
            Applied applied = apply(operator, state, material);
            state = applied.sample();
            material = applied.material();
        }
        return new ComposedSample(state, material);
    }

    private static VolumeCsgSampleV2 fromBase(TerrainQuery base, int x, int y, int z) {
        return switch (base.blockClassAt(x, y, z)) {
            case AIR -> VolumeCsgSampleV2.air();
            case SOLID -> VolumeCsgSampleV2.solid();
            case FLUID -> VolumeCsgSampleV2.fluid(BASE_WATER_BODY);
        };
    }

    private boolean inside(VolumeCsgPlanV2.Operator operator, int x, int y, int z) {
        long xm = Math.addExact(Math.multiplyExact(x, MILLIONTHS), MILLIONTHS / 2L);
        long ym = Math.addExact(Math.multiplyExact(y, MILLIONTHS), MILLIONTHS / 2L);
        long zm = Math.addExact(Math.multiplyExact(z, MILLIONTHS), MILLIONTHS / 2L);
        boolean primary = sdfKernel.sampleDistanceMillionths(
                operator.primitiveId(), xm, ym, zm) < 0L;
        if (!primary) {
            return false;
        }
        if (operator.mask() == VolumeCsgPlanV2.MaskMode.NONE) {
            return true;
        }
        return sdfKernel.sampleDistanceMillionths(
                operator.maskPrimitiveId(), xm, ym, zm) < 0L;
    }

    private static Applied apply(
            VolumeCsgPlanV2.Operator operator,
            VolumeCsgSampleV2 state,
            TerrainQuery.SemanticMaterial material
    ) {
        return switch (operator.kind()) {
            case ADD_SOLID -> new Applied(VolumeCsgSampleV2.solid(), TerrainQuery.SemanticMaterial.STONE);
            case CARVE_SOLID -> {
                if (state.occupancy() == VolumeCsgOccupancyV2.SOLID) {
                    yield new Applied(VolumeCsgSampleV2.air(), TerrainQuery.SemanticMaterial.NONE);
                }
                yield new Applied(state, material);
            }
            case ADD_FLUID -> {
                if (state.occupancy() == VolumeCsgOccupancyV2.AIR) {
                    yield new Applied(
                            VolumeCsgSampleV2.fluid(operator.fluidBodyId()),
                            TerrainQuery.SemanticMaterial.NONE);
                }
                // Match ordered CSG: existing fluid is left untouched. Distinct owners on the
                // same cell are rejected only when a later operator would replace ownership.
                if (state.occupancy() == VolumeCsgOccupancyV2.FLUID
                        && !state.fluidBodyId().isEmpty()
                        && !state.fluidBodyId().equals(operator.fluidBodyId())
                        && !state.fluidBodyId().equals(BASE_WATER_BODY)
                        && !operator.fluidBodyId().equals(BASE_WATER_BODY)) {
                    throw new VolumeTerrainQueryExceptionV2(
                            VolumeTerrainQueryFailureCodeV2.OWNER_CONFLICT,
                            "fluid owner conflict at composed cell");
                }
                yield new Applied(state, material);
            }
        };
    }

    private static long sampleCost(VolumeCsgPlanV2.Operator operator) {
        return operator.mask() == VolumeCsgPlanV2.MaskMode.NONE ? 1L : 2L;
    }

    public record ComposedSample(VolumeCsgSampleV2 sample, TerrainQuery.SemanticMaterial material) {
        public ComposedSample {
            Objects.requireNonNull(sample, "sample");
            Objects.requireNonNull(material, "material");
            if (sample.occupancy() == VolumeCsgOccupancyV2.SOLID
                    && material == TerrainQuery.SemanticMaterial.NONE) {
                throw new VolumeTerrainQueryExceptionV2(
                        VolumeTerrainQueryFailureCodeV2.INVALID_INTERVAL,
                        "SOLID requires semantic material");
            }
            if (sample.occupancy() != VolumeCsgOccupancyV2.SOLID
                    && material != TerrainQuery.SemanticMaterial.NONE) {
                throw new VolumeTerrainQueryExceptionV2(
                        VolumeTerrainQueryFailureCodeV2.INVALID_INTERVAL,
                        "non-SOLID forbids semantic material");
            }
        }

        public TerrainQuery.BlockClass blockClass() {
            return switch (sample.occupancy()) {
                case AIR -> TerrainQuery.BlockClass.AIR;
                case SOLID -> TerrainQuery.BlockClass.SOLID;
                case FLUID -> TerrainQuery.BlockClass.FLUID;
            };
        }

        public TerrainQuery.FluidBody fluidBody() {
            return sample.occupancy() == VolumeCsgOccupancyV2.FLUID
                    ? TerrainQuery.FluidBody.WATER
                    : TerrainQuery.FluidBody.NONE;
        }
    }

    private record Applied(VolumeCsgSampleV2 sample, TerrainQuery.SemanticMaterial material) {
    }
}
