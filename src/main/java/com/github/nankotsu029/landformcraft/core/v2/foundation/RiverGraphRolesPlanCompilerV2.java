package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.river.RiverFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles a general river graph with roles, modifiers, and child ownership (V2-9-13).
 * Does not invent FeatureKinds; accepts a frozen manual graph input.
 */
public final class RiverGraphRolesPlanCompilerV2 {
    public static final String RULE_BUDGET = "v2.river-graph-budget";
    public static final String RULE_FLOW = "v2.river-graph-flow";
    public static final String RULE_ELEVATION = "v2.river-graph-elevation";
    public static final String RULE_ORPHAN = "v2.river-graph-orphan";
    public static final String RULE_CYCLE = "v2.river-graph-cycle";
    public static final String RULE_CHILD = "v2.river-graph-child";
    public static final String RULE_MODIFIER = "v2.river-graph-modifier";

    public RiverPlanV2 compile(RiverGraphRolesInputV2 input) {
        Objects.requireNonNull(input, "input");
        if (input.nodes().size() > RiverPlanV2.MAXIMUM_NODES
                || input.reaches().size() > RiverPlanV2.MAXIMUM_REACHES
                || input.children().size() > RiverPlanV2.MAXIMUM_CHILDREN) {
            throw failure(RULE_BUDGET, "node/reach/child budget exceeded");
        }
        if (input.reaches().isEmpty() || input.nodes().size() < 2) {
            throw failure(RULE_ORPHAN, "graph requires nodes and reaches");
        }
        long work = Math.multiplyExact(
                Math.multiplyExact((long) input.width(), input.length()),
                Math.addExact(5L, input.reaches().size()));
        if (work > RiverPlanV2.MAXIMUM_GRAPH_WORK_UNITS) {
            throw failure(RULE_BUDGET, "graph/raster work budget exceeded");
        }
        Map<String, NodeSpec> nodesById = new HashMap<>();
        for (NodeSpec node : input.nodes()) {
            if (nodesById.put(node.nodeId(), node) != null) {
                throw failure(RULE_ORPHAN, "duplicate node id");
            }
        }
        Set<String> reachIds = new HashSet<>();
        for (ReachSpec reach : input.reaches()) {
            if (!reachIds.add(reach.reachId())) {
                throw failure(RULE_ORPHAN, "duplicate reach id");
            }
            if (!nodesById.containsKey(reach.fromNodeId()) || !nodesById.containsKey(reach.toNodeId())) {
                throw failure(RULE_ORPHAN, "reach references unknown node");
            }
            if (!isModifierOrderStable(reach.modifiers())) {
                throw failure(RULE_MODIFIER, "reach modifier order is unstable");
            }
        }
        for (ChildSpec child : input.children()) {
            switch (child.kind()) {
                case SANDBAR, RIVER_ISLAND -> {
                    if (!reachIds.contains(child.ownerReachId())) {
                        throw failure(RULE_CHILD, "child owner reach missing");
                    }
                }
                case PLUNGE_POOL -> {
                    NodeSpec owner = nodesById.get(child.ownerNodeId());
                    if (owner == null || owner.kind() != RiverPlanV2.NodeKind.WATERFALL) {
                        throw failure(RULE_CHILD, "plunge-pool owner must be WATERFALL");
                    }
                }
            }
        }
        List<RiverPlanV2.CenterlineSample> centerline = buildMainStemCenterline(input, nodesById);
        RiverPlanV2.CenterlineSample first = centerline.getFirst();
        RiverPlanV2.CenterlineSample last = centerline.getLast();
        List<RiverPlanV2.Node> nodes = input.nodes().stream()
                .sorted(Comparator.comparing(NodeSpec::nodeId))
                .map(node -> new RiverPlanV2.Node(
                        node.nodeId(), node.kind(), node.role(),
                        node.xMillionths(), node.zMillionths(), node.bedYMillionths()))
                .toList();
        List<RiverPlanV2.Reach> reaches = input.reaches().stream()
                .sorted(Comparator.comparing(ReachSpec::reachId))
                .map(reach -> new RiverPlanV2.Reach(
                        reach.reachId(), reach.fromNodeId(), reach.toNodeId(),
                        reach.reachClass(),
                        List.copyOf(reach.modifiers()),
                        reach.dischargeShareMillionths()))
                .toList();
        List<RiverPlanV2.ChildFeature> children = input.children().stream()
                .sorted(Comparator.comparing(ChildSpec::childId))
                .map(child -> new RiverPlanV2.ChildFeature(
                        child.childId(), child.kind(),
                        child.ownerReachId() == null ? "" : child.ownerReachId(),
                        child.ownerNodeId() == null ? "" : child.ownerNodeId(),
                        child.xMillionths(), child.zMillionths(),
                        child.radiusBlocks(), child.ownershipPriority()))
                .toList();
        try {
            return new RiverPlanV2(
                    RiverPlanV2.VERSION,
                    input.featureId(),
                    input.dischargeClass(),
                    nodes,
                    reaches,
                    children,
                    centerline,
                    last.arcLengthMillionths(),
                    first.bedYMillionths(),
                    last.bedYMillionths(),
                    input.bankfullWidthBlocks(),
                    Math.max(1, (input.bankfullWidthBlocks() + 3) / 4),
                    input.floodplainHandoffWidthBlocks(),
                    input.minimumBedSlopeMillionths(),
                    switch (input.dischargeClass()) {
                        case SMALL -> 1;
                        case MEDIUM -> 2;
                        case LARGE -> 3;
                    },
                    input.minY(),
                    input.maxY(),
                    input.waterLevel(),
                    input.width(),
                    input.length(),
                    RiverPlanV2.CHANNEL_MASK_FIELD_ID,
                    RiverPlanV2.BANK_MASK_FIELD_ID,
                    RiverPlanV2.FLOODPLAIN_MASK_FIELD_ID,
                    RiverPlanV2.BED_ELEVATION_FIELD_ID,
                    RiverPlanV2.DISCHARGE_INDEX_FIELD_ID,
                    Math.max(input.floodplainHandoffWidthBlocks(), input.supportRadiusXZ()),
                    work,
                    input.geometryChecksum(),
                    "0".repeat(64));
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage() == null ? "graph rejected" : exception.getMessage();
            if (message.contains("flow conservation")) {
                throw failure(RULE_FLOW, message);
            }
            if (message.contains("elevation") || message.contains("bed")) {
                throw failure(RULE_ELEVATION, message);
            }
            if (message.contains("cycle")) {
                throw failure(RULE_CYCLE, message);
            }
            if (message.contains("orphan") || message.contains("reachability") || message.contains("unknown")) {
                throw failure(RULE_ORPHAN, message);
            }
            if (message.contains("child") || message.contains("plunge") || message.contains("sandbar")) {
                throw failure(RULE_CHILD, message);
            }
            if (message.contains("budget") || message.contains("degree")) {
                throw failure(RULE_BUDGET, message);
            }
            throw failure(RULE_ORPHAN, message);
        }
    }

