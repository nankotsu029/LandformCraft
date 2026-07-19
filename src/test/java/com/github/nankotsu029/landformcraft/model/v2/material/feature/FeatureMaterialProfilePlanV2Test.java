package com.github.nankotsu029.landformcraft.model.v2.material.feature;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureMaterialProfilePlanV2Test {
    private static final String ZERO = "0".repeat(64);

    private FeatureMaterialProfilePlanV2.Catalog catalog() {
        List<FeatureMaterialProfilePlanV2.Entry> entries = java.util.Arrays
                .stream(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.values())
                .map(kind -> new FeatureMaterialProfilePlanV2.Entry(
                        kind, kind.classId(), kind.compactCode(), kind.featureKind()))
                .toList();
        return new FeatureMaterialProfilePlanV2.Catalog(
                1, "landformcraft.builtin-feature-material-profile",
                "builtin-feature-material-profile-catalog-v1",
                entries,
                new FeatureMaterialProfilePlanV2.CatalogBudget(
                        "feature-material-catalog-budget-v1", 6, 16L * 1024L));
    }

    private FeatureMaterialProfilePlanV2.ResourceBudget budget(long cells) {
        return new FeatureMaterialProfilePlanV2.ResourceBudget(
                "feature-material-budget-v1", cells, 4, 2, 0, 0,
                cells * 4, 4_096L, 64, 16_384L, 48_000L);
    }

    private FeatureMaterialProfilePlanV2 build(
            List<FeatureMaterialProfilePlanV2.ResolutionRule> rules,
            List<FeatureMaterialProfilePlanV2.ConflictRule> conflicts
    ) {
        return new FeatureMaterialProfilePlanV2(
                FeatureMaterialProfilePlanV2.VERSION,
                FeatureMaterialProfilePlanV2.PROFILE_CONTRACT_VERSION,
                new FeatureMaterialProfilePlanV2.MaterialProfileBinding(
                        1, ZERO, "feature-material-material-profile-binding-v1"),
                new FeatureMaterialProfilePlanV2.GeologyBinding(
                        1, ZERO, ZERO, ZERO, "feature-material-geology-binding-v1"),
                List.of(),
                List.of(),
                catalog(),
                rules,
                conflicts,
                FeatureMaterialProfilePlanV2.Kernel.standard(),
                budget(1_000L),
                ZERO);
    }

    @Test
    void acceptsFixedRuleTables() {
        assertDoesNotThrow(() -> build(
                FeatureMaterialProfilePlanV2.ResolutionRule.standardOrder(),
                FeatureMaterialProfilePlanV2.ConflictRule.standardOrder()));
    }

    @Test
    void rejectsReorderedResolutionRules() {
        List<FeatureMaterialProfilePlanV2.ResolutionRule> reordered = List.of(
                new FeatureMaterialProfilePlanV2.ResolutionRule(
                        0, FeatureMaterialProfilePlanV2.ResolutionRuleId.VOLCANIC_ZONE_OVERRIDE,
                        FeatureMaterialProfilePlanV2.RuleMergeOperator.CONDITIONAL_OVERRIDE,
                        List.of(FeatureMaterialProfilePlanV2.FeatureKind.VOLCANIC)),
                new FeatureMaterialProfilePlanV2.ResolutionRule(
                        1, FeatureMaterialProfilePlanV2.ResolutionRuleId.BASE_FROM_MATERIAL_PROFILE,
                        FeatureMaterialProfilePlanV2.RuleMergeOperator.BASE_ASSIGNMENT,
                        List.of()),
                FeatureMaterialProfilePlanV2.ResolutionRule.standardOrder().get(2),
                FeatureMaterialProfilePlanV2.ResolutionRule.standardOrder().get(3));
        assertThrows(IllegalArgumentException.class, () -> build(
                reordered, FeatureMaterialProfilePlanV2.ConflictRule.standardOrder()));
    }

    @Test
    void rejectsCatalogTamperingAndUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> new FeatureMaterialProfilePlanV2.Entry(
                FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_BASALT_EXPOSED,
                "material.feature.arbitrary", 7,
                FeatureMaterialProfilePlanV2.FeatureKind.VOLCANIC));
        FeatureMaterialProfilePlanV2 plan = build(
                FeatureMaterialProfilePlanV2.ResolutionRule.standardOrder(),
                FeatureMaterialProfilePlanV2.ConflictRule.standardOrder());
        assertThrows(IllegalArgumentException.class, () -> plan.catalog().requireByCode(99));
    }

    @Test
    void rejectsInsufficientCpuBudget() {
        assertThrows(IllegalArgumentException.class, () -> new FeatureMaterialProfilePlanV2(
                FeatureMaterialProfilePlanV2.VERSION,
                FeatureMaterialProfilePlanV2.PROFILE_CONTRACT_VERSION,
                new FeatureMaterialProfilePlanV2.MaterialProfileBinding(
                        1, ZERO, "feature-material-material-profile-binding-v1"),
                new FeatureMaterialProfilePlanV2.GeologyBinding(
                        1, ZERO, ZERO, ZERO, "feature-material-geology-binding-v1"),
                List.of(),
                List.of(),
                catalog(),
                FeatureMaterialProfilePlanV2.ResolutionRule.standardOrder(),
                FeatureMaterialProfilePlanV2.ConflictRule.standardOrder(),
                FeatureMaterialProfilePlanV2.Kernel.standard(),
                new FeatureMaterialProfilePlanV2.ResourceBudget(
                        "feature-material-budget-v1", 1_000L, 4, 2, 0, 0,
                        1L, 4_096L, 64, 16_384L, 48_000L),
                ZERO));
    }
}
