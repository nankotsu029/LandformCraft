package com.github.nankotsu029.landformcraft.model.v2.material;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialProfilePlanV2Test {
    private static final String ZERO_CHECKSUM = "0".repeat(64);

    private MaterialProfilePlanV2.GeologyBinding standardGeologyBinding() {
        return new MaterialProfilePlanV2.GeologyBinding(
                1, ZERO_CHECKSUM, ZERO_CHECKSUM, ZERO_CHECKSUM,
                "material-profile-geology-binding-v1");
    }

    private MaterialProfilePlanV2.WaterConditionBinding standardWaterConditionBinding() {
        return new MaterialProfilePlanV2.WaterConditionBinding(
                1, ZERO_CHECKSUM, "environment.water.wetness",
                "material-profile-water-condition-binding-v1");
    }

    private MaterialProfilePlanV2.SnowBinding standardSnowBinding() {
        return new MaterialProfilePlanV2.SnowBinding(
                1, ZERO_CHECKSUM, "environment.snow.cover",
                "material-profile-snow-binding-v1");
    }

    private MaterialProfilePlanV2.Catalog standardCatalog() {
        List<MaterialProfilePlanV2.Entry> entries = java.util.Arrays
                .stream(MaterialProfilePlanV2.SemanticMaterialClass.values())
                .map(kind -> new MaterialProfilePlanV2.Entry(
                        kind, kind.classId(), kind.compactCode(), kind.substrate(),
                        kind.wetVariant(), kind.snowVariant()))
                .toList();
        return new MaterialProfilePlanV2.Catalog(
                1, "landformcraft.builtin-material-profile", "builtin-material-profile-catalog-v1",
                entries,
                new MaterialProfilePlanV2.CatalogBudget(
                        "material-profile-catalog-budget-v1", 6, 16L * 1024L));
    }

    private MaterialProfilePlanV2.ResourceBudget standardBudget(long cells) {
        return new MaterialProfilePlanV2.ResourceBudget(
                "material-profile-budget-v1", cells, 3, cells * 3, 4_096L, 256, 262_144L, 32_768L);
    }

    private MaterialProfilePlanV2 build(
            List<MaterialProfilePlanV2.ResolutionRule> rules,
            MaterialProfilePlanV2.ResourceBudget budget
    ) {
        return new MaterialProfilePlanV2(
                MaterialProfilePlanV2.VERSION,
                MaterialProfilePlanV2.PROFILE_CONTRACT_VERSION,
                standardGeologyBinding(),
                standardWaterConditionBinding(),
                standardSnowBinding(),
                standardCatalog(),
                rules,
                MaterialProfilePlanV2.Kernel.standard(),
                budget,
                ZERO_CHECKSUM);
    }

    @Test
    void acceptsTheFixedStandardOrder() {
        assertDoesNotThrow(() -> build(
                MaterialProfilePlanV2.ResolutionRule.standardOrder(), standardBudget(1_000L)));
    }

    @Test
    void rejectsReorderedRulePrecedence() {
        List<MaterialProfilePlanV2.ResolutionRule> reordered = List.of(
                new MaterialProfilePlanV2.ResolutionRule(
                        0, MaterialProfilePlanV2.ResolutionRuleId.WETNESS_OVERRIDE,
                        MaterialProfilePlanV2.RuleMergeOperator.CONDITIONAL_OVERRIDE,
                        List.of(MaterialProfilePlanV2.SurfaceAspect.SURFACE)),
                new MaterialProfilePlanV2.ResolutionRule(
                        1, MaterialProfilePlanV2.ResolutionRuleId.BASE_SUBSTRATE_FROM_LITHOLOGY,
                        MaterialProfilePlanV2.RuleMergeOperator.BASE_ASSIGNMENT,
                        List.of(MaterialProfilePlanV2.SurfaceAspect.SURFACE)),
                new MaterialProfilePlanV2.ResolutionRule(
                        2, MaterialProfilePlanV2.ResolutionRuleId.SNOW_OVERRIDE,
                        MaterialProfilePlanV2.RuleMergeOperator.CONDITIONAL_OVERRIDE,
                        List.of(MaterialProfilePlanV2.SurfaceAspect.SURFACE)));
        assertThrows(IllegalArgumentException.class, () -> build(reordered, standardBudget(1_000L)));
    }

    @Test
    void rejectsImplicitLastWriteWinsMergeOperator() {
        List<MaterialProfilePlanV2.ResolutionRule> tampered = List.of(
                new MaterialProfilePlanV2.ResolutionRule(
                        0, MaterialProfilePlanV2.ResolutionRuleId.BASE_SUBSTRATE_FROM_LITHOLOGY,
                        MaterialProfilePlanV2.RuleMergeOperator.CONDITIONAL_OVERRIDE,
                        List.of(MaterialProfilePlanV2.SurfaceAspect.CEILING,
                                MaterialProfilePlanV2.SurfaceAspect.FLOOR,
                                MaterialProfilePlanV2.SurfaceAspect.SURFACE)),
                MaterialProfilePlanV2.ResolutionRule.standardOrder().get(1),
                MaterialProfilePlanV2.ResolutionRule.standardOrder().get(2));
        assertThrows(IllegalArgumentException.class, () -> build(tampered, standardBudget(1_000L)));
    }

    @Test
    void rejectsDuplicateRuleOrder() {
        List<MaterialProfilePlanV2.ResolutionRule> duplicated = List.of(
                MaterialProfilePlanV2.ResolutionRule.standardOrder().get(0),
                MaterialProfilePlanV2.ResolutionRule.standardOrder().get(0),
                MaterialProfilePlanV2.ResolutionRule.standardOrder().get(2));
        assertThrows(IllegalArgumentException.class, () -> build(duplicated, standardBudget(1_000L)));
    }

    @Test
    void rejectsUnknownAndOutOfRangeRuleFields() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialProfilePlanV2.ResolutionRule(
                3, MaterialProfilePlanV2.ResolutionRuleId.SNOW_OVERRIDE,
                MaterialProfilePlanV2.RuleMergeOperator.CONDITIONAL_OVERRIDE,
                List.of(MaterialProfilePlanV2.SurfaceAspect.SURFACE)));
        assertThrows(IllegalArgumentException.class, () -> new MaterialProfilePlanV2.ResolutionRule(
                0, MaterialProfilePlanV2.ResolutionRuleId.BASE_SUBSTRATE_FROM_LITHOLOGY,
                MaterialProfilePlanV2.RuleMergeOperator.BASE_ASSIGNMENT,
                List.of()));
    }

    @Test
    void rejectsCatalogEntryThatDoesNotMatchTheBuiltInDefinition() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialProfilePlanV2.Entry(
                MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_EXPOSED,
                "material.host-rock-exposed", 1,
                MaterialProfilePlanV2.SubstrateCategory.SEDIMENT, false, false));
        assertThrows(IllegalArgumentException.class, () -> new MaterialProfilePlanV2.Entry(
                MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_EXPOSED,
                "material.arbitrary-class", 1,
                MaterialProfilePlanV2.SubstrateCategory.ROCK, false, false));
    }

    @Test
    void requireByCodeRejectsUnknownSemanticId() {
        MaterialProfilePlanV2 plan = build(
                MaterialProfilePlanV2.ResolutionRule.standardOrder(), standardBudget(1_000L));
        assertThrows(IllegalArgumentException.class, () -> plan.catalog().requireByCode(99));
    }

    @Test
    void rejectsInsufficientCpuBudget() {
        assertThrows(IllegalArgumentException.class, () -> build(
                MaterialProfilePlanV2.ResolutionRule.standardOrder(),
                new MaterialProfilePlanV2.ResourceBudget(
                        "material-profile-budget-v1", 1_000L, 3, 1L, 4_096L, 256, 262_144L, 32_768L)));
    }

    @Test
    void rejectsUnknownBindingContracts() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialProfilePlanV2.WaterConditionBinding(
                1, ZERO_CHECKSUM, "environment.water.salinity",
                "material-profile-water-condition-binding-v1"));
        assertThrows(IllegalArgumentException.class, () -> new MaterialProfilePlanV2.SnowBinding(
                1, ZERO_CHECKSUM, "environment.snow.potential",
                "material-profile-snow-binding-v1"));
        assertThrows(IllegalArgumentException.class, () -> new MaterialProfilePlanV2(
                2, MaterialProfilePlanV2.PROFILE_CONTRACT_VERSION,
                standardGeologyBinding(), standardWaterConditionBinding(), standardSnowBinding(),
                standardCatalog(), MaterialProfilePlanV2.ResolutionRule.standardOrder(),
                MaterialProfilePlanV2.Kernel.standard(), standardBudget(1_000L), ZERO_CHECKSUM));
    }
}
