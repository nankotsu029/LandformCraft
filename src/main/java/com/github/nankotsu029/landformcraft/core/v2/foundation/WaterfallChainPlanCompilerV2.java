package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.WaterfallChainPlanV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles a WATERFALL_CHAIN COMPOSITE_PRESET from a sealed general river graph (V2-9-13).
 * Does not implement a dedicated waterfall-chain world generator.
 */
public final class WaterfallChainPlanCompilerV2 {
    public static final String RULE_COUNT = "v2.waterfall-chain-count";
    public static final String RULE_ORDER = "v2.waterfall-chain-order";
    public static final String RULE_BIND = "v2.waterfall-chain-bind";
    public static final String RULE_ELEVATION = "v2.waterfall-chain-elevation";
    public static final String RULE_BUDGET = "v2.waterfall-chain-budget";

    public WaterfallChainPlanV2 compile(RiverPlanV2 river, String riverPlanChecksum, String chainId) {
        Objects.requireNonNull(river, "river");
        Objects.requireNonNull(riverPlanChecksum, "riverPlanChecksum");
        Objects.requireNonNull(chainId, "chainId");
        List<RiverPlanV2.Node> waterfallNodes = river.nodes().stream()
                .filter(node -> node.kind() == RiverPlanV2.NodeKind.WATERFALL)
                .sorted(Comparator.comparingLong(RiverPlanV2.Node::bedYMillionths).reversed()
                        .thenComparing(RiverPlanV2.Node::nodeId))
                .toList();
        if (waterfallNodes.size() < 2) {
            throw failure(RULE_COUNT, "waterfall chain requires at least two WATERFALL nodes");
        }
        if (waterfallNodes.size() > WaterfallChainPlanV2.MAXIMUM_WATERFALLS) {
            throw failure(RULE_BUDGET, "waterfall chain node budget exceeded");
        }
        Map<String, List<String>> outgoing = new HashMap<>();
        for (RiverPlanV2.Node node : river.nodes()) {
            outgoing.put(node.nodeId(), new ArrayList<>());
        }
        for (RiverPlanV2.Reach reach : river.reaches()) {
            outgoing.get(reach.fromNodeId()).add(reach.toNodeId());
        }
        List<String> ordered = orderAlongFlow(waterfallNodes, outgoing, river);
        if (ordered.size() != waterfallNodes.size()) {
            throw failure(RULE_ORDER, "waterfall nodes are not totally ordered on one flow path");
        }
        for (int index = 1; index < ordered.size(); index++) {
            RiverPlanV2.Node upstream = node(river, ordered.get(index - 1));
            RiverPlanV2.Node downstream = node(river, ordered.get(index));
            if (downstream.bedYMillionths() > upstream.bedYMillionths()) {
                throw failure(RULE_ELEVATION, "waterfall chain elevation must decrease downstream");
            }
        }
        Map<String, RiverPlanV2.ChildFeature> poolsByNode = new HashMap<>();
        for (RiverPlanV2.ChildFeature child : river.children()) {
            if (child.kind() != RiverPlanV2.ChildKind.PLUNGE_POOL) {
                continue;
            }
            if (poolsByNode.put(child.ownerNodeId(), child) != null) {
                throw failure(RULE_BIND, "duplicate plunge-pool for waterfall node");
            }
        }
        List<WaterfallChainPlanV2.PlungePoolRef> pools = new ArrayList<>();
        long totalDrop = 0L;
        for (String waterfallId : ordered) {
            RiverPlanV2.ChildFeature pool = poolsByNode.get(waterfallId);
            if (pool == null) {
                throw failure(RULE_BIND, "missing plunge-pool child for waterfall node");
            }
            pools.add(new WaterfallChainPlanV2.PlungePoolRef(
                    pool.childId(), waterfallId, pool.radiusBlocks(), Math.max(1, pool.radiusBlocks() / 2)));
            RiverPlanV2.Node waterfall = node(river, waterfallId);
            RiverPlanV2.Node downstream = downstreamNeighbor(river, waterfallId, outgoing);
            totalDrop = Math.addExact(totalDrop,
                    Math.subtractExact(waterfall.bedYMillionths(), downstream.bedYMillionths()));
        }
        if (totalDrop < 1_000_000L) {
            throw failure(RULE_ELEVATION, "waterfall chain total drop below one block");
        }
        int support = Math.max(2, river.supportRadiusXZ());
        long work = Math.multiplyExact((long) ordered.size(), 1_000L);
        if (work > WaterfallChainPlanV2.MAXIMUM_GRAPH_WORK_UNITS) {
            throw failure(RULE_BUDGET, "waterfall chain work budget exceeded");
        }
        return new WaterfallChainPlanV2(
                WaterfallChainPlanV2.VERSION,
                chainId,
                WaterfallChainPlanV2.CONTRACT_VERSION,
                river.featureId(),
                riverPlanChecksum,
                ordered,
                pools,
                totalDrop,
                ordered.size(),
                support,
                work,
                river.geometryChecksum(),
                "0".repeat(64));
    }

    private static List<String> orderAlongFlow(
            List<RiverPlanV2.Node> waterfallNodes,
            Map<String, List<String>> outgoing,
            RiverPlanV2 river
    ) {
        Set<String> waterfallIds = new HashSet<>();
        for (RiverPlanV2.Node node : waterfallNodes) {
            waterfallIds.add(node.nodeId());
        }
        RiverPlanV2.Node source = river.nodes().stream()
                .filter(node -> node.kind() == RiverPlanV2.NodeKind.SOURCE)
                .min(Comparator.comparing(RiverPlanV2.Node::nodeId))
                .orElseThrow();
        List<String> discovered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(source.nodeId());
        seen.add(source.nodeId());
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (waterfallIds.contains(current)) {
                discovered.add(current);
            }
            for (String next : outgoing.getOrDefault(current, List.of()).stream().sorted().toList()) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return discovered;
    }

    private static RiverPlanV2.Node node(RiverPlanV2 river, String nodeId) {
        return river.nodes().stream()
                .filter(node -> node.nodeId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> failure(RULE_BIND, "unknown waterfall node"));
    }

    private static RiverPlanV2.Node downstreamNeighbor(
            RiverPlanV2 river,
            String waterfallId,
            Map<String, List<String>> outgoing
    ) {
        List<String> next = outgoing.getOrDefault(waterfallId, List.of());
        if (next.size() != 1) {
            throw failure(RULE_ORDER, "WATERFALL must have exactly one downstream edge");
        }
        return node(river, next.getFirst());
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