    private static List<RiverPlanV2.CenterlineSample> buildMainStemCenterline(
            RiverGraphRolesInputV2 input,
            Map<String, NodeSpec> nodesById
    ) {
        List<NodeSpec> path = mainStemPath(input, nodesById);
        if (path.size() < 2) {
            throw failure(RULE_ORPHAN, "main-stem path requires at least two nodes");
        }
        List<RiverPlanV2.CenterlineSample> samples = new ArrayList<>();
        long arc = 0L;
        NodeSpec previous = null;
        for (int index = 0; index < path.size(); index++) {
            NodeSpec node = path.get(index);
            if (previous != null) {
                arc = Math.addExact(arc, RiverFixedMathV2.hypot(
                        node.xMillionths() - previous.xMillionths(),
                        node.zMillionths() - previous.zMillionths()));
                if (node.bedYMillionths() > previous.bedYMillionths()) {
                    throw failure(RULE_ELEVATION, "main-stem bed must be non-increasing");
                }
            }
            samples.add(new RiverPlanV2.CenterlineSample(
                    index, node.xMillionths(), node.zMillionths(), arc, node.bedYMillionths()));
            previous = node;
        }
        if (arc < TerrainIntentV2.FIXED_SCALE) {
            throw failure(RULE_ELEVATION, "main-stem arc shorter than one block");
        }
        return List.copyOf(samples);
    }

