package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-06 {@code CAVE_NETWORK} plan. Holds a stable tunnel/chamber graph, entrance
 * bindings, minimum roof, and conservative AABB. SDF／CSG artifacts are bound by checksum.
 * Lush ecology, underground lakes, and sea caves are out of scope.
 */
public record CaveNetworkPlanV2(
        int planVersion,
        String caveContractVersion,
        String featureId,
        Kernel kernel,
        List<Node> nodes,
        List<Edge> edges,
        List<String> entranceNodeIds,
        VolumeSdfAabbV2 aabb,
        int surfaceHeightBlocks,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CAVE_CONTRACT_VERSION = "cave-network-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 64L * 1024L;
    public static final int MAXIMUM_NODES = 32;
    public static final int MAXIMUM_EDGES = 64;
    public static final int MAXIMUM_ROOF_BLOCKS = 64;
    public static final long MAXIMUM_RADIUS_MILLIONTHS = 16_000_000L;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public CaveNetworkPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("cave-network planVersion must be 1");
        }
        caveContractVersion = nonBlank(caveContractVersion, "caveContractVersion", 64);
        if (!CAVE_CONTRACT_VERSION.equals(caveContractVersion)) {
            throw new IllegalArgumentException("unknown cave-network contract version");
        }
        featureId = qualified(featureId, "featureId");
        Objects.requireNonNull(kernel, "kernel");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        entranceNodeIds = List.copyOf(Objects.requireNonNull(entranceNodeIds, "entranceNodeIds"));
        Objects.requireNonNull(aabb, "aabb");
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (nodes.isEmpty() || nodes.size() > MAXIMUM_NODES
                || edges.size() > MAXIMUM_EDGES
                || entranceNodeIds.isEmpty()
                || entranceNodeIds.size() > nodes.size()) {
            throw new IllegalArgumentException("cave-network graph size out of range");
        }
        if (surfaceHeightBlocks < -512 || surfaceHeightBlocks > 512) {
            throw new IllegalArgumentException("cave-network surfaceHeightBlocks out of range");
        }
        validateGraph(nodes, edges, entranceNodeIds, kernel);
        validateBudget(budget, nodes, edges);
    }

    public CaveNetworkPlanV2 withCanonicalChecksum(String checksum) {
        return new CaveNetworkPlanV2(
                planVersion, caveContractVersion, featureId, kernel, nodes, edges, entranceNodeIds,
                aabb, surfaceHeightBlocks, sdfPlanBinding, csgPlanBinding, budget, checksum);
    }

    public enum NodeKind {
        CHAMBER,
        JUNCTION,
        ENTRANCE,
        DEAD_END
    }

    public record Kernel(
            String kernelVersion,
            int minimumRoofBlocks,
            int maximumNodes,
            int maximumEdges,
            long maximumRadiusMillionths
    ) {
        public static final String VERSION = "cave-network-v1";

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown cave-network kernel version");
            }
            if (minimumRoofBlocks < 1 || minimumRoofBlocks > MAXIMUM_ROOF_BLOCKS
                    || maximumNodes < 1 || maximumNodes > MAXIMUM_NODES
                    || maximumEdges < 0 || maximumEdges > MAXIMUM_EDGES
                    || maximumRadiusMillionths < 1_000_000L
                    || maximumRadiusMillionths > MAXIMUM_RADIUS_MILLIONTHS) {
                throw new IllegalArgumentException("cave-network kernel budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(VERSION, 3, MAXIMUM_NODES, MAXIMUM_EDGES, MAXIMUM_RADIUS_MILLIONTHS);
        }
    }

    public record Node(
            String nodeId,
            NodeKind kind,
            VolumeSdfVec3V2 center,
            long radiusMillionths
    ) {
        public Node {
            nodeId = qualified(nodeId, "nodeId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(center, "center");
            if (radiusMillionths < 1_000_000L || radiusMillionths > MAXIMUM_RADIUS_MILLIONTHS) {
                throw new IllegalArgumentException("cave-network node radius out of range");
            }
        }
    }

    public record Edge(
            String edgeId,
            String fromNodeId,
            String toNodeId,
            long radiusMillionths
    ) {
        public Edge {
            edgeId = qualified(edgeId, "edgeId");
            fromNodeId = qualified(fromNodeId, "fromNodeId");
            toNodeId = qualified(toNodeId, "toNodeId");
            if (fromNodeId.equals(toNodeId)) {
                throw new IllegalArgumentException("cave-network edge must connect distinct nodes");
            }
            if (radiusMillionths < 1_000_000L || radiusMillionths > MAXIMUM_RADIUS_MILLIONTHS) {
                throw new IllegalArgumentException("cave-network edge radius out of range");
            }
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "cave-network-sdf-binding-v1";
        public static final String CSG_CONTRACT = "cave-network-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("unknown cave-network artifact binding version");
            }
            bindingContractVersion = nonBlank(bindingContractVersion, "bindingContractVersion", 64);
            if (!SDF_CONTRACT.equals(bindingContractVersion)
                    && !CSG_CONTRACT.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown cave-network artifact binding");
            }
            sourceArtifactChecksum = checksum(sourceArtifactChecksum, "sourceArtifactChecksum");
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumNodes,
            int maximumEdges,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes,
            long maximumWorkingBytes,
            long maximumGraphBytes
    ) {
        public static final String VERSION = "cave-network-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown cave-network budget version");
            }
            if (maximumNodes < 1 || maximumNodes > MAXIMUM_NODES
                    || maximumEdges < 0 || maximumEdges > MAXIMUM_EDGES
                    || estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1
                    || maximumGraphBytes < 1) {
                throw new IllegalArgumentException("cave-network budget out of range");
            }
        }

        public static ResourceBudget standard(int nodeCount, int edgeCount) {
            long estimate = 2048L + nodeCount * 128L + edgeCount * 96L;
            return new ResourceBudget(
                    VERSION,
                    MAXIMUM_NODES,
                    MAXIMUM_EDGES,
                    estimate,
                    MAX_CANONICAL_BYTES,
                    256L * 1024L,
                    64L * 1024L);
        }
    }

    private static void validateGraph(
            List<Node> nodes,
            List<Edge> edges,
            List<String> entranceNodeIds,
            Kernel kernel
    ) {
        if (nodes.size() > kernel.maximumNodes() || edges.size() > kernel.maximumEdges()) {
            throw new IllegalArgumentException("cave-network exceeds kernel graph budget");
        }
        Set<String> nodeIds = new HashSet<>();
        Set<String> entranceKinds = new HashSet<>();
        for (Node node : nodes) {
            if (!nodeIds.add(node.nodeId())) {
                throw new IllegalArgumentException("duplicate cave-network node id");
            }
            if (node.radiusMillionths() > kernel.maximumRadiusMillionths()) {
                throw new IllegalArgumentException("cave-network node radius exceeds kernel");
            }
            if (node.kind() == NodeKind.ENTRANCE) {
                entranceKinds.add(node.nodeId());
            }
        }
        Set<String> entrances = new HashSet<>();
        for (String entranceId : entranceNodeIds) {
            if (!entrances.add(entranceId)) {
                throw new IllegalArgumentException("duplicate cave-network entrance id");
            }
            if (!nodeIds.contains(entranceId)) {
                throw new IllegalArgumentException("unknown cave-network entrance node");
            }
            Node node = nodes.stream().filter(n -> n.nodeId().equals(entranceId)).findFirst().orElseThrow();
            if (node.kind() != NodeKind.ENTRANCE) {
                throw new IllegalArgumentException("entrance list requires ENTRANCE kind nodes");
            }
        }
        if (!entranceKinds.equals(entrances)) {
            throw new IllegalArgumentException("all ENTRANCE nodes must be listed in entranceNodeIds");
        }
        Set<String> edgeIds = new HashSet<>();
        for (Edge edge : edges) {
            if (!edgeIds.add(edge.edgeId())) {
                throw new IllegalArgumentException("duplicate cave-network edge id");
            }
            if (!nodeIds.contains(edge.fromNodeId()) || !nodeIds.contains(edge.toNodeId())) {
                throw new IllegalArgumentException("cave-network edge references unknown node");
            }
            if (edge.radiusMillionths() > kernel.maximumRadiusMillionths()) {
                throw new IllegalArgumentException("cave-network edge radius exceeds kernel");
            }
        }
    }

    private static void validateBudget(ResourceBudget budget, List<Node> nodes, List<Edge> edges) {
        if (nodes.size() > budget.maximumNodes() || edges.size() > budget.maximumEdges()) {
            throw new IllegalArgumentException("cave-network exceeds resource graph budget");
        }
    }

    private static String qualified(String value, String name) {
        if (value == null || !QUALIFIED.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " invalid");
        }
        return value;
    }

    private static String nonBlank(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " invalid");
        }
        return value;
    }

    private static String checksum(String value, String name) {
        if (value == null || !CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be 64 lowercase hex");
        }
        return value;
    }
}
