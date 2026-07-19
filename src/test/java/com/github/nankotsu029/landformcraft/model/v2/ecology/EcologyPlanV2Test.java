package com.github.nankotsu029.landformcraft.model.v2.ecology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EcologyPlanV2Test {
    private static final String ZERO_CHECKSUM = "0".repeat(64);

    private EcologyPlanV2.ClimateBinding climateBinding() {
        return new EcologyPlanV2.ClimateBinding(
                1, ZERO_CHECKSUM, "climate.final.temperature", "ecology-climate-binding-v1");
    }

    private EcologyPlanV2.WaterConditionBinding waterBinding() {
        return new EcologyPlanV2.WaterConditionBinding(
                1, ZERO_CHECKSUM,
                "environment.water.wetness",
                "environment.water.salinity",
                "environment.water.hydroperiod",
                "ecology-water-condition-binding-v1");
    }

    private EcologyPlanV2.SnowBinding snowBinding() {
        return new EcologyPlanV2.SnowBinding(
                1, ZERO_CHECKSUM, "environment.snow.cover", "ecology-snow-binding-v1");
    }

    private EcologyPlanV2.Catalog catalog() {
        List<EcologyPlanV2.Entry> entries = java.util.Arrays.stream(EcologyPlanV2.AssemblageKind.values())
                .map(kind -> new EcologyPlanV2.Entry(
                        kind, kind.assemblageId(), kind.compactCode(), kind.habitatClass(),
                        kind.layer(), kind.supportRule(), kind.densityMillionths(),
                        kind.minSpacingBlocks(), kind.clusterScaleBlocks()))
                .toList();
        return new EcologyPlanV2.Catalog(
                1, "landformcraft.builtin-ecology-assemblage", "builtin-ecology-assemblage-catalog-v1",
                entries,
                new EcologyPlanV2.CatalogBudget("ecology-catalog-budget-v1", 5, 16L * 1024L));
    }

    private EcologyPlanV2.ResourceBudget budget(int width, int length, int active) {
        long cells = (long) width * length;
        return new EcologyPlanV2.ResourceBudget(
                "ecology-placement-budget-v1",
                cells,
                5,
                active,
                cells * Math.max(1, active),
                4_096L,
                64,
                16_384L,
                1_024L,
                48_000L);
    }

    private EcologyPlanV2 build(
            EcologyPlanV2.EcologyPreset preset,
            List<EcologyPlanV2.AssemblageKind> active,
            EcologyPlanV2.ResourceBudget resourceBudget
    ) {
        return new EcologyPlanV2(
                EcologyPlanV2.VERSION,
                EcologyPlanV2.PLACEMENT_CONTRACT_VERSION,
                EcologyPlanV2.MODULE_ID,
                EcologyPlanV2.MODULE_VERSION,
                EcologyPlanV2.STAGE_ID,
                1L,
                EcologyPlanV2.SEED_NAMESPACE,
                32,
                24,
                -64,
                255,
                preset,
                climateBinding(),
                waterBinding(),
                snowBinding(),
                catalog(),
                active,
                EcologyPlanV2.Kernel.standard(),
                resourceBudget,
                ZERO_CHECKSUM);
    }

    @Test
    void acceptsClosedMangrovePreset() {
        assertDoesNotThrow(() -> build(
                EcologyPlanV2.EcologyPreset.MANGROVE_ESTUARY,
                EcologyPlanV2.EcologyPreset.MANGROVE_ESTUARY.assemblages(),
                budget(32, 24, 2)));
    }

    @Test
    void rejectsActiveAssemblagesThatDoNotMatchPreset() {
        assertThrows(IllegalArgumentException.class, () -> build(
                EcologyPlanV2.EcologyPreset.MANGROVE_ESTUARY,
                List.of(EcologyPlanV2.AssemblageKind.CORAL_COLONY),
                budget(32, 24, 1)));
    }

    @Test
    void rejectsUnknownCatalogEntryDefinition() {
        assertThrows(IllegalArgumentException.class, () -> new EcologyPlanV2.Entry(
                EcologyPlanV2.AssemblageKind.MANGROVE_ROOT,
                "ecology.arbitrary-root",
                2,
                EcologyPlanV2.HabitatClass.MANGROVE_WETLAND,
                EcologyPlanV2.PlacementLayer.ROOT,
                EcologyPlanV2.SupportRule.WETLAND_ROOT,
                120_000,
                2,
                4));
    }

    @Test
    void rejectsInsufficientCpuBudget() {
        assertThrows(IllegalArgumentException.class, () -> build(
                EcologyPlanV2.EcologyPreset.SHALLOW_CORAL_REEF,
                EcologyPlanV2.EcologyPreset.SHALLOW_CORAL_REEF.assemblages(),
                new EcologyPlanV2.ResourceBudget(
                        "ecology-placement-budget-v1",
                        768L,
                        5,
                        1,
                        1L,
                        4_096L,
                        64,
                        16_384L,
                        1_024L,
                        48_000L)));
    }

    @Test
    void rejectsExternalSeedNamespace() {
        assertThrows(IllegalArgumentException.class, () -> new EcologyPlanV2(
                EcologyPlanV2.VERSION,
                EcologyPlanV2.PLACEMENT_CONTRACT_VERSION,
                EcologyPlanV2.MODULE_ID,
                EcologyPlanV2.MODULE_VERSION,
                EcologyPlanV2.STAGE_ID,
                1L,
                "external.script.seed",
                32,
                24,
                -64,
                255,
                EcologyPlanV2.EcologyPreset.SPARSE_COASTAL,
                climateBinding(),
                waterBinding(),
                snowBinding(),
                catalog(),
                List.of(),
                EcologyPlanV2.Kernel.standard(),
                budget(32, 24, 0),
                ZERO_CHECKSUM));
    }
}
