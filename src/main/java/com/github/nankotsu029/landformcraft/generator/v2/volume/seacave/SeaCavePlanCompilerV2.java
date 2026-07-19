package com.github.nankotsu029.landformcraft.generator.v2.volume.seacave;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.SeaCavePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles a {@code SEA_CAVE}: cliff-hosted capsule carve with marine opening and static
 * {@code ADD_FLUID} below sea level.
 */
public final class SeaCavePlanCompilerV2 {
    public static final String MODULE_ID = "generate.volume-sea-cave";
    public static final String LIFECYCLE = "SUPPORTED";

    private static final String ZERO = "0".repeat(64);
    private static final long M = 1_000_000L;

    private SeaCavePlanCompilerV2() {
    }

    public record CompiledSeaCaveV2(
            SeaCavePlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
    }

    public static CompiledSeaCaveV2 compile(
            String featureId,
            String hostCliffFeatureId,
            SeaCavePlanV2.CardinalFace seawardFace,
            VolumeSdfAabbV2 hostAabb,
            VolumeSdfVec3V2 openingCenter,
            VolumeSdfVec3V2 inlandCenter,
            long radiusMillionths,
            int seaLevelYBlocks,
            String fluidBodyId,
            int surfaceHeightBlocks,
            SeaCavePlanV2.Kernel kernel
    ) {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(hostCliffFeatureId, "hostCliffFeatureId");
        Objects.requireNonNull(seawardFace, "seawardFace");
        Objects.requireNonNull(hostAabb, "hostAabb");
        Objects.requireNonNull(openingCenter, "openingCenter");
        Objects.requireNonNull(inlandCenter, "inlandCenter");
        Objects.requireNonNull(fluidBodyId, "fluidBodyId");
        Objects.requireNonNull(kernel, "kernel");
        if (!SeaCavePlanV2.Kernel.VERSION.equals(kernel.kernelVersion())) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.UNKNOWN_KERNEL, "unsupported sea-cave kernel");
        }
        if (radiusMillionths > kernel.maximumRadiusMillionths()) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.BUDGET_EXCEEDED, "sea-cave radius exceeds kernel");
        }
        requireOpeningOnSeawardFace(hostAabb, seawardFace, openingCenter, radiusMillionths);
        requireInlandInsideHost(hostAabb, inlandCenter, radiusMillionths);
        requireInlandFromOpening(seawardFace, openingCenter, inlandCenter);

        String chamberId = "prim.seacave.chamber." + featureId;
        String planeId = "prim.seacave.water-plane." + featureId;
        VolumeSdfPrimitiveV2 chamber = new VolumeSdfPrimitiveV2.Capsule(
                chamberId, openingCenter, inlandCenter, radiusMillionths);
        VolumeSdfAabbV2 chamberAabb = chamber.conservativeBounds();
        VolumeSdfPrimitiveV2 waterPlane = new VolumeSdfPrimitiveV2.Plane(
                planeId,
                new VolumeSdfVec3V2(
                        openingCenter.xMillionths(),
                        Math.multiplyExact((long) seaLevelYBlocks + 1L, M),
                        openingCenter.zMillionths()),
                new VolumeSdfVec3V2(0, M, 0),
                chamberAabb);

        List<VolumeSdfPrimitiveV2> primitives = List.of(chamber, waterPlane);
        List<VolumeCsgPlanV2.Operator> operators = List.of(
                new VolumeCsgPlanV2.Operator(
                        "op.carve.seacave." + featureId,
                        0,
                        VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                        chamberId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(),
                        ""),
                new VolumeCsgPlanV2.Operator(
                        "op.fluid.seacave." + featureId,
                        1,
                        VolumeCsgPlanV2.OperationKind.ADD_FLUID,
                        chamberId,
                        VolumeCsgPlanV2.MaskMode.INTERSECTION_WITH_PRIMITIVE,
                        planeId,
                        List.of("op.carve.seacave." + featureId),
                        fluidBodyId));
        requireSingleFluidOwner(operators);
        requireCarveThenFluid(operators);

        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 sdfPlan = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        2,
                        64,
                        2048,
                        65536,
                        65536),
                ZERO));
        VolumeCsgPlanV2 csgPlan = codec.sealVolumeCsgPlan(new VolumeCsgPlanV2(
                1,
                "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, sdfPlan.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                operators,
                new VolumeCsgPlanV2.ResourceBudget(
                        "volume-csg-budget-v1",
                        64,
                        8,
                        2048L,
                        1_048_576L,
                        4096,
                        65536,
                        65536),
                ZERO));

        SeaCavePlanV2 draft = new SeaCavePlanV2(
                1,
                SeaCavePlanV2.SEA_CAVE_CONTRACT_VERSION,
                featureId,
                kernel,
                new SeaCavePlanV2.HostCliffBinding(
                        SeaCavePlanV2.HostCliffBinding.CARVES_FLANK_OF,
                        hostCliffFeatureId,
                        seawardFace,
                        hostAabb),
                new SeaCavePlanV2.MarineBoundaryBinding(
                        SeaCavePlanV2.MarineBoundaryBinding.EMPTIES_INTO,
                        SeaCavePlanV2.MarineBoundaryBinding.SEA,
                        seaLevelYBlocks),
                new SeaCavePlanV2.ChamberSpec(openingCenter, inlandCenter, radiusMillionths),
                new SeaCavePlanV2.FluidBody(fluidBodyId, seaLevelYBlocks),
                chamberAabb,
                surfaceHeightBlocks,
                new SeaCavePlanV2.ArtifactBinding(
                        1, sdfPlan.canonicalChecksum(), SeaCavePlanV2.ArtifactBinding.SDF_CONTRACT),
                new SeaCavePlanV2.ArtifactBinding(
                        1, csgPlan.canonicalChecksum(), SeaCavePlanV2.ArtifactBinding.CSG_CONTRACT),
                SeaCavePlanV2.ResourceBudget.standard(),
                ZERO);
        return new CompiledSeaCaveV2(codec.sealSeaCavePlan(draft), sdfPlan, csgPlan);
    }

    static void requireOpeningOnSeawardFace(
            VolumeSdfAabbV2 hostAabb,
            SeaCavePlanV2.CardinalFace face,
            VolumeSdfVec3V2 opening,
            long radiusMillionths
    ) {
        long tolerance = Math.addExact(radiusMillionths, M);
        boolean onFace = switch (face) {
            case WEST -> Math.abs(opening.xMillionths() - hostAabb.minXMillionths()) <= tolerance
                    && inRange(opening.yMillionths(), hostAabb.minYMillionths(), hostAabb.maxYMillionths())
                    && inRange(opening.zMillionths(), hostAabb.minZMillionths(), hostAabb.maxZMillionths());
            case EAST -> Math.abs(opening.xMillionths() - hostAabb.maxXMillionths()) <= tolerance
                    && inRange(opening.yMillionths(), hostAabb.minYMillionths(), hostAabb.maxYMillionths())
                    && inRange(opening.zMillionths(), hostAabb.minZMillionths(), hostAabb.maxZMillionths());
            case NORTH -> Math.abs(opening.zMillionths() - hostAabb.minZMillionths()) <= tolerance
                    && inRange(opening.xMillionths(), hostAabb.minXMillionths(), hostAabb.maxXMillionths())
                    && inRange(opening.yMillionths(), hostAabb.minYMillionths(), hostAabb.maxYMillionths());
            case SOUTH -> Math.abs(opening.zMillionths() - hostAabb.maxZMillionths()) <= tolerance
                    && inRange(opening.xMillionths(), hostAabb.minXMillionths(), hostAabb.maxXMillionths())
                    && inRange(opening.yMillionths(), hostAabb.minYMillionths(), hostAabb.maxYMillionths());
        };
        if (!onFace) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.LANDLOCKED,
                    "sea-cave opening is not on the seaward host face");
        }
    }

    static void requireInlandInsideHost(
            VolumeSdfAabbV2 hostAabb,
            VolumeSdfVec3V2 inland,
            long radiusMillionths
    ) {
        if (!containsExpanded(hostAabb, inland, radiusMillionths)) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.UNSUPPORTED_HOST,
                    "sea-cave inland chamber is outside host cliff AABB");
        }
    }

    static void requireInlandFromOpening(
            SeaCavePlanV2.CardinalFace face,
            VolumeSdfVec3V2 opening,
            VolumeSdfVec3V2 inland
    ) {
        boolean inlandDirection = switch (face) {
            case WEST -> inland.xMillionths() > opening.xMillionths();
            case EAST -> inland.xMillionths() < opening.xMillionths();
            case NORTH -> inland.zMillionths() > opening.zMillionths();
            case SOUTH -> inland.zMillionths() < opening.zMillionths();
        };
        if (!inlandDirection) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.UNSUPPORTED_HOST,
                    "sea-cave inland center must retreat from the seaward face");
        }
    }

    static void requireSingleFluidOwner(List<VolumeCsgPlanV2.Operator> operators) {
        Set<String> fluidIds = new HashSet<>();
        int fluidOps = 0;
        for (VolumeCsgPlanV2.Operator operator : operators) {
            if (operator.kind() == VolumeCsgPlanV2.OperationKind.ADD_FLUID) {
                fluidOps++;
                fluidIds.add(operator.fluidBodyId());
            }
        }
        if (fluidOps != 1 || fluidIds.size() != 1) {
            throw new SeaCaveExceptionV2(
                    SeaCaveFailureCodeV2.DOUBLE_FLUID_OWNER,
                    "sea cave requires exactly one ADD_FLUID owner");
        }
    }

    static void requireCarveThenFluid(List<VolumeCsgPlanV2.Operator> operators) {
        boolean sawCarve = false;
        for (VolumeCsgPlanV2.Operator operator : operators) {
            if (operator.kind() == VolumeCsgPlanV2.OperationKind.CARVE_SOLID) {
                if (!operator.fluidBodyId().isEmpty()) {
                    throw new SeaCaveExceptionV2(
                            SeaCaveFailureCodeV2.CARVE_AS_FLUID_CORRUPTION,
                            "CARVE_SOLID must not own fluidBodyId");
                }
                sawCarve = true;
            }
            if (operator.kind() == VolumeCsgPlanV2.OperationKind.ADD_FLUID && !sawCarve) {
                throw new SeaCaveExceptionV2(
                        SeaCaveFailureCodeV2.CARVE_AS_FLUID_CORRUPTION,
                        "ADD_FLUID must follow CARVE_SOLID for sea cave");
            }
        }
    }

    private static boolean containsExpanded(VolumeSdfAabbV2 aabb, VolumeSdfVec3V2 point, long expand) {
        return point.xMillionths() >= Math.subtractExact(aabb.minXMillionths(), expand)
                && point.xMillionths() <= Math.addExact(aabb.maxXMillionths(), expand)
                && point.yMillionths() >= Math.subtractExact(aabb.minYMillionths(), expand)
                && point.yMillionths() <= Math.addExact(aabb.maxYMillionths(), expand)
                && point.zMillionths() >= Math.subtractExact(aabb.minZMillionths(), expand)
                && point.zMillionths() <= Math.addExact(aabb.maxZMillionths(), expand);
    }

    private static boolean inRange(long value, long min, long max) {
        return value >= min && value <= max;
    }
}
