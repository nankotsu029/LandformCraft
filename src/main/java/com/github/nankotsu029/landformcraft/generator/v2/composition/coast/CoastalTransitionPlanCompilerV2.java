package com.github.nankotsu029.landformcraft.generator.v2.composition.coast;

import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalTransitionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Compiles explicit coastal relation policies into a canonical compositor plan. */
public final class CoastalTransitionPlanCompilerV2 {
    public static final String PLAN_ID = "coastal-transition";

    public CoastalTransitionPlanV2 compile(TerrainIntentV2 intent) {
        List<TerrainIntentV2.Feature> features = intent.features().stream()
                .filter(feature -> CoastalFeaturePlanV2.isFoundationKind(feature.kind()))
                .sorted(Comparator.comparing(TerrainIntentV2.Feature::id))
                .toList();
        if (features.isEmpty()) {
            throw failure("v2.coastal-transition-empty", "coastal transition plan requires a coastal feature");
        }
        Map<String, TerrainIntentV2.Feature> byId = features.stream()
                .collect(Collectors.toUnmodifiableMap(TerrainIntentV2.Feature::id, Function.identity()));
        List<CoastalTransitionPlanV2.Contributor> contributors = new ArrayList<>();
        for (int index = 0; index < features.size(); index++) {
            TerrainIntentV2.Feature feature = features.get(index);
            contributors.add(new CoastalTransitionPlanV2.Contributor(
                    feature.id(), feature.kind(), feature.priority(), index + 1));
        }

        List<CoastalTransitionPlanV2.Interaction> interactions = new ArrayList<>();
        for (TerrainIntentV2.Relation relation : intent.relations()) {
            String first = featureId(relation.from());
            String second = featureId(relation.to());
            boolean firstCoastal = first != null && byId.containsKey(first);
            boolean secondCoastal = second != null && byId.containsKey(second);
            if (relation.transition().profile() != TerrainIntentV2.TransitionProfile.NONE
                    && (!firstCoastal || !secondCoastal)) {
                throw failure("v2.coastal-transition-noncoastal",
                        "transition relation must connect two coastal features: " + relation.id());
            }
            if (!firstCoastal || !secondCoastal) continue;

            CoastalTransitionPlanV2.InteractionProfile profile;
            int band;
            if (isBreakwaterBasinEnclosure(relation, byId.get(first), byId.get(second))) {
                if (relation.strength() != TerrainIntentV2.Strength.HARD) {
                    throw failure("v2.coastal-transition-seam-strength",
                            "breakwater-basin connection seam requires a HARD relation: " + relation.id());
                }
                profile = CoastalTransitionPlanV2.InteractionProfile.STRUCTURE_OVER_WATER;
                band = 0;
            } else if (relation.kind() == TerrainIntentV2.RelationKind.ADJACENT_TO
                    || relation.kind() == TerrainIntentV2.RelationKind.OVERLAPS) {
                if (relation.transition().profile() != TerrainIntentV2.TransitionProfile.PRIORITY_BLEND) {
                    throw failure("v2.coastal-transition-missing-policy",
                            "coastal adjacency requires an explicit transition policy: " + relation.id());
                }
                profile = CoastalTransitionPlanV2.InteractionProfile.PRIORITY_BLEND;
                band = relation.transition().bandBlocks();
            } else {
                continue;
            }
            String low = first.compareTo(second) < 0 ? first : second;
            String high = first.compareTo(second) < 0 ? second : first;
            interactions.add(new CoastalTransitionPlanV2.Interaction(
                    relation.id(), low, high, relation.strength(), profile, band));
        }
        int support = interactions.stream().mapToInt(CoastalTransitionPlanV2.Interaction::bandBlocks).max().orElse(0);
        try {
            return new CoastalTransitionPlanV2(
                    CoastalTransitionPlanV2.VERSION,
                    PLAN_ID,
                    CoastalTransitionModuleV2.MODULE_ID,
                    CoastalTransitionModuleV2.MODULE_VERSION,
                    ModuleDescriptorV2.MergeOperator.PRIORITY_BLEND,
                    CoastalTransitionPlanV2.HardCellPolicy.PROTECT_EXACT,
                    CoastalTransitionPlanV2.AmbiguityPolicy.REJECT,
                    contributors,
                    interactions,
                    CoastalTransitionModuleV2.inputFields(),
                    CoastalTransitionModuleV2.LAND_WATER_FIELD_ID,
                    CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID,
                    CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID,
                    CoastalTransitionModuleV2.BLEND_WEIGHT_FIELD_ID,
                    CoastalTransitionModuleV2.CONFLICT_FIELD_ID,
                    support);
        } catch (IllegalArgumentException exception) {
            throw failure("v2.coastal-transition-contract", exception.getMessage());
        }
    }

    private static String featureId(String endpoint) {
        return endpoint.startsWith("feature:") ? endpoint.substring("feature:".length()) : null;
    }

    private static boolean isBreakwaterBasinEnclosure(
            TerrainIntentV2.Relation relation,
            TerrainIntentV2.Feature first,
            TerrainIntentV2.Feature second
    ) {
        if (relation.kind() != TerrainIntentV2.RelationKind.ENCLOSES
                && relation.kind() != TerrainIntentV2.RelationKind.ENCLOSED_BY) return false;
        return (first.kind() == TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR
                && second.kind() == TerrainIntentV2.FeatureKind.HARBOR_BASIN)
                || (second.kind() == TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR
                && first.kind() == TerrainIntentV2.FeatureKind.HARBOR_BASIN);
    }

    private static CoastalTransitionException failure(String ruleId, String message) {
        return new CoastalTransitionException(ruleId, message);
    }
}
