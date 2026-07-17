package com.github.nankotsu029.landformcraft.model.v2.hydrology;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Versioned, routing-independent V2-3 hydrology graph contract. */
public record HydrologyPlanV2(
        int planVersion,
        String graphContractVersion,
        String moduleId,
        String moduleVersion,
        FixedPriors fixedPriors,
        List<DrainageBasin> basins,
        List<HydrologyNode> nodes,
        List<HydrologyReach> reaches,
        List<WaterBodyPlan> waterBodies,
        List<FallPlan> fallPlans,
        List<FieldBinding> fields,
        GraphWorkBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String GRAPH_CONTRACT_VERSION = "hydrology-graph-v1";

    private static final int MAX_BASINS = 256;
    private static final int MAX_NODES = 4_096;
    private static final int MAX_REACHES = 8_192;
    private static final int MAX_WATER_BODIES = 1_024;
    private static final int MAX_FALLS = 256;
    private static final int MAX_FIELDS = 32;

    public HydrologyPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("hydrology planVersion must be 1");
        graphContractVersion = nonBlank(graphContractVersion, "graphContractVersion", 64);
        if (!GRAPH_CONTRACT_VERSION.equals(graphContractVersion)) {
            throw new IllegalArgumentException("unknown hydrology graph contract version");
        }
        moduleId = qualifiedId(moduleId, "moduleId");
        moduleVersion = nonBlank(moduleVersion, "moduleVersion", 64);
        Objects.requireNonNull(fixedPriors, "fixedPriors");
        basins = sorted(basins, "basins", MAX_BASINS, Comparator.comparing(DrainageBasin::basinId));
        nodes = sorted(nodes, "nodes", MAX_NODES, Comparator.comparing(HydrologyNode::nodeId));
        reaches = sorted(reaches, "reaches", MAX_REACHES, Comparator.comparing(HydrologyReach::reachId));
        waterBodies = sorted(
                waterBodies, "waterBodies", MAX_WATER_BODIES, Comparator.comparing(WaterBodyPlan::waterBodyId));
        fallPlans = sorted(fallPlans, "fallPlans", MAX_FALLS, Comparator.comparing(FallPlan::fallId));
        fields = sorted(fields, "fields", MAX_FIELDS, Comparator.comparing(FieldBinding::fieldId));
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateGraph(basins, nodes, reaches, waterBodies, fallPlans, fields, budget);
    }

    public HydrologyPlanV2 withCanonicalChecksum(String checksum) {
        return new HydrologyPlanV2(
                planVersion, graphContractVersion, moduleId, moduleVersion, fixedPriors,
                basins, nodes, reaches, waterBodies, fallPlans, fields, budget, checksum);
    }

    /** V2-3 bootstrap priors. V2-4 may only replace them through a new versioned binding. */
    public record FixedPriors(
            int priorVersion,
            GeologyPriorKind geologyPrior,
            int uniformHardnessMillionths,
            int uniformPermeabilityMillionths,
            RunoffPriorKind runoffPrior,
            int constantRunoffMillionths,
            String priorChecksum
    ) {
        public static final int VERSION = 1;
        public static final int UNIFORM_HARDNESS_MILLIONTHS = 500_000;
        public static final int UNIFORM_PERMEABILITY_MILLIONTHS = 500_000;
        public static final int CONSTANT_RUNOFF_MILLIONTHS = 1_000_000;
        private static final String CANONICAL_PRIOR = String.join("\n",
                "priorVersion=1",
                "geologyPrior=UNIFORM_GEOLOGY_PRIOR",
                "uniformHardnessMillionths=500000",
                "uniformPermeabilityMillionths=500000",
                "runoffPrior=CONSTANT_RUNOFF_PRIOR",
                "constantRunoffMillionths=1000000");
        public static final String CHECKSUM = sha256(CANONICAL_PRIOR);

        public FixedPriors {
            if (priorVersion != VERSION
                    || geologyPrior != GeologyPriorKind.UNIFORM_GEOLOGY_PRIOR
                    || uniformHardnessMillionths != UNIFORM_HARDNESS_MILLIONTHS
                    || uniformPermeabilityMillionths != UNIFORM_PERMEABILITY_MILLIONTHS
                    || runoffPrior != RunoffPriorKind.CONSTANT_RUNOFF_PRIOR
                    || constantRunoffMillionths != CONSTANT_RUNOFF_MILLIONTHS) {
                throw new IllegalArgumentException("unknown or mutable hydrology prior contract");
            }
            priorChecksum = checksum(priorChecksum, "priorChecksum");
            if (!CHECKSUM.equals(priorChecksum)) {
                throw new IllegalArgumentException("hydrology fixed prior checksum mismatch");
            }
        }

        public static FixedPriors v2Phase3Defaults() {
            return new FixedPriors(
                    VERSION,
                    GeologyPriorKind.UNIFORM_GEOLOGY_PRIOR,
                    UNIFORM_HARDNESS_MILLIONTHS,
                    UNIFORM_PERMEABILITY_MILLIONTHS,
                    RunoffPriorKind.CONSTANT_RUNOFF_PRIOR,
                    CONSTANT_RUNOFF_MILLIONTHS,
                    CHECKSUM);
        }
    }

    public enum GeologyPriorKind { UNIFORM_GEOLOGY_PRIOR }
    public enum RunoffPriorKind { CONSTANT_RUNOFF_PRIOR }
    public enum RunoffClass { FIXED_PRIOR }
    public enum TerminalType { SEA, LAKE, CLOSED }
    public enum NodeKind {
        SOURCE,
        CONFLUENCE,
        BIFURCATION,
        LAKE_INLET,
        LAKE_OUTLET,
        WATERFALL_LIP,
        WATERFALL_BASE,
        MOUTH,
        SPRING,
        TIDAL_BOUNDARY
    }
    public enum ReachKind { RIVER, DISTRIBUTARY, SPILLWAY, MARINE_CHANNEL, TIDAL_CHANNEL }
    public enum WaterBodyKind { RIVER, LAKE, SEA, CLOSED_BASIN }
    public enum FieldSemantic {
        WATER_BODY_ID,
        FLOW_DIRECTION,
        FLOW_ACCUMULATION,
        BED_ELEVATION,
        WATER_SURFACE,
        WATER_DEPTH
    }
    public enum FieldValueType { U8, I32 }
    public enum Ownership { SINGLE_OWNER }

    public record DrainageBasin(
            String basinId,
            String outletNodeId,
            List<String> sourceNodeIds,
            long areaBlocksSquared,
            RunoffClass runoffClass,
            TerminalType terminalType,
            String waterBodyId
    ) {
        public DrainageBasin {
            basinId = slug(basinId, "basinId");
            outletNodeId = slug(outletNodeId, "outletNodeId");
            sourceNodeIds = sortedStrings(sourceNodeIds, "sourceNodeIds", 1_024, true);
            if (areaBlocksSquared < 1 || areaBlocksSquared > 1_000_000L) {
                throw new IllegalArgumentException("basin area outside 1..1000000");
            }
            Objects.requireNonNull(runoffClass, "runoffClass");
            Objects.requireNonNull(terminalType, "terminalType");
            waterBodyId = slug(waterBodyId, "waterBodyId");
        }
    }

    public record HydrologyNode(
            String nodeId,
            String basinId,
            NodeKind kind,
            long xMillionths,
            long zMillionths,
            long bedYMillionths,
            long waterSurfaceYMillionths,
            List<String> incomingReachIds,
            List<String> outgoingReachIds
    ) {
        public HydrologyNode {
            nodeId = slug(nodeId, "nodeId");
            basinId = slug(basinId, "basinId");
            Objects.requireNonNull(kind, "kind");
            if (xMillionths < 0 || xMillionths > 999_000_000L
                    || zMillionths < 0 || zMillionths > 999_000_000L) {
                throw new IllegalArgumentException("hydrology node coordinate outside release-local bounds");
            }
            if (bedYMillionths < -512_000_000L || bedYMillionths > 1_024_000_000L
                    || waterSurfaceYMillionths < bedYMillionths
                    || waterSurfaceYMillionths > 1_024_000_000L) {
                throw new IllegalArgumentException("hydrology node elevation range is invalid");
            }
            incomingReachIds = sortedStrings(incomingReachIds, "incomingReachIds", 64, false);
            outgoingReachIds = sortedStrings(outgoingReachIds, "outgoingReachIds", 64, false);
        }
    }

    public record HydrologyReach(
            String reachId,
            String basinId,
            ReachKind kind,
            String fromNodeId,
            String toNodeId,
            String waterBodyId,
            int rasterSupportRadiusXZ
    ) {
        public HydrologyReach {
            reachId = slug(reachId, "reachId");
            basinId = slug(basinId, "basinId");
            Objects.requireNonNull(kind, "kind");
            fromNodeId = slug(fromNodeId, "fromNodeId");
            toNodeId = slug(toNodeId, "toNodeId");
            if (fromNodeId.equals(toNodeId)) throw new IllegalArgumentException("hydrology reach must not self-loop");
            waterBodyId = slug(waterBodyId, "waterBodyId");
            if (rasterSupportRadiusXZ < 0 || rasterSupportRadiusXZ > 256) {
                throw new IllegalArgumentException("reach support radius outside 0..256");
            }
        }
    }

    public record WaterBodyPlan(
            String waterBodyId,
            WaterBodyKind kind,
            String basinId,
            List<String> nodeIds,
            long minimumSurfaceYMillionths,
            long maximumSurfaceYMillionths
    ) {
        public WaterBodyPlan {
            waterBodyId = slug(waterBodyId, "waterBodyId");
            Objects.requireNonNull(kind, "kind");
            basinId = slug(basinId, "basinId");
            nodeIds = sortedStrings(nodeIds, "water body nodeIds", 4_096, true);
            if (minimumSurfaceYMillionths < -512_000_000L
                    || maximumSurfaceYMillionths < minimumSurfaceYMillionths
                    || maximumSurfaceYMillionths > 1_024_000_000L) {
                throw new IllegalArgumentException("water body surface range is invalid");
            }
        }
    }

    public record FallPlan(
            String fallId,
            String basinId,
            String waterBodyId,
            String lipNodeId,
            String baseNodeId,
            String upstreamReachId,
            String downstreamReachId,
            long dropMillionths
    ) {
        public FallPlan {
            fallId = slug(fallId, "fallId");
            basinId = slug(basinId, "basinId");
            waterBodyId = slug(waterBodyId, "waterBodyId");
            lipNodeId = slug(lipNodeId, "lipNodeId");
            baseNodeId = slug(baseNodeId, "baseNodeId");
            if (lipNodeId.equals(baseNodeId)) throw new IllegalArgumentException("fall lip and base must differ");
            upstreamReachId = slug(upstreamReachId, "upstreamReachId");
            downstreamReachId = slug(downstreamReachId, "downstreamReachId");
            if (upstreamReachId.equals(downstreamReachId)) {
                throw new IllegalArgumentException("fall upstream and downstream reaches must differ");
            }
            if (dropMillionths < 1_000_000L || dropMillionths > 512_000_000L) {
                throw new IllegalArgumentException("fall drop outside 1..512 blocks");
            }
        }
    }

    public record FieldBinding(
            String fieldId,
            FieldSemantic semantic,
            FieldValueType valueType,
            String ownerModuleId,
            Ownership ownership
    ) {
        public FieldBinding {
            fieldId = qualifiedId(fieldId, "fieldId");
            Objects.requireNonNull(semantic, "semantic");
            Objects.requireNonNull(valueType, "valueType");
            ownerModuleId = qualifiedId(ownerModuleId, "ownerModuleId");
            Objects.requireNonNull(ownership, "ownership");
            FieldValueType expected = semantic == FieldSemantic.FLOW_DIRECTION
                    ? FieldValueType.U8 : FieldValueType.I32;
            if (valueType != expected) throw new IllegalArgumentException("hydrology field value type mismatch");
        }
    }

    public record GraphWorkBudget(
            String budgetVersion,
            int maximumBasins,
            int maximumNodes,
            int maximumReaches,
            int maximumWaterBodies,
            int maximumFallPlans,
            int maximumFields,
            long globalCellCount,
            long estimatedCpuWorkUnits,
            long estimatedResidentBytes
    ) {
        public static final String VERSION = "hydrology-budget-v1";

        public GraphWorkBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown hydrology budget version");
            }
            if (maximumBasins < 0 || maximumBasins > MAX_BASINS
                    || maximumNodes < 0 || maximumNodes > MAX_NODES
                    || maximumReaches < 0 || maximumReaches > MAX_REACHES
                    || maximumWaterBodies < 0 || maximumWaterBodies > MAX_WATER_BODIES
                    || maximumFallPlans < 0 || maximumFallPlans > MAX_FALLS
                    || maximumFields < FieldSemantic.values().length || maximumFields > MAX_FIELDS
                    || globalCellCount < 1 || globalCellCount > 1_000_000L
                    || estimatedCpuWorkUnits < 1 || estimatedCpuWorkUnits > 100_000_000L
                    || estimatedResidentBytes < 1 || estimatedResidentBytes > 512L * 1024L * 1024L) {
                throw new IllegalArgumentException("hydrology graph/work budget is outside trusted bounds");
            }
        }
    }

    private static void validateGraph(
            List<DrainageBasin> basins,
            List<HydrologyNode> nodes,
            List<HydrologyReach> reaches,
            List<WaterBodyPlan> waterBodies,
            List<FallPlan> falls,
            List<FieldBinding> fields,
            GraphWorkBudget budget
    ) {
        if (basins.size() > budget.maximumBasins()
                || nodes.size() > budget.maximumNodes()
                || reaches.size() > budget.maximumReaches()
                || waterBodies.size() > budget.maximumWaterBodies()
                || falls.size() > budget.maximumFallPlans()
                || fields.size() > budget.maximumFields()) {
            throw new IllegalArgumentException("hydrology graph exceeds its declared budget");
        }

        Map<String, DrainageBasin> basinById = uniqueMap(basins, DrainageBasin::basinId, "basin");
        Map<String, HydrologyNode> nodeById = uniqueMap(nodes, HydrologyNode::nodeId, "node");
        Map<String, HydrologyReach> reachById = uniqueMap(reaches, HydrologyReach::reachId, "reach");
        Map<String, WaterBodyPlan> waterBodyById = uniqueMap(
                waterBodies, WaterBodyPlan::waterBodyId, "water body");
        uniqueMap(falls, FallPlan::fallId, "fall");

        Set<String> fieldIds = new HashSet<>();
        EnumSet<FieldSemantic> semantics = EnumSet.noneOf(FieldSemantic.class);
        for (FieldBinding field : fields) {
            if (!fieldIds.add(field.fieldId()) || !semantics.add(field.semantic())) {
                throw new IllegalArgumentException("conflicting hydrology field binding");
            }
        }
        if (!semantics.equals(EnumSet.allOf(FieldSemantic.class))) {
            throw new IllegalArgumentException("hydrology field contract is incomplete");
        }

        Map<String, List<String>> expectedIncoming = new HashMap<>();
        Map<String, List<String>> expectedOutgoing = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();
        for (HydrologyReach reach : reaches) {
            DrainageBasin basin = require(basinById, reach.basinId(), "reach basin");
            HydrologyNode from = require(nodeById, reach.fromNodeId(), "reach from node");
            HydrologyNode to = require(nodeById, reach.toNodeId(), "reach to node");
            WaterBodyPlan waterBody = require(waterBodyById, reach.waterBodyId(), "reach water body");
            if (!from.basinId().equals(basin.basinId())
                    || !to.basinId().equals(basin.basinId())
                    || !waterBody.basinId().equals(basin.basinId())) {
                throw new IllegalArgumentException("reach crosses an undeclared basin boundary");
            }
            expectedOutgoing.computeIfAbsent(from.nodeId(), ignored -> new ArrayList<>()).add(reach.reachId());
            expectedIncoming.computeIfAbsent(to.nodeId(), ignored -> new ArrayList<>()).add(reach.reachId());
            graph.computeIfAbsent(from.nodeId(), ignored -> new ArrayList<>()).add(to.nodeId());
        }

        for (HydrologyNode node : nodes) {
            require(basinById, node.basinId(), "node basin");
            List<String> incoming = expectedIncoming.getOrDefault(node.nodeId(), List.of()).stream().sorted().toList();
            List<String> outgoing = expectedOutgoing.getOrDefault(node.nodeId(), List.of()).stream().sorted().toList();
            if (!node.incomingReachIds().equals(incoming) || !node.outgoingReachIds().equals(outgoing)) {
                throw new IllegalArgumentException("node reach index does not match graph endpoints: " + node.nodeId());
            }
            if ((node.kind() == NodeKind.SOURCE || node.kind() == NodeKind.SPRING) && !incoming.isEmpty()) {
                throw new IllegalArgumentException("source node has an incoming reach");
            }
            if (node.kind() == NodeKind.MOUTH && !outgoing.isEmpty()) {
                throw new IllegalArgumentException("mouth node has an outgoing reach");
            }
        }

        for (WaterBodyPlan waterBody : waterBodies) {
            require(basinById, waterBody.basinId(), "water body basin");
            for (String nodeId : waterBody.nodeIds()) {
                HydrologyNode node = require(nodeById, nodeId, "water body node");
                if (!node.basinId().equals(waterBody.basinId())) {
                    throw new IllegalArgumentException("water body contains a node from another basin");
                }
            }
        }

        for (DrainageBasin basin : basins) {
            HydrologyNode outlet = require(nodeById, basin.outletNodeId(), "basin outlet");
            WaterBodyPlan waterBody = require(waterBodyById, basin.waterBodyId(), "basin water body");
            if (!outlet.basinId().equals(basin.basinId()) || !waterBody.basinId().equals(basin.basinId())) {
                throw new IllegalArgumentException("basin outlet or water body binding mismatch");
            }
            for (String sourceId : basin.sourceNodeIds()) {
                HydrologyNode source = require(nodeById, sourceId, "basin source");
                if (!source.basinId().equals(basin.basinId())
                        || (source.kind() != NodeKind.SOURCE && source.kind() != NodeKind.SPRING)) {
                    throw new IllegalArgumentException("basin source binding mismatch");
                }
            }
        }

        for (FallPlan fall : falls) {
            require(basinById, fall.basinId(), "fall basin");
            WaterBodyPlan waterBody = require(waterBodyById, fall.waterBodyId(), "fall water body");
            HydrologyNode lip = require(nodeById, fall.lipNodeId(), "fall lip");
            HydrologyNode base = require(nodeById, fall.baseNodeId(), "fall base");
            HydrologyReach upstream = require(reachById, fall.upstreamReachId(), "fall upstream reach");
            HydrologyReach downstream = require(reachById, fall.downstreamReachId(), "fall downstream reach");
            if (lip.kind() != NodeKind.WATERFALL_LIP || base.kind() != NodeKind.WATERFALL_BASE
                    || !upstream.toNodeId().equals(lip.nodeId())
                    || !downstream.fromNodeId().equals(base.nodeId())
                    || !lip.basinId().equals(fall.basinId())
                    || !base.basinId().equals(fall.basinId())
                    || !waterBody.basinId().equals(fall.basinId())
                    || !upstream.waterBodyId().equals(fall.waterBodyId())
                    || !downstream.waterBodyId().equals(fall.waterBodyId())) {
                throw new IllegalArgumentException("fall graph binding mismatch");
            }
            graph.computeIfAbsent(lip.nodeId(), ignored -> new ArrayList<>()).add(base.nodeId());
        }

        requireAcyclic(graph);
        for (DrainageBasin basin : basins) {
            for (String sourceId : basin.sourceNodeIds()) {
                if (!reachable(sourceId, basin.outletNodeId(), graph)) {
                    throw new IllegalArgumentException("basin source cannot reach its outlet");
                }
            }
        }
    }

    private static void requireAcyclic(Map<String, List<String>> graph) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : graph.keySet()) {
            if (hasCycle(node, graph, visiting, visited)) {
                throw new IllegalArgumentException("hydrology graph contains a cycle");
            }
        }
    }

    private static boolean hasCycle(
            String node,
            Map<String, List<String>> graph,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (visited.contains(node)) return false;
        if (!visiting.add(node)) return true;
        for (String next : graph.getOrDefault(node, List.of())) {
            if (hasCycle(next, graph, visiting, visited)) return true;
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private static boolean reachable(String start, String target, Map<String, List<String>> graph) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            if (current.equals(target)) return true;
            graph.getOrDefault(current, List.of()).stream().sorted().forEach(queue::addLast);
        }
        return false;
    }

    private static <T> Map<String, T> uniqueMap(
            List<T> values,
            java.util.function.Function<T, String> id,
            String kind
    ) {
        Map<String, T> result = new HashMap<>();
        for (T value : values) {
            String key = id.apply(value);
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("duplicate hydrology " + kind + " id: " + key);
            }
        }
        return result;
    }

    private static <T> T require(Map<String, T> values, String id, String kind) {
        T value = values.get(id);
        if (value == null) throw new IllegalArgumentException(kind + " references unknown id: " + id);
        return value;
    }

    private static <T> List<T> sorted(
            List<T> values,
            String field,
            int maximumSize,
            Comparator<T> comparator
    ) {
        Objects.requireNonNull(values, field);
        if (values.size() > maximumSize || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " exceeds " + maximumSize);
        }
        return values.stream().sorted(comparator).toList();
    }

    private static List<String> sortedStrings(
            List<String> values,
            String field,
            int maximumSize,
            boolean requireNonEmpty
    ) {
        List<String> sorted = sorted(values, field, maximumSize, Comparator.naturalOrder()).stream()
                .map(value -> slug(value, field + " entry"))
                .toList();
        if (requireNonEmpty && sorted.isEmpty()) throw new IllegalArgumentException(field + " must not be empty");
        if (new HashSet<>(sorted).size() != sorted.size()) throw new IllegalArgumentException(field + " has duplicates");
        return sorted;
    }

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern QUALIFIED_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private static String slug(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!SLUG.matcher(value).matches()) throw new IllegalArgumentException(field + " must be a lowercase slug");
        return value;
    }

    private static String qualifiedId(String value, String field) {
        value = nonBlank(value, field, 128);
        if (!QUALIFIED_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase qualified id");
        }
        return value;
    }

    private static String checksum(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!CHECKSUM_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256");
        }
        return value;
    }

    private static String nonBlank(String value, String field, int maximumLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maximumLength);
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
