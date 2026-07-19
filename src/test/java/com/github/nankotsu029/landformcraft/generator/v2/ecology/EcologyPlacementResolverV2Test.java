package com.github.nankotsu029.landformcraft.generator.v2.ecology;

import com.github.nankotsu029.landformcraft.core.v2.ClimatePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.EcologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.GeologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.HydrologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.WaterConditionPlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcologyPlacementResolverV2Test {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void mangroveHabitatEligibilityDensitySpacingAndSupport() {
        Fixture fixture = fixture(64, 48, 11L, EcologyPlanV2.EcologyPreset.MANGROVE_ESTUARY);
        EcologyPlacementResolverV2 resolver = resolver(fixture);

        EcologyPlacementResolverV2.CellInputs eligible = mangroveInputs(true, true, true, 0);
        assertEquals(
                EcologyPlanV2.HabitatClass.MANGROVE_WETLAND.compactCode(),
                resolver.habitatCodeAt(8, 8, eligible));

        Optional<EcologyPlacementResolverV2.PlacementDecision> canopy =
                findAnyPlacement(resolver, EcologyPlanV2.AssemblageKind.MANGROVE_CANOPY, eligible);
        Optional<EcologyPlacementResolverV2.PlacementDecision> root =
                findAnyPlacement(resolver, EcologyPlanV2.AssemblageKind.MANGROVE_ROOT, eligible);
        assertTrue(canopy.isPresent() || root.isPresent());

        assertSpacingHonored(resolver, EcologyPlanV2.AssemblageKind.MANGROVE_ROOT, eligible);
    }

    @Test
    void rejectsDryMangroveUnsupportedRootAndDeepColdCoral() {
        Fixture mangrove = fixture(64, 48, 11L, EcologyPlanV2.EcologyPreset.MANGROVE_ESTUARY);
        EcologyPlacementResolverV2 mangroveResolver = resolver(mangrove);

        EcologyPlacementResolverV2.CellInputs dry = mangroveInputs(true, false, true, 0);
        assertEquals(
                EcologyPlanV2.HabitatClass.MANGROVE_WETLAND.compactCode(),
                mangroveResolver.habitatCodeAt(4, 4, dry));
        assertTrue(scanEmpty(mangroveResolver, EcologyPlanV2.AssemblageKind.MANGROVE_CANOPY, dry));
        assertTrue(scanEmpty(mangroveResolver, EcologyPlanV2.AssemblageKind.MANGROVE_ROOT, dry));

        EcologyPlacementResolverV2.CellInputs unsupportedRoot = mangroveInputs(true, true, false, 1);
        assertTrue(scanEmpty(mangroveResolver, EcologyPlanV2.AssemblageKind.MANGROVE_ROOT, unsupportedRoot));

        Fixture coral = fixture(64, 48, 17L, EcologyPlanV2.EcologyPreset.SHALLOW_CORAL_REEF);
        EcologyPlacementResolverV2 coralResolver = resolver(coral);
        EcologyPlacementResolverV2.CellInputs deepCold = coralInputs(true, 200, 900, 100);
        assertTrue(scanEmpty(coralResolver, EcologyPlanV2.AssemblageKind.CORAL_COLONY, deepCold));
        EcologyPlacementResolverV2.CellInputs warmShallow = coralInputs(true, 700, 800, 120);
        assertTrue(findAnyPlacement(coralResolver, EcologyPlanV2.AssemblageKind.CORAL_COLONY, warmShallow)
                .isPresent());
    }

    @Test
    void alpineDescriptorsRespectElevationBands() {
        Fixture fixture = fixture(64, 48, 23L, EcologyPlanV2.EcologyPreset.ALPINE_TREELINE);
        EcologyPlacementResolverV2 resolver = resolver(fixture);

        EcologyPlacementResolverV2.CellInputs shrubBand = alpineInputs(140, 200, 200);
        EcologyPlacementResolverV2.CellInputs meadowBand = alpineInputs(190, 200, 200);
        assertEquals(
                EcologyPlanV2.HabitatClass.ALPINE_VEGETATION.compactCode(),
                resolver.habitatCodeAt(10, 10, shrubBand));
        assertTrue(scanEmpty(resolver, EcologyPlanV2.AssemblageKind.ALPINE_MEADOW, shrubBand));
        assertTrue(scanEmpty(resolver, EcologyPlanV2.AssemblageKind.ALPINE_SHRUB, meadowBand));
    }

    @Test
    void wholeTileSeamThreadAndCandidateOrderAreDeterministic() throws Exception {
        Fixture fixture = fixture(48, 32, 99L, EcologyPlanV2.EcologyPreset.MANGROVE_ESTUARY);
        EcologyPlacementResolverV2 resolver = resolver(fixture);
        EcologyPlacementResolverV2.CellInputs inputs = mangroveInputs(true, true, true, 0);
        EcologyPlacementResolverV2.CellInputSource source = (x, z) -> inputs;

        String habitatWhole = resolver.habitatChecksum(48, 32, source);
        String placementWhole = resolver.placementChecksum(48, 32, source);

        int[] left = resolver.sampleHabitatWindow(0, 0, 24, 32, source);
        int[] right = resolver.sampleHabitatWindow(24, 0, 24, 32, source);
        int[] reconstructed = new int[48 * 32];
        for (int z = 0; z < 32; z++) {
            System.arraycopy(left, z * 24, reconstructed, z * 48, 24);
            System.arraycopy(right, z * 24, reconstructed, z * 48 + 24, 24);
        }
        assertEquals(
                java.util.Arrays.toString(resolver.sampleHabitatWindow(0, 0, 48, 32, source)),
                java.util.Arrays.toString(reconstructed));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(habitatWhole, resolver.habitatChecksum(48, 32, source));
            assertEquals(placementWhole, resolver.placementChecksum(48, 32, source));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> resolver.placementChecksum(48, 32, source)));
            }
            for (Future<String> future : futures) {
                assertEquals(placementWhole, future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        List<EcologyPlanV2.AssemblageKind> forward = new ArrayList<>(fixture.ecology.activeAssemblages());
        List<EcologyPlanV2.AssemblageKind> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);
        assertNotEquals(forward, reverse);
        assertEquals(
                countPlacements(resolver, forward, source),
                countPlacements(resolver, reverse, source));
    }

    @Test
    void rejectsWindowBudgetOverflowAndInactiveAssemblage() {
        Fixture fixture = fixture(32, 24, 3L, EcologyPlanV2.EcologyPreset.SPARSE_COASTAL);
        EcologyPlacementResolverV2 resolver = resolver(fixture);
        EcologyPlacementResolverV2.CellInputs inputs = mangroveInputs(true, true, true, 0);
        assertTrue(resolver.placementAt(
                EcologyPlanV2.AssemblageKind.MANGROVE_ROOT, 2, 2, inputs).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> resolver.sampleHabitatWindow(
                0, 0, 257, 16, (x, z) -> inputs));
    }

    private Optional<EcologyPlacementResolverV2.PlacementDecision> findAnyPlacement(
            EcologyPlacementResolverV2 resolver,
            EcologyPlanV2.AssemblageKind kind,
            EcologyPlacementResolverV2.CellInputs inputs
    ) {
        for (int z = 0; z < resolver.plan().length(); z++) {
            for (int x = 0; x < resolver.plan().width(); x++) {
                Optional<EcologyPlacementResolverV2.PlacementDecision> decision =
                        resolver.placementAt(kind, x, z, inputs);
                if (decision.isPresent()) {
                    return decision;
                }
            }
        }
        return Optional.empty();
    }

    private boolean scanEmpty(
            EcologyPlacementResolverV2 resolver,
            EcologyPlanV2.AssemblageKind kind,
            EcologyPlacementResolverV2.CellInputs inputs
    ) {
        return findAnyPlacement(resolver, kind, inputs).isEmpty();
    }

    private void assertSpacingHonored(
            EcologyPlacementResolverV2 resolver,
            EcologyPlanV2.AssemblageKind kind,
            EcologyPlacementResolverV2.CellInputs inputs
    ) {
        int spacing = kind.minSpacingBlocks();
        java.util.Set<String> tiles = new java.util.HashSet<>();
        int placed = 0;
        for (int z = 0; z < resolver.plan().length(); z++) {
            for (int x = 0; x < resolver.plan().width(); x++) {
                if (resolver.placementAt(kind, x, z, inputs).isPresent()) {
                    placed++;
                    String tile = Math.floorDiv(x, spacing) + ":" + Math.floorDiv(z, spacing);
                    assertTrue(tiles.add(tile), "spacing lattice admits at most one placement per tile");
                }
            }
        }
        assertTrue(placed > 0);
    }

    private int countPlacements(
            EcologyPlacementResolverV2 resolver,
            List<EcologyPlanV2.AssemblageKind> order,
            EcologyPlacementResolverV2.CellInputSource source
    ) {
        int count = 0;
        for (EcologyPlanV2.AssemblageKind kind : order) {
            for (int z = 0; z < resolver.plan().length(); z++) {
                for (int x = 0; x < resolver.plan().width(); x++) {
                    if (resolver.placementAt(kind, x, z, source.at(x, z)).isPresent()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private EcologyPlacementResolverV2.CellInputs mangroveInputs(
            boolean wetland,
            boolean wet,
            boolean substrateWet,
            int openWaterGap
    ) {
        return new EcologyPlacementResolverV2.CellInputs(
                700, wet ? 600 : 100, 400, wet ? 500 : 50, 0,
                wetland ? 1 : 0, openWaterGap, substrateWet ? 1 : 0,
                0, 0, 0, 40);
    }

    private EcologyPlacementResolverV2.CellInputs coralInputs(
            boolean reef,
            int temperature,
            int salinity,
            int depth
    ) {
        return new EcologyPlacementResolverV2.CellInputs(
                temperature, 200, salinity, 100, 0,
                0, 0, 0, reef ? 1 : 0, depth, 0, 20);
    }

    private EcologyPlacementResolverV2.CellInputs alpineInputs(
            int surfaceY,
            int temperature,
            int snowCover
    ) {
        return new EcologyPlacementResolverV2.CellInputs(
                temperature, 100, 0, 0, snowCover,
                0, 0, 0, 0, 0, 0, surfaceY);
    }

    private EcologyPlacementResolverV2 resolver(Fixture fixture) {
        return new EcologyPlacementResolverV2(
                fixture.climate, fixture.waterCondition, fixture.snow, fixture.ecology);
    }

    private Fixture fixture(int width, int length, long seed, EcologyPlanV2.EcologyPreset preset) {
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(width, length, -64, 255, 50);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        new GeologyPlanCompilerV2().compile(bounds, 128, seed, hydrology.fixedPriors());
        ClimatePlanV2 climate = new ClimatePlanCompilerV2().compile(
                bounds, 128, seed, hydrology, ClimatePlanV2.BaseClimatePreset.TEMPERATE_MARITIME);
        WaterConditionPlanV2 waterCondition = new WaterConditionPlanCompilerV2().compile(
                bounds, 128, seed, hydrology, climate);
        SnowPlanV2 snow = snowPlan(width, length, climate.minY(), climate.maxY(), seed);
        EcologyPlanV2 ecology = new EcologyPlanCompilerV2().compile(climate, waterCondition, snow, preset);
        return new Fixture(climate, waterCondition, snow, ecology);
    }

    private SnowPlanV2 snowPlan(int width, int length, int minY, int maxY, long seed) {
        long cells = Math.multiplyExact((long) width, length);
        int windowSize = Math.min(256, Math.max(width, length));
        long workingBytes = Math.max(1L, Math.multiplyExact(
                Math.multiplyExact((long) Math.min(width, windowSize), Math.min(length, windowSize)),
                2L * Integer.BYTES));
        SnowPlanV2 draft = new SnowPlanV2(
                SnowPlanV2.VERSION,
                SnowPlanV2.FIELD_CONTRACT_VERSION,
                "generate.snow",
                "0.1.0-v2-4-06",
                "stage.snow",
                seed,
                SnowPlanV2.SEED_NAMESPACE,
                width,
                length,
                minY,
                maxY,
                SnowPlanV2.Kernel.standard(),
                new SnowPlanV2.ClimateBinding(
                        1, "0".repeat(64), "climate.final.temperature", "climate.final.moisture",
                        "snow-climate-binding-v1"),
                List.of(
                        new SnowPlanV2.FieldBinding(
                                "environment.snow.potential", SnowPlanV2.FieldSemantic.SNOW_POTENTIAL,
                                SnowPlanV2.FieldValueType.U16, "generate.snow",
                                SnowPlanV2.Ownership.SINGLE_OWNER, SnowPlanV2.Sampling.NEAREST, 1_000),
                        new SnowPlanV2.FieldBinding(
                                "environment.snow.cover", SnowPlanV2.FieldSemantic.SNOW_COVER,
                                SnowPlanV2.FieldValueType.U16, "generate.snow",
                                SnowPlanV2.Ownership.SINGLE_OWNER, SnowPlanV2.Sampling.NEAREST, 1_000)),
                new SnowPlanV2.ResourceBudget(
                        "snow-field-budget-v1", 2, cells, Math.multiplyExact(cells, 2L), 32_768L, windowSize,
                        workingBytes, 131_072L),
                "0".repeat(64));
        return codec.sealSnowPlan(draft);
    }

    private record Fixture(
            ClimatePlanV2 climate,
            WaterConditionPlanV2 waterCondition,
            SnowPlanV2 snow,
            EcologyPlanV2 ecology
    ) {
    }
}