    private static List<NodeSpec> mainStemPath(
            RiverGraphRolesInputV2 input,
            Map<String, NodeSpec> nodesById
    ) {
        Map<String, List<ReachSpec>> outgoing = new HashMap<>();
        for (NodeSpec node : input.nodes()) {
            outgoing.put(node.nodeId(), new ArrayList<>());
        }
        for (ReachSpec reach : input.reaches()) {
            outgoing.get(reach.fromNodeId()).add(reach);
        }
        NodeSpec source = input.nodes().stream()
                .filter(node -> node.kind() == RiverPlanV2.NodeKind.SOURCE)
                .min(Comparator.comparing(NodeSpec::nodeId))
                .orElseThrow(() -> failure(RULE_ORPHAN, "missing SOURCE"));
        NodeSpec mouth = input.nodes().stream()
                .filter(node -> node.kind() == RiverPlanV2.NodeKind.MOUTH)
                .findFirst()
                .orElseThrow(() -> failure(RULE_ORPHAN, "missing MOUTH"));
        List<NodeSpec> best = new ArrayList<>();
        List<NodeSpec> current = new ArrayList<>();
        dfsMainStem(source.nodeId(), mouth.nodeId(), outgoing, nodesById, current, best);
        return best;
    }

    private static void dfsMainStem(
            String nodeId,
            String mouthId,
            Map<String, List<ReachSpec>> outgoing,
            Map<String, NodeSpec> nodesById,
            List<NodeSpec> current,
            List<NodeSpec> best
    ) {
        current.add(nodesById.get(nodeId));
        if (nodeId.equals(mouthId)) {
            if (best.isEmpty() || score(current) > score(best)) {
                best.clear();
                best.addAll(current);
            }
            current.removeLast();
            return;
        }
        List<ReachSpec> edges = outgoing.getOrDefault(nodeId, List.of()).stream()
                .sorted(Comparator
                        .comparing((ReachSpec reach) -> reach.reachClass() == RiverPlanV2.ReachClass.MAIN_STEM ? 0 : 1)
                        .thenComparing(ReachSpec::reachId))
                .toList();
        for (ReachSpec edge : edges) {
            dfsMainStem(edge.toNodeId(), mouthId, outgoing, nodesById, current, best);
        }
        current.removeLast();
    }

    private static int score(List<NodeSpec> path) {
        int mainStemHops = 0;
        // Prefer longer paths that include WATERFALL nodes for chain fixtures.
        for (NodeSpec node : path) {
            if (node.kind() == RiverPlanV2.NodeKind.WATERFALL) {
                mainStemHops += 10;
            }
            mainStemHops += 1;
        }
        return mainStemHops;
    }

    private static boolean isModifierOrderStable(List<RiverPlanV2.ReachModifier> modifiers) {
        List<RiverPlanV2.ReachModifier> sorted = modifiers.stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        return sorted.equals(modifiers);
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }

    public record RiverGraphRolesInputV2(
            String featureId,
            TerrainIntentV2.DischargeClass dischargeClass,
            List<NodeSpec> nodes,
            List<ReachSpec> reaches,
            List<ChildSpec> children,
            int bankfullWidthBlocks,
            int floodplainHandoffWidthBlocks,
            long minimumBedSlopeMillionths,
            int minY,
            int maxY,
            int waterLevel,
            int width,
            int length,
            int supportRadiusXZ,
            String geometryChecksum
    ) {
        public RiverGraphRolesInputV2 {
            Objects.requireNonNull(featureId, "featureId");
            Objects.requireNonNull(dischargeClass, "dischargeClass");
            Objects.requireNonNull(nodes, "nodes");
            Objects.requireNonNull(reaches, "reaches");
            Objects.requireNonNull(children, "children");
            Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        }
    }

    public record NodeSpec(
            String nodeId,
            RiverPlanV2.NodeKind kind,
            RiverPlanV2.NodeRole role,
            long xMillionths,
            long zMillionths,
            long bedYMillionths
    ) {
    }

    public record ReachSpec(
            String reachId,
            String fromNodeId,
            String toNodeId,
            RiverPlanV2.ReachClass reachClass,
            List<RiverPlanV2.ReachModifier> modifiers,
            int dischargeShareMillionths
    ) {
        public ReachSpec {
            modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        }
    }

    public record ChildSpec(
            String childId,
            RiverPlanV2.ChildKind kind,
            String ownerReachId,
            String ownerNodeId,
            long xMillionths,
            long zMillionths,
            int radiusBlocks,
            int ownershipPriority
    ) {
    }
}
