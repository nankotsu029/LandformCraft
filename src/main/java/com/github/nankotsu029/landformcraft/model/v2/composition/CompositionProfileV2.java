package com.github.nankotsu029.landformcraft.model.v2.composition;

import java.util.Objects;
import java.util.Set;

/**
 * ADR 0038 D3 composition profile: the versioned contract that says, separately from the catalog
 * role ({@code FeaturePrimaryRoleV2}), whether a feature kind can be a foundation candidate and
 * which composition stages its compiled plan contributes to. A single-value role enum was rejected
 * by the ADR because kinds like {@code OVERHANG} (add + carve) or {@code WATERFALL}
 * (surface + volume + fluid) contribute to several stages at once.
 */
public record CompositionProfileV2(
        boolean foundationEligible,
        Set<CompositionStageV2> stages,
        ParentPolicyV2 parentPolicy
) {
    public static final String CONTRACT_VERSION = "composition-profile-v1";

    public CompositionProfileV2 {
        Objects.requireNonNull(stages, "stages");
        Objects.requireNonNull(parentPolicy, "parentPolicy");
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("a composition profile must contribute to at least one stage");
        }
        if (foundationEligible && !stages.contains(CompositionStageV2.FOUNDATION)) {
            throw new IllegalArgumentException(
                    "a foundation-eligible profile must contribute to the FOUNDATION stage");
        }
        if (!foundationEligible && stages.contains(CompositionStageV2.FOUNDATION)) {
            throw new IllegalArgumentException(
                    "only foundation-eligible profiles may contribute to the FOUNDATION stage");
        }
        stages = Set.copyOf(stages);
    }
}
