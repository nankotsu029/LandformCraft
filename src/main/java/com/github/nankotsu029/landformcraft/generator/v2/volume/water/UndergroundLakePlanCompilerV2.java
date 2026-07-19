package com.github.nankotsu029.landformcraft.generator.v2.volume.water;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.UndergroundLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles an {@code UNDERGROUND_LAKE}: basin {@code CARVE_SOLID} then single {@code ADD_FLUID}
 * body masked below a water surface plane, hosted WITHIN a cave-network chamber.
 */
public final class UndergroundLakePlanCompilerV2 {
    public static final String MODULE_ID = "generate.volume-underground-lake";
    public static final String LIFECYCLE = "SUPPORTED";

    private static final String ZERO = "0".repeat(64);
    private static final long M = 1_000_000L;

    private UndergroundLakePlanCompilerV2() {
    }

    public record CompiledUndergroundLakeV2(
            UndergroundLakePlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan,
            CaveNetworkPlanV2 hostNetwork
    ) {
    }

    public static CompiledUndergroundLakeV2 compile(
            String featureId,
            CaveNetworkPlanV2 hostNetwork,
            String hostChamberNodeId,
            String reachableEntranceNodeId,
            long basinRadiusMillionths,
            int waterSurfaceYBlocks,
            String fluidBodyId,
            int minimumAirCavityBlocks,
            UndergroundLakePlanV2.Kernel kernel
    ) {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(hostNetwork, "hostNetwork");
        Objects.requireNonNull(hostChamberNodeId, "hostChamberNodeId");
        Objects.requireNonNull(reachableEntranceNodeId, "reachableEntranceNodeId");
        Objects.requireNonNull(fluidBodyId, "fluidBodyId");
        Objects.requireNonNull(kernel, "kernel");
        if (!UndergroundLakePlanV2.Kernel.VERSION.equals(kernel.kernelVersion())) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.UNKNOWN_KERNEL, "unsupported underground-lake kernel");
        }

        CaveNetworkPlanV2.Node hostChamber = requireHostChamber(hostNetwork, hostChamberNodeId);
        requireReachable(hostNetwork, reachableEntranceNodeId, hostChamberNodeId);
        if (basinRadiusMillionths < hostChamber.radiusMillionths()
                || basinRadiusMillionths > kernel.maximumRadiusMillionths()) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.NETWORK_CONTAINMENT_FAILED,
                    "lake basin radius must cover host chamber within kernel max");
        }
        if (minimumAirCavityBlocks < kernel.minimumAirCavityBlocks()) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.MISSING_AIR_CAVITY,
                    "air cavity below kernel minimum");
        }

        String basinId = "prim.lake.basin." + featureId;
        String planeId = "prim.lake.water-plane." + featureId;
        VolumeSdfPrimitiveV2 basin = new VolumeSdfPrimitiveV2.Sphere(
                basinId, hostChamber.center(), basinRadiusMillionths);
        VolumeSdfAabbV2 basinAabb = basin.conservativeBounds();
        VolumeSdfPrimitiveV2 waterPlane = new VolumeSdfPrimitiveV2.Plane(
                planeId,
                new VolumeSdfVec3V2(
                        hostChamber.center().xMillionths(),
                        Math.multiplyExact((long) waterSurfaceYBlocks + 1L, M),
                        hostChamber.center().zMillionths()),
                new VolumeSdfVec3V2(0, M, 0),
                basinAabb);

        List<VolumeSdfPrimitiveV2> primitives = List.of(basin, waterPlane);
        List<VolumeCsgPlanV2.Operator> operators = List.of(
                new VolumeCsgPlanV2.Operator(
                        "op.carve.lake." + featureId,
                        0,
                        VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                        basinId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(),
                        ""),
                new VolumeCsgPlanV2.Operator(
                        "op.fluid.lake." + featureId,
                        1,
                        VolumeCsgPlanV2.OperationKind.ADD_FLUID,
                        basinId,
                        VolumeCsgPlanV2.MaskMode.INTERSECTION_WITH_PRIMITIVE,
                        planeId,
                        List.of("op.carve.lake." + featureId),
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

        UndergroundLakePlanV2 draft = new UndergroundLakePlanV2(
                1,
                UndergroundLakePlanV2.LAKE_CONTRACT_VERSION,
                featureId,
                kernel,
                new UndergroundLakePlanV2.HostBinding(
                        UndergroundLakePlanV2.HostBinding.WITHIN,
                        hostNetwork.featureId(),
                        hostNetwork.canonicalChecksum(),
                        hostChamberNodeId),
                new UndergroundLakePlanV2.CaveAccessBinding(
                        UndergroundLakePlanV2.CaveAccessBinding.REACHABLE_FROM,
                        reachableEntranceNodeId),
                new UndergroundLakePlanV2.BasinSpec(
                        hostChamber.center(), basinRadiusMillionths, minimumAirCavityBlocks),
                new UndergroundLakePlanV2.FluidBody(fluidBodyId, waterSurfaceYBlocks),
                basinAabb,
                hostNetwork.surfaceHeightBlocks(),
                new UndergroundLakePlanV2.ArtifactBinding(
                        1, sdfPlan.canonicalChecksum(),
                        UndergroundLakePlanV2.ArtifactBinding.SDF_CONTRACT),
                new UndergroundLakePlanV2.ArtifactBinding(
                        1, csgPlan.canonicalChecksum(),
                        UndergroundLakePlanV2.ArtifactBinding.CSG_CONTRACT),
                UndergroundLakePlanV2.ResourceBudget.standard(),
                ZERO);
        UndergroundLakePlanV2 sealed = codec.sealUndergroundLakePlan(draft);
        return new CompiledUndergroundLakeV2(sealed, sdfPlan, csgPlan, hostNetwork);
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
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.DOUBLE_FLUID_OWNER,
                    "underground lake requires exactly one ADD_FLUID owner");
        }
    }

    static void requireCarveThenFluid(List<VolumeCsgPlanV2.Operator> operators) {
        boolean sawCarve = false;
        for (VolumeCsgPlanV2.Operator operator : operators) {
            if (operator.kind() == VolumeCsgPlanV2.OperationKind.CARVE_SOLID) {
                if (!operator.fluidBodyId().isEmpty()) {
                    throw new UndergroundLakeExceptionV2(
                            UndergroundLakeFailureCodeV2.CARVE_AS_FLUID_CORRUPTION,
                            "CARVE_SOLID must not own fluidBodyId");
                }
                sawCarve = true;
            }
            if (operator.kind() == VolumeCsgPlanV2.OperationKind.ADD_FLUID && !sawCarve) {
                throw new UndergroundLakeExceptionV2(
                        UndergroundLakeFailureCodeV2.CARVE_AS_FLUID_CORRUPTION,
                        "ADD_FLUID must follow CARVE_SOLID for lake cavity");
            }
        }
    }

    static CaveNetworkPlanV2.Node requireHostChamber(
            CaveNetworkPlanV2 hostNetwork,
            String hostChamberNodeId
    ) {
        for (CaveNetworkPlanV2.Node node : hostNetwork.nodes()) {
            if (node.nodeId().equals(hostChamberNodeId)) {
                if (node.kind() != CaveNetworkPlanV2.NodeKind.CHAMBER) {
                    throw new UndergroundLakeExceptionV2(
                            UndergroundLakeFailureCodeV2.ORPHAN_BASIN,
                            "lake host node must be CHAMBER kind");
                }
                return node;
            }
        }
        throw new UndergroundLakeExceptionV2(
                UndergroundLakeFailureCodeV2.ORPHAN_BASIN, "lake host chamber missing from network");
    }

    static void requireReachable(
            CaveNetworkPlanV2 hostNetwork,
            String entranceNodeId,
            String chamberNodeId
    ) {
        if (!hostNetwork.entranceNodeIds().contains(entranceNodeId)) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.NOT_REACHABLE_FROM_ENTRANCE,
                    "REACHABLE_FROM entrance is not a declared network entrance");
        }
        Map<String, List<String>> adjacency = new HashMap<>();
        for (CaveNetworkPlanV2.Node node : hostNetwork.nodes()) {
            adjacency.put(node.nodeId(), new ArrayList<>());
        }
        for (CaveNetworkPlanV2.Edge edge : hostNetwork.edges()) {
            adjacency.get(edge.fromNodeId()).add(edge.toNodeId());
            adjacency.get(edge.toNodeId()).add(edge.fromNodeId());
        }
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(entranceNodeId);
        reachable.add(entranceNodeId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String next : adjacency.getOrDefault(current, List.of())) {
                if (reachable.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        if (!reachable.contains(chamberNodeId)) {
            throw new UndergroundLakeExceptionV2(
                    UndergroundLakeFailureCodeV2.NOT_REACHABLE_FROM_ENTRANCE,
                    "lake chamber is not REACHABLE_FROM entrance");
        }
    }
}
