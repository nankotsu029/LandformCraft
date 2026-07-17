package com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TidalChannelPlanV2;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Independent EXPERIMENTAL graph-integrity hook for V2-3-08 corruption fixtures. */
public final class TidalGraphValidatorV2 {
    private TidalGraphValidatorV2() {
    }

    public static void requireValid(
            TidalChannelPlanV2 plan,
            List<TidalChannelPlanV2.ChannelEdge> edges
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(edges, "edges");
        Set<String> edgeIds = new HashSet<>();
        Set<String> connected = new HashSet<>();
        boolean marine = false;
        for (TidalChannelPlanV2.ChannelEdge edge : edges) {
            if (!edgeIds.add(edge.edgeId())) {
                throw failure("v2.tidal-unknown-edge", "duplicate tidal edge id");
            }
            if (edge.edgeKind() != TerrainIntentV2.TidalEdgeKind.BIDIRECTIONAL
                    || edge.edgeKind() != plan.edgeKind()) {
                throw failure("v2.tidal-ambiguous-direction", "tidal edge kind is not bidirectional");
            }
            if (!plan.nodes().stream().anyMatch(node -> node.nodeId().equals(edge.fromNodeId()))
                    || !plan.nodes().stream().anyMatch(node -> node.nodeId().equals(edge.toNodeId()))) {
                throw failure("v2.tidal-isolated-component", "tidal edge references an unknown node");
            }
            boolean fromMarine = plan.nodes().stream()
                    .filter(node -> node.nodeId().equals(edge.fromNodeId()))
                    .findFirst()
                    .orElseThrow()
                    .marine();
            boolean toMarine = plan.nodes().stream()
                    .filter(node -> node.nodeId().equals(edge.toNodeId()))
                    .findFirst()
                    .orElseThrow()
                    .marine();
            if (fromMarine || toMarine) marine = true;
            if ((fromMarine && !onBoundary(edge.path().getFirst(), plan))
                    || (toMarine && !onBoundary(edge.path().getLast(), plan))) {
                throw failure("v2.tidal-closed-channel", "marine tidal endpoint is not on the receiving sea");
            }
            connected.add(edge.fromNodeId());
            connected.add(edge.toNodeId());
        }
        if (!marine) {
            throw failure("v2.tidal-closed-channel", "tidal network has no open-sea connection");
        }
        if (connected.size() != plan.nodes().size() || edges.size() != plan.edges().size()) {
            throw failure("v2.tidal-isolated-component", "tidal network contains an isolated component");
        }
        for (TidalChannelPlanV2.ChannelNode node : plan.nodes()) {
            if (node.marine() != TidalChannelPlanV2.onBoundary(
                    node.point(), plan.receivingSeaBoundary(), plan.width(), plan.length())) {
                throw failure("v2.tidal-ambiguous-direction",
                        "tidal marine flag conflicts with receiving sea boundary");
            }
        }
    }

    private static boolean onBoundary(TidalChannelPlanV2.ChannelPoint point, TidalChannelPlanV2 plan) {
        return TidalChannelPlanV2.onBoundary(
                point, plan.receivingSeaBoundary(), plan.width(), plan.length());
    }

    private static TidalGenerationException failure(String ruleId, String message) {
        return new TidalGenerationException(ruleId, message);
    }
}
