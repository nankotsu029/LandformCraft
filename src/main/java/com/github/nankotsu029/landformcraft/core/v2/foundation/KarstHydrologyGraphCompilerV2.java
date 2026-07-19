package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstHydrologyGraphPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstSpringPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SinkholePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Compiles the typed karst hydrology graph from sealed sinkhole, spring, and host cave plans. */
public final class KarstHydrologyGraphCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private static final long LEAK_MARGIN = TerrainIntentV2.FIXED_SCALE;

    public KarstHydrologyGraphPlanV2 compile(
            TerrainIntentV2 intent,
            SinkholePlanV2 sinkhole,
            KarstSpringPlanV2 spring,
            CaveNetworkPlanV2 hostCave,
            String undergroundRiverFeatureId,
            String undergroundRiverPlanChecksum
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(sinkhole, "sinkhole");
        Objects.requireNonNull(spring, "spring");
        Objects.requireNonNull(hostCave, "hostCave");
        if (undergroundRiverFeatureId == null) {
            undergroundRiverFeatureId = "";
        }
        if (undergroundRiverPlanChecksum == null) {
            undergroundRiverPlanChecksum = "";
        }

        if (sinkhole.lossVolumeBlocks() != spring.springDischargeBlocks()) {
            throw failure("v2.karst-loss-spring-imbalance",
                    "sinkhole lossVolumeBlocks must equal spring springDischargeBlocks");
        }

        String sinkNodeId = "node.sinkhole." + sinkhole.featureId();
        String caveNodeId = "node.cave." + hostCave.featureId();
        String springNodeId = "node.spring." + spring.featureId();
        String riverNodeId = undergroundRiverFeatureId.isBlank()
                ? ""
                : "node.river." + undergroundRiverFeatureId;

        List<KarstHydrologyGraphPlanV2.Node> nodes = new ArrayList<>();
        nodes.add(new KarstHydrologyGraphPlanV2.Node(
                sinkNodeId, KarstHydrologyGraphPlanV2.NodeRole.SINKHOLE,
                sinkhole.featureId(), sinkhole.canonicalChecksum()));
        nodes.add(new KarstHydrologyGraphPlanV2.Node(
                caveNodeId, KarstHydrologyGraphPlanV2.NodeRole.KARST_CAVE_SYSTEM,
                hostCave.featureId(), hostCave.canonicalChecksum()));
        if (!undergroundRiverFeatureId.isBlank()) {
            nodes.add(new KarstHydrologyGraphPlanV2.Node(
                    riverNodeId, KarstHydrologyGraphPlanV2.NodeRole.UNDERGROUND_RIVER,
                    undergroundRiverFeatureId, undergroundRiverPlanChecksum));
        }
        nodes.add(new KarstHydrologyGraphPlanV2.Node(
                springNodeId, KarstHydrologyGraphPlanV2.NodeRole.KARST_SPRING,
                spring.featureId(), spring.canonicalChecksum()));
        nodes.sort(Comparator.comparing(KarstHydrologyGraphPlanV2.Node::nodeId));

        List<KarstHydrologyGraphPlanV2.Edge> edges = buildEdges(
                intent, sinkhole, spring, sinkNodeId, caveNodeId, springNodeId, riverNodeId);
        detectCycle(edges);

        KarstHydrologyGraphPlanV2 draft = new KarstHydrologyGraphPlanV2(
                KarstHydrologyGraphPlanV2.VERSION,
                "karst-graph." + sinkhole.featureId(),
                KarstHydrologyGraphPlanV2.CONTRACT,
                nodes,
                edges,
                sinkhole.featureId(),
                spring.featureId(),
                hostCave.featureId(),
                hostCave.canonicalChecksum(),
                undergroundRiverFeatureId,
                undergroundRiverPlanChecksum,
                sinkhole.canonicalChecksum(),
                spring.canonicalChecksum(),
                sinkhole.lossVolumeBlocks(),
                spring.springDischargeBlocks(),
                SinkholePlanV2.MATERIAL_HANDOFF_ID,
                Math.max(sinkhole.supportRadiusXZ(), spring.supportRadiusXZ()),
                Math.addExact(sinkhole.estimatedWorkUnits(), spring.estimatedWorkUnits()),
                bindGeometryChecksum(sinkhole.geometryChecksum(), spring.geometryChecksum()),
                ZERO);

        if (!draft.reachableFromSinkholeToSpring()) {
            throw failure("v2.karst-unreachable", "karst graph lacks drainage path from sinkhole to spring");
        }
        validateNoLeak(hostCave, sinkhole, spring);

        return draft;
    }

    private static List<KarstHydrologyGraphPlanV2.Edge> buildEdges(
            TerrainIntentV2 intent,
            SinkholePlanV2 sinkhole,
            KarstSpringPlanV2 spring,
            String sinkNodeId,
            String caveNodeId,
            String springNodeId,
            String riverNodeId
    ) {
        List<KarstHydrologyGraphPlanV2.Edge> edges = new ArrayList<>();
        edges.add(new KarstHydrologyGraphPlanV2.Edge(
                "edge.sinkhole-drains-to-cave", sinkNodeId, caveNodeId, "DRAINS_TO"));
        if (!riverNodeId.isBlank()) {
            edges.add(new KarstHydrologyGraphPlanV2.Edge(
                    "edge.cave-upstream-of-river", caveNodeId, riverNodeId, "UPSTREAM_OF"));
            edges.add(new KarstHydrologyGraphPlanV2.Edge(
                    "edge.river-upstream-of-spring", riverNodeId, springNodeId, "UPSTREAM_OF"));
        } else {
            edges.add(new KarstHydrologyGraphPlanV2.Edge(
                    "edge.cave-upstream-of-spring", caveNodeId, springNodeId, "UPSTREAM_OF"));
        }

        for (TerrainIntentV2.Relation relation : intent.relations()) {
            if (relation.strength() != TerrainIntentV2.Strength.HARD) {
                continue;
            }
            if (relation.kind() == TerrainIntentV2.RelationKind.UPSTREAM_OF
                    && relation.from().equals("feature:" + sinkhole.featureId())
                    && relation.to().equals("feature:" + spring.featureId())) {
                edges.add(new KarstHydrologyGraphPlanV2.Edge(
                        "edge.sinkhole-upstream-of-spring", sinkNodeId, springNodeId, "UPSTREAM_OF"));
            }
            if (relation.kind() == TerrainIntentV2.RelationKind.UPSTREAM_OF
                    && relation.from().equals("feature:" + spring.featureId())
                    && relation.to().equals("feature:" + sinkhole.featureId())) {
                edges.add(new KarstHydrologyGraphPlanV2.Edge(
                        "edge.spring-upstream-of-sinkhole", springNodeId, sinkNodeId, "UPSTREAM_OF"));
            }
            if (relation.kind() == TerrainIntentV2.RelationKind.ENTRANCE_OF
                    && relation.from().equals("feature:" + sinkhole.featureId())) {
                edges.add(new KarstHydrologyGraphPlanV2.Edge(
                        "edge.sinkhole-entrance-of-cave", sinkNodeId, caveNodeId, "ENTRANCE_OF"));
            }
        }
        edges.sort(Comparator.comparing(KarstHydrologyGraphPlanV2.Edge::edgeId));
        return List.copyOf(edges);
    }

    private static void detectCycle(List<KarstHydrologyGraphPlanV2.Edge> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();
        Set<String> nodeIds = new HashSet<>();
        for (KarstHydrologyGraphPlanV2.Edge edge : edges) {
            nodeIds.add(edge.fromNodeId());
            nodeIds.add(edge.toNodeId());
            adjacency.computeIfAbsent(edge.fromNodeId(), key -> new ArrayList<>()).add(edge.toNodeId());
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String nodeId : nodeIds) {
            if (!visited.contains(nodeId) && dfsCycle(nodeId, adjacency, visiting, visited)) {
                throw failure("v2.karst-graph-cycle", "karst hydrology graph contains a cycle");
            }
        }
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

    private static void validateNoLeak(
            CaveNetworkPlanV2 hostCave,
            SinkholePlanV2 sinkhole,
            KarstSpringPlanV2 spring
    ) {
        VolumeSdfAabbV2 expanded = hostCave.aabb().expand(LEAK_MARGIN);
        if (!contains(expanded, sinkhole.openingXMillionths(), sinkhole.openingYMillionths(), sinkhole.openingZMillionths())
                || !contains(expanded, spring.openingXMillionths(), spring.openingYMillionths(), spring.openingZMillionths())) {
            throw failure("v2.karst-leak",
                    "sinkhole or spring opening lies outside expanded host cave AABB");
        }
    }

    private static boolean contains(VolumeSdfAabbV2 aabb, long x, long y, long z) {
        return x >= aabb.minXMillionths() && x <= aabb.maxXMillionths()
                && y >= aabb.minYMillionths() && y <= aabb.maxYMillionths()
                && z >= aabb.minZMillionths() && z <= aabb.maxZMillionths();
    }

    private static String bindGeometryChecksum(String left, String right) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((left + "|" + right).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
