package com.github.nankotsu029.landformcraft.generator.v2.volume.cave;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
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
 * Compiles a cave tunnel/chamber graph into sealed SDF＋ordered CARVE_SOLID CSG plans and a
 * {@link CaveNetworkPlanV2}. Float is never the source of truth.
 */
public final class CaveNetworkPlanCompilerV2 {
    public static final String MODULE_ID = "generate.volume-cave-network";
    public static final String LIFECYCLE = "SUPPORTED";

    private static final String ZERO = "0".repeat(64);

    private CaveNetworkPlanCompilerV2() {
    }

    public record CompiledCaveNetworkV2(
            CaveNetworkPlanV2 plan,
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
    }

    public static CompiledCaveNetworkV2 compile(
            String featureId,
            List<CaveNetworkPlanV2.Node> nodes,
            List<CaveNetworkPlanV2.Edge> edges,
            List<String> entranceNodeIds,
            int surfaceHeightBlocks,
            CaveNetworkPlanV2.Kernel kernel
    ) {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(entranceNodeIds, "entranceNodeIds");
        Objects.requireNonNull(kernel, "kernel");
        if (!CaveNetworkPlanV2.Kernel.VERSION.equals(kernel.kernelVersion())) {
            throw new CaveNetworkExceptionV2(
                    CaveNetworkFailureCodeV2.UNKNOWN_KERNEL, "unsupported cave-network kernel");
        }
        requireConnected(nodes, edges, entranceNodeIds);

        List<VolumeSdfPrimitiveV2> primitives = new ArrayList<>();
        List<VolumeCsgPlanV2.Operator> operators = new ArrayList<>();
        int ordinal = 0;
        VolumeSdfAabbV2 aabb = null;
        for (CaveNetworkPlanV2.Node node : nodes) {
            String primitiveId = "prim.node." + node.nodeId();
            VolumeSdfPrimitiveV2 primitive = new VolumeSdfPrimitiveV2.Sphere(
                    primitiveId, node.center(), node.radiusMillionths());
            primitives.add(primitive);
            operators.add(new VolumeCsgPlanV2.Operator(
                    "op.carve.node." + node.nodeId(),
                    ordinal++,
                    VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                    primitiveId,
                    VolumeCsgPlanV2.MaskMode.NONE,
                    "",
                    List.of(),
                    ""));
            aabb = union(aabb, primitive.conservativeBounds());
        }
        Map<String, CaveNetworkPlanV2.Node> byId = new HashMap<>();
        for (CaveNetworkPlanV2.Node node : nodes) {
            byId.put(node.nodeId(), node);
        }
        for (CaveNetworkPlanV2.Edge edge : edges) {
            CaveNetworkPlanV2.Node from = byId.get(edge.fromNodeId());
            CaveNetworkPlanV2.Node to = byId.get(edge.toNodeId());
            String primitiveId = "prim.edge." + edge.edgeId();
            VolumeSdfPrimitiveV2 primitive = new VolumeSdfPrimitiveV2.Capsule(
                    primitiveId, from.center(), to.center(), edge.radiusMillionths());
            primitives.add(primitive);
            operators.add(new VolumeCsgPlanV2.Operator(
                    "op.carve.edge." + edge.edgeId(),
                    ordinal++,
                    VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                    primitiveId,
                    VolumeCsgPlanV2.MaskMode.NONE,
                    "",
                    List.of(),
                    ""));
            aabb = union(aabb, primitive.conservativeBounds());
        }
        if (aabb == null) {
            throw new CaveNetworkExceptionV2(
                    CaveNetworkFailureCodeV2.INVALID_GRAPH, "cave-network AABB empty");
        }

        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 sdfPlan = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        Math.max(primitives.size(), 1),
                        64,
                        4096,
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
                        Math.max(1, operators.size() * 1024L),
                        1_048_576L,
                        4096,
                        65536,
                        65536),
                ZERO));

        CaveNetworkPlanV2 draft = new CaveNetworkPlanV2(
                1,
                CaveNetworkPlanV2.CAVE_CONTRACT_VERSION,
                featureId,
                kernel,
                nodes,
                edges,
                entranceNodeIds,
                aabb,
                surfaceHeightBlocks,
                new CaveNetworkPlanV2.ArtifactBinding(
                        1, sdfPlan.canonicalChecksum(), CaveNetworkPlanV2.ArtifactBinding.SDF_CONTRACT),
                new CaveNetworkPlanV2.ArtifactBinding(
                        1, csgPlan.canonicalChecksum(), CaveNetworkPlanV2.ArtifactBinding.CSG_CONTRACT),
                CaveNetworkPlanV2.ResourceBudget.standard(nodes.size(), edges.size()),
                ZERO);
        CaveNetworkPlanV2 sealed = codec.sealCaveNetworkPlan(draft);
        return new CompiledCaveNetworkV2(sealed, sdfPlan, csgPlan);
    }

    static void requireConnected(
            List<CaveNetworkPlanV2.Node> nodes,
            List<CaveNetworkPlanV2.Edge> edges,
            List<String> entranceNodeIds
    ) {
        if (entranceNodeIds.isEmpty()) {
            throw new CaveNetworkExceptionV2(
                    CaveNetworkFailureCodeV2.MISSING_ENTRANCE, "cave-network requires an entrance");
        }
        Map<String, List<String>> adjacency = new HashMap<>();
        for (CaveNetworkPlanV2.Node node : nodes) {
            adjacency.put(node.nodeId(), new ArrayList<>());
        }
        for (CaveNetworkPlanV2.Edge edge : edges) {
            adjacency.get(edge.fromNodeId()).add(edge.toNodeId());
            adjacency.get(edge.toNodeId()).add(edge.fromNodeId());
        }
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(entranceNodeIds);
        reachable.addAll(entranceNodeIds);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String next : adjacency.getOrDefault(current, List.of())) {
                if (reachable.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        if (reachable.size() != nodes.size()) {
            boolean hasIsolatedChamber = nodes.stream()
                    .anyMatch(node -> !reachable.contains(node.nodeId())
                            && (node.kind() == CaveNetworkPlanV2.NodeKind.CHAMBER
                            || node.kind() == CaveNetworkPlanV2.NodeKind.DEAD_END));
            throw new CaveNetworkExceptionV2(
                    hasIsolatedChamber
                            ? CaveNetworkFailureCodeV2.ISOLATED_CHAMBER
                            : CaveNetworkFailureCodeV2.DISCONNECTED_GRAPH,
                    "cave-network graph is not reachable from entrances");
        }
    }

    private static VolumeSdfAabbV2 union(VolumeSdfAabbV2 current, VolumeSdfAabbV2 next) {
        if (current == null) {
            return next;
        }
        return new VolumeSdfAabbV2(
                Math.min(current.minXMillionths(), next.minXMillionths()),
                Math.min(current.minYMillionths(), next.minYMillionths()),
                Math.min(current.minZMillionths(), next.minZMillionths()),
                Math.max(current.maxXMillionths(), next.maxXMillionths()),
                Math.max(current.maxYMillionths(), next.maxYMillionths()),
                Math.max(current.maxZMillionths(), next.maxZMillionths()));
    }
}
