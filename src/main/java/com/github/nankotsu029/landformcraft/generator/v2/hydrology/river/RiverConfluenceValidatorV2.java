package com.github.nankotsu029.landformcraft.generator.v2.hydrology.river;

import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * EXPERIMENTAL confluence discharge hook for multiple compiled river reaches.
 * It does not invent new graph topology beyond declared HARD relations.
 */
public final class RiverConfluenceValidatorV2 {
    private RiverConfluenceValidatorV2() {
    }

    public static void requireConfluenceDischargeConsistent(
            TerrainIntentV2 intent,
            List<MeanderingRiverPlanV2> riverPlans
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(riverPlans, "riverPlans");
        Map<String, MeanderingRiverPlanV2> byId = new HashMap<>();
        for (MeanderingRiverPlanV2 plan : riverPlans) {
            if (byId.put(plan.featureId(), plan) != null) {
                throw new RiverGenerationException("v2.river-confluence", "duplicate river plan feature id");
            }
        }
        Map<String, List<String>> upstreamOf = new HashMap<>();
        for (TerrainIntentV2.Relation relation : intent.relations()) {
            if (relation.strength() != TerrainIntentV2.Strength.HARD) continue;
            if (relation.kind() != TerrainIntentV2.RelationKind.UPSTREAM_OF
                    && relation.kind() != TerrainIntentV2.RelationKind.DRAINS_TO) {
                continue;
            }
            if (!relation.from().startsWith("feature:") || !relation.to().startsWith("feature:")) continue;
            String from = relation.from().substring("feature:".length());
            String to = relation.to().substring("feature:".length());
            if (!byId.containsKey(from) || !byId.containsKey(to)) continue;
            upstreamOf.computeIfAbsent(to, ignored -> new ArrayList<>()).add(from);
        }
        for (Map.Entry<String, List<String>> entry : upstreamOf.entrySet()) {
            MeanderingRiverPlanV2 downstream = byId.get(entry.getKey());
            List<String> upstreamIds = entry.getValue().stream().sorted().toList();
            if (upstreamIds.size() < 2) continue;
            int upstreamSum = 0;
            Set<String> seen = new HashSet<>();
            for (String upstreamId : upstreamIds) {
                if (!seen.add(upstreamId)) {
                    throw new RiverGenerationException("v2.river-confluence", "duplicate upstream edge");
                }
                upstreamSum = Math.addExact(upstreamSum, byId.get(upstreamId).selectedDischargeIndex());
            }
            if (downstream.selectedDischargeIndex() < upstreamIds.stream()
                    .map(byId::get)
                    .max(Comparator.comparingInt(MeanderingRiverPlanV2::selectedDischargeIndex))
                    .orElseThrow()
                    .selectedDischargeIndex()) {
                throw new RiverGenerationException(
                        "v2.river-confluence-flow",
                        "downstream discharge is smaller than the largest upstream reach");
            }
            if (downstream.selectedDischargeIndex() > upstreamSum) {
                throw new RiverGenerationException(
                        "v2.river-confluence-flow",
                        "downstream discharge exceeds the sum of upstream reaches");
            }
        }
    }
}
