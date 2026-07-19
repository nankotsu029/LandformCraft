package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Central typed karst hydrology graph freeze. {@code KARST_CAVE_SYSTEM} is a graph node role
 * on a sealed {@code CAVE_NETWORK}, not a FeatureKind.
 */
public record KarstHydrologyGraphPlanV2(
        int planVersion,
        String graphId,
        String contractVersion,
        List<Node> nodes,
        List<Edge> edges,
        String sinkholeFeatureId,
        String springFeatureId,
        String caveNetworkFeatureId,
        String caveNetworkCanonicalChecksum,
        String undergroundRiverFeatureId,
        String undergroundRiverPlanChecksum,
        String sinkholePlanChecksum,
        String springPlanChecksum,
        int lossVolumeBlocks,
        int springDischargeBlocks,
        String materialHandoffId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.karst-hydrology-graph";
    public static final String MODULE_VERSION = "0.1.0-v2-10-03";
    public static final String CONTRACT = "karst-hydrology-graph-plan-contract-v1";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<String> EDGE_KINDS = Set.of(
            "DRAINS_TO", "WITHIN", "ORIGINATES_AT", "ENTRANCE_OF", "EMPTIES_INTO", "UPSTREAM_OF");

    public KarstHydrologyGraphPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("karst hydrology graph planVersion must be 1");
        }
        graphId = FoundationValidationV2.slug(graphId, "graphId");
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown karst hydrology graph contract version");
        }
        nodes = FoundationValidationV2.immutable(nodes, "nodes", 16).stream()
                .sorted(Comparator.comparing(Node::nodeId))
                .toList();
        edges = FoundationValidationV2.immutable(edges, "edges", 32).stream()
                .sorted(Comparator.comparing(Edge::edgeId))
                .toList();
        sinkholeFeatureId = FoundationValidationV2.slug(sinkholeFeatureId, "sinkholeFeatureId");
        springFeatureId = FoundationValidationV2.slug(springFeatureId, "springFeatureId");
        caveNetworkFeatureId = FoundationValidationV2.slug(caveNetworkFeatureId, "caveNetworkFeatureId");
        caveNetworkCanonicalChecksum = requireChecksum(caveNetworkCanonicalChecksum, "caveNetworkCanonicalChecksum");
        undergroundRiverFeatureId = FoundationValidationV2.optionalSlug(
                undergroundRiverFeatureId, "undergroundRiverFeatureId");
        if (undergroundRiverPlanChecksum == null || undergroundRiverPlanChecksum.isBlank()) {
            undergroundRiverPlanChecksum = "";
        } else {
            undergroundRiverPlanChecksum = requireChecksum(
                    undergroundRiverPlanChecksum, "undergroundRiverPlanChecksum");
        }
        sinkholePlanChecksum = requireChecksum(sinkholePlanChecksum, "sinkholePlanChecksum");
        springPlanChecksum = requireChecksum(springPlanChecksum, "springPlanChecksum");
        if (lossVolumeBlocks < 1 || lossVolumeBlocks > 1_000_000
                || springDischargeBlocks < 1 || springDischargeBlocks > 1_000_000) {
            throw new IllegalArgumentException("karst hydrology volume blocks are invalid");
        }
        if (lossVolumeBlocks != springDischargeBlocks) {
            throw new IllegalArgumentException("lossVolumeBlocks must equal springDischargeBlocks");
        }
        materialHandoffId = FoundationValidationV2.qualified(materialHandoffId, "materialHandoffId");
        if (!SinkholePlanV2.MATERIAL_HANDOFF_ID.equals(materialHandoffId)) {
            throw new IllegalArgumentException("karst graph materialHandoffId must be karst-limestone-handoff");
        }
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("karst graph support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        validateGraphTopology(nodes, edges, caveNetworkFeatureId);
    }

    public KarstHydrologyGraphPlanV2 withCanonicalChecksum(String checksum) {
        return new KarstHydrologyGraphPlanV2(
                planVersion, graphId, contractVersion, nodes, edges, sinkholeFeatureId, springFeatureId,
                caveNetworkFeatureId, caveNetworkCanonicalChecksum, undergroundRiverFeatureId,
                undergroundRiverPlanChecksum, sinkholePlanChecksum, springPlanChecksum,
                lossVolumeBlocks, springDischargeBlocks, materialHandoffId, supportRadiusXZ,
                estimatedWorkUnits, geometryChecksum, checksum);
    }

    public enum NodeRole {
        SINKHOLE, UNDERGROUND_RIVER, KARST_CAVE_SYSTEM, KARST_SPRING
    }

    public record Node(String nodeId, NodeRole role, String featureId, String planChecksum) {
        public Node {
            nodeId = FoundationValidationV2.slug(nodeId, "nodeId");
            Objects.requireNonNull(role, "role");
            featureId = FoundationValidationV2.slug(featureId, "featureId");
            planChecksum = FoundationValidationV2.checksum(planChecksum, "planChecksum");
        }
    }

    public record Edge(String edgeId, String fromNodeId, String toNodeId, String relationKind) {
        public Edge {
            edgeId = FoundationValidationV2.slug(edgeId, "edgeId");
            fromNodeId = FoundationValidationV2.slug(fromNodeId, "fromNodeId");
            toNodeId = FoundationValidationV2.slug(toNodeId, "toNodeId");
            relationKind = FoundationValidationV2.nonBlank(relationKind, "relationKind", 32);
            if (!EDGE_KINDS.contains(relationKind)) {
                throw new IllegalArgumentException("unknown karst graph edge relationKind");
            }
        }
    }

    private static void validateGraphTopology(
            List<Node> nodes,
            List<Edge> edges,
            String caveNetworkFeatureId
    ) {
        long sinkholes = nodes.stream().filter(node -> node.role() == NodeRole.SINKHOLE).count();
        long caves = nodes.stream().filter(node -> node.role() == NodeRole.KARST_CAVE_SYSTEM).count();
        long springs = nodes.stream().filter(node -> node.role() == NodeRole.KARST_SPRING).count();
        long rivers = nodes.stream().filter(node -> node.role() == NodeRole.UNDERGROUND_RIVER).count();
        if (sinkholes != 1L || caves != 1L || springs != 1L || rivers > 1L) {
            throw new IllegalArgumentException("karst graph requires exactly one sinkhole, cave system, and spring");
        }
        Node caveNode = nodes.stream()
                .filter(node -> node.role() == NodeRole.KARST_CAVE_SYSTEM)
                .findFirst()
                .orElseThrow();
        if (!caveNode.featureId().equals(caveNetworkFeatureId)) {
            throw new IllegalArgumentException("KARST_CAVE_SYSTEM node featureId must match caveNetworkFeatureId");
        }
        Set<String> nodeIds = new HashSet<>();
        for (Node node : nodes) {
            if (!nodeIds.add(node.nodeId())) {
                throw new IllegalArgumentException("duplicate karst graph nodeId");
            }
        }
        Map<String, List<String>> adjacency = new HashMap<>();
        for (Edge edge : edges) {
            if (!nodeIds.contains(edge.fromNodeId()) || !nodeIds.contains(edge.toNodeId())) {
                throw new IllegalArgumentException("karst graph edge references unknown node");
            }
            adjacency.computeIfAbsent(edge.fromNodeId(), key -> new ArrayList<>()).add(edge.toNodeId());
        }
        if (hasCycle(adjacency, nodeIds)) {
            throw new IllegalArgumentException("karst hydrology graph edges must form a DAG");
        }
    }

    private static boolean hasCycle(Map<String, List<String>> adjacency, Set<String> nodeIds) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String nodeId : nodeIds) {
            if (!visited.contains(nodeId) && dfsCycle(nodeId, adjacency, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean dfsCycle(
            String nodeId,
            Map<String, List<String>> adjacency,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (visiting.contains(nodeId)) {
            return true;
        }
        if (!visited.add(nodeId)) {
            return false;
        }
        visiting.add(nodeId);
        for (String next : adjacency.getOrDefault(nodeId, List.of())) {
            if (dfsCycle(next, adjacency, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        return false;
    }

    public boolean reachableFromSinkholeToSpring() {
        String sinkNodeId = nodes.stream()
                .filter(node -> node.role() == NodeRole.SINKHOLE)
                .map(Node::nodeId)
                .findFirst()
                .orElseThrow();
        String springNodeId = nodes.stream()
                .filter(node -> node.role() == NodeRole.KARST_SPRING)
                .map(Node::nodeId)
                .findFirst()
                .orElseThrow();
        Map<String, List<String>> adjacency = new HashMap<>();
        for (Edge edge : edges) {
            adjacency.computeIfAbsent(edge.fromNodeId(), key -> new ArrayList<>()).add(edge.toNodeId());
        }
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(List.of(sinkNodeId));
        visited.add(sinkNodeId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (current.equals(springNodeId)) {
                return true;
            }
            for (String next : adjacency.getOrDefault(current, List.of())) {
                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return false;
    }

    private static String requireChecksum(String value, String field) {
        String checksum = FoundationValidationV2.checksum(value, field);
        if (!CHECKSUM.matcher(checksum).matches()) {
            throw new IllegalArgumentException(field + " must be 64 lowercase hex");
        }
        return checksum;
    }
}
