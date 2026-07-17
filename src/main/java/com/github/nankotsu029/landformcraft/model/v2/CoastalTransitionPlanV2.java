package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Frozen ownership and merge contract for interactions between V2 coastal feature rasters. */
public record CoastalTransitionPlanV2(
        int planVersion,
        String planId,
        String moduleId,
        String moduleVersion,
        ModuleDescriptorV2.MergeOperator mergeOperator,
        HardCellPolicy hardCellPolicy,
        AmbiguityPolicy ambiguityPolicy,
        List<Contributor> contributors,
        List<Interaction> interactions,
        List<String> inputFieldIds,
        String landWaterFieldId,
        String surfaceHeightFieldId,
        String ownerIndexFieldId,
        String blendWeightFieldId,
        String conflictFieldId,
        int supportRadiusXZ
) {
    public static final int VERSION = 1;

    public CoastalTransitionPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("coastal transition planVersion must be 1");
        planId = V2Validation.slug(planId, "planId");
        moduleId = V2Validation.qualifiedId(moduleId, "moduleId");
        moduleVersion = V2Validation.nonBlank(moduleVersion, "moduleVersion", 64);
        if (mergeOperator != ModuleDescriptorV2.MergeOperator.PRIORITY_BLEND) {
            throw new IllegalArgumentException("coastal transition mergeOperator must be PRIORITY_BLEND");
        }
        Objects.requireNonNull(hardCellPolicy, "hardCellPolicy");
        Objects.requireNonNull(ambiguityPolicy, "ambiguityPolicy");
        contributors = V2Validation.sorted(
                contributors, "contributors", 64, Comparator.comparing(Contributor::featureId));
        interactions = V2Validation.sorted(
                interactions, "interactions", 128,
                Comparator.comparing(Interaction::firstFeatureId)
                        .thenComparing(Interaction::secondFeatureId)
                        .thenComparing(Interaction::relationId));
        inputFieldIds = V2Validation.sorted(inputFieldIds, "inputFieldIds", 64, Comparator.naturalOrder())
                .stream().map(value -> V2Validation.qualifiedId(value, "inputFieldId")).toList();
        landWaterFieldId = V2Validation.qualifiedId(landWaterFieldId, "landWaterFieldId");
        surfaceHeightFieldId = V2Validation.qualifiedId(surfaceHeightFieldId, "surfaceHeightFieldId");
        ownerIndexFieldId = V2Validation.qualifiedId(ownerIndexFieldId, "ownerIndexFieldId");
        blendWeightFieldId = V2Validation.qualifiedId(blendWeightFieldId, "blendWeightFieldId");
        conflictFieldId = V2Validation.qualifiedId(conflictFieldId, "conflictFieldId");
        if (supportRadiusXZ < 0 || supportRadiusXZ > 32) {
            throw new IllegalArgumentException("coastal transition supportRadiusXZ outside 0..32");
        }
        validate(contributors, interactions, supportRadiusXZ);
    }

    public enum HardCellPolicy { PROTECT_EXACT }
    public enum AmbiguityPolicy { REJECT }
    public enum InteractionProfile { PRIORITY_BLEND, STRUCTURE_OVER_WATER }

    public record Contributor(
            String featureId,
            TerrainIntentV2.FeatureKind kind,
            int priority,
            int ownerIndex
    ) {
        public Contributor {
            featureId = V2Validation.slug(featureId, "featureId");
            Objects.requireNonNull(kind, "kind");
            if (!CoastalFeaturePlanV2.isFoundationKind(kind)) {
                throw new IllegalArgumentException("transition contributor is not a coastal feature: " + featureId);
            }
            if (priority < -100 || priority > 100) throw new IllegalArgumentException("priority outside -100..100");
            if (ownerIndex < 1 || ownerIndex > 65_535) throw new IllegalArgumentException("ownerIndex outside 1..65535");
        }
    }

    public record Interaction(
            String relationId,
            String firstFeatureId,
            String secondFeatureId,
            TerrainIntentV2.Strength strength,
            InteractionProfile profile,
            int bandBlocks
    ) {
        public Interaction {
            relationId = V2Validation.slug(relationId, "relationId");
            firstFeatureId = V2Validation.slug(firstFeatureId, "firstFeatureId");
            secondFeatureId = V2Validation.slug(secondFeatureId, "secondFeatureId");
            Objects.requireNonNull(strength, "strength");
            Objects.requireNonNull(profile, "profile");
            if (firstFeatureId.compareTo(secondFeatureId) >= 0) {
                throw new IllegalArgumentException("interaction feature IDs must be canonical and distinct");
            }
            if ((profile == InteractionProfile.PRIORITY_BLEND && (bandBlocks < 1 || bandBlocks > 32))
                    || (profile == InteractionProfile.STRUCTURE_OVER_WATER && bandBlocks != 0)) {
                throw new IllegalArgumentException("interaction band does not match profile");
            }
        }

        public boolean connects(String first, String second) {
            String low = first.compareTo(second) < 0 ? first : second;
            String high = first.compareTo(second) < 0 ? second : first;
            return firstFeatureId.equals(low) && secondFeatureId.equals(high);
        }
    }

    private static void validate(
            List<Contributor> contributors,
            List<Interaction> interactions,
            int supportRadiusXZ
    ) {
        if (contributors.isEmpty()) throw new IllegalArgumentException("coastal transition requires a contributor");
        Map<String, Contributor> byId = new HashMap<>();
        Set<Integer> ownerIndexes = new HashSet<>();
        for (Contributor contributor : contributors) {
            if (byId.putIfAbsent(contributor.featureId(), contributor) != null) {
                throw new IllegalArgumentException("duplicate transition contributor: " + contributor.featureId());
            }
            if (!ownerIndexes.add(contributor.ownerIndex())) {
                throw new IllegalArgumentException("duplicate transition ownerIndex: " + contributor.ownerIndex());
            }
        }
        Set<String> pairs = new HashSet<>();
        int maximumBand = 0;
        for (Interaction interaction : interactions) {
            Contributor first = byId.get(interaction.firstFeatureId());
            Contributor second = byId.get(interaction.secondFeatureId());
            if (first == null || second == null) {
                throw new IllegalArgumentException("interaction references unknown contributor: " + interaction.relationId());
            }
            String pair = interaction.firstFeatureId() + '\n' + interaction.secondFeatureId();
            if (!pairs.add(pair)) throw new IllegalArgumentException("multiple transition rules for pair: " + pair);
            if (interaction.profile() == InteractionProfile.STRUCTURE_OVER_WATER
                    && !isBreakwaterBasin(first.kind(), second.kind())) {
                throw new IllegalArgumentException("STRUCTURE_OVER_WATER requires breakwater and harbor basin");
            }
            maximumBand = Math.max(maximumBand, interaction.bandBlocks());
        }
        if (supportRadiusXZ != maximumBand) {
            throw new IllegalArgumentException("transition supportRadiusXZ must equal maximum interaction band");
        }
    }

    private static boolean isBreakwaterBasin(
            TerrainIntentV2.FeatureKind first,
            TerrainIntentV2.FeatureKind second
    ) {
        return (first == TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR
                && second == TerrainIntentV2.FeatureKind.HARBOR_BASIN)
                || (second == TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR
                && first == TerrainIntentV2.FeatureKind.HARBOR_BASIN);
    }
}
