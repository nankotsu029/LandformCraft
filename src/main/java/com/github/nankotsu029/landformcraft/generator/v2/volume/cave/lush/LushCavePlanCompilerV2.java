package com.github.nankotsu029.landformcraft.generator.v2.volume.cave.lush;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.LushCavePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles a {@code LUSH_CAVE} chamber carve on a host {@link CaveNetworkPlanV2}. Requires HARD
 * WITHIN (host chamber) and REACHABLE_FROM (entrance). Ecology hooks are descriptors only.
 */
public final class LushCavePlanCompilerV2 {
    public static final String MODULE_ID = "generate.volume-lush-cave";
    public static final String LIFECYCLE = "SUPPORTED";

    private static final String ZERO = "0".repeat(64);

    private LushCavePlanCompilerV2() {
    }

    public record CompiledLushCaveV2(
            LushCavePlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan,
            CaveNetworkPlanV2 hostNetwork
    ) {
    }

    public static CompiledLushCaveV2 compile(
            String featureId,
            CaveNetworkPlanV2 hostNetwork,
            String hostChamberNodeId,
            String reachableEntranceNodeId,
            long chamberRadiusMillionths,
            int ceilingClearanceBlocks,
            int moistureMillionths,
            int poolShareMillionths,
            LushCavePlanV2.Kernel kernel
    ) {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(hostNetwork, "hostNetwork");
        Objects.requireNonNull(hostChamberNodeId, "hostChamberNodeId");
        Objects.requireNonNull(reachableEntranceNodeId, "reachableEntranceNodeId");
        Objects.requireNonNull(kernel, "kernel");
        if (!LushCavePlanV2.Kernel.VERSION.equals(kernel.kernelVersion())) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.UNKNOWN_KERNEL, "unsupported lush-cave kernel");
        }
        if (moistureMillionths < kernel.minimumMoistureMillionths()) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.TOO_DRY, "lush-cave moisture below minimum");
        }

        CaveNetworkPlanV2.Node hostChamber = requireHostChamber(hostNetwork, hostChamberNodeId);
        requireReachable(hostNetwork, reachableEntranceNodeId, hostChamberNodeId);
        if (chamberRadiusMillionths < hostChamber.radiusMillionths()
                || chamberRadiusMillionths > kernel.maximumRadiusMillionths()) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.NETWORK_CONTAINMENT_FAILED,
                    "lush chamber radius must cover host chamber within kernel max");
        }
        if (ceilingClearanceBlocks < kernel.minimumCeilingClearanceBlocks()) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.HARD_CLEARANCE_CONFLICT,
                    "lush ceiling clearance below kernel minimum");
        }

        String primitiveId = "prim.lush.chamber." + featureId;
        VolumeSdfPrimitiveV2 primitive = new VolumeSdfPrimitiveV2.Sphere(
                primitiveId, hostChamber.center(), chamberRadiusMillionths);
        List<VolumeSdfPrimitiveV2> primitives = List.of(primitive);
        List<VolumeCsgPlanV2.Operator> operators = List.of(new VolumeCsgPlanV2.Operator(
                "op.carve.lush." + featureId,
                0,
                VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                primitiveId,
                VolumeCsgPlanV2.MaskMode.NONE,
                "",
                List.of(),
                ""));

        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 sdfPlan = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        1,
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
                        1024L,
                        1_048_576L,
                        4096,
                        65536,
                        65536),
                ZERO));

        LushCavePlanV2 draft = new LushCavePlanV2(
                1,
                LushCavePlanV2.LUSH_CONTRACT_VERSION,
                featureId,
                kernel,
                new LushCavePlanV2.HostBinding(
                        LushCavePlanV2.HostBinding.WITHIN,
                        hostNetwork.featureId(),
                        hostNetwork.canonicalChecksum(),
                        hostChamberNodeId),
                new LushCavePlanV2.ReachableFromBinding(
                        LushCavePlanV2.ReachableFromBinding.REACHABLE_FROM,
                        reachableEntranceNodeId),
                new LushCavePlanV2.ChamberSpec(
                        hostChamber.center(), chamberRadiusMillionths, ceilingClearanceBlocks),
                new LushCavePlanV2.WetCondition(
                        moistureMillionths,
                        poolShareMillionths,
                        List.of(
                                LushCavePlanV2.WetSurfaceClass.FLOOR,
                                LushCavePlanV2.WetSurfaceClass.WALL,
                                LushCavePlanV2.WetSurfaceClass.CEILING)),
                LushCavePlanV2.EcologyHook.standard(),
                primitive.conservativeBounds(),
                hostNetwork.surfaceHeightBlocks(),
                new LushCavePlanV2.ArtifactBinding(
                        1, sdfPlan.canonicalChecksum(), LushCavePlanV2.ArtifactBinding.SDF_CONTRACT),
                new LushCavePlanV2.ArtifactBinding(
                        1, csgPlan.canonicalChecksum(), LushCavePlanV2.ArtifactBinding.CSG_CONTRACT),
                LushCavePlanV2.ResourceBudget.standard(),
                ZERO);
        LushCavePlanV2 sealed = codec.sealLushCavePlan(draft);
        return new CompiledLushCaveV2(sealed, sdfPlan, csgPlan, hostNetwork);
    }

    static CaveNetworkPlanV2.Node requireHostChamber(
            CaveNetworkPlanV2 hostNetwork,
            String hostChamberNodeId
    ) {
        for (CaveNetworkPlanV2.Node node : hostNetwork.nodes()) {
            if (node.nodeId().equals(hostChamberNodeId)) {
                if (node.kind() != CaveNetworkPlanV2.NodeKind.CHAMBER) {
                    throw new LushCaveExceptionV2(
                            LushCaveFailureCodeV2.ORPHAN_CHAMBER,
                            "lush host node must be CHAMBER kind");
                }
                return node;
            }
        }
        throw new LushCaveExceptionV2(
                LushCaveFailureCodeV2.ORPHAN_CHAMBER, "lush host chamber missing from network");
    }

    static void requireReachable(
            CaveNetworkPlanV2 hostNetwork,
            String entranceNodeId,
            String chamberNodeId
    ) {
        if (!hostNetwork.entranceNodeIds().contains(entranceNodeId)) {
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.NOT_REACHABLE_FROM_ENTRANCE,
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
            throw new LushCaveExceptionV2(
                    LushCaveFailureCodeV2.NOT_REACHABLE_FROM_ENTRANCE,
                    "lush chamber is not REACHABLE_FROM entrance");
        }
    }
}
