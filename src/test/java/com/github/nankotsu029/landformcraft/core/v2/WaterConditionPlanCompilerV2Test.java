package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.climate.ClimateFieldModulesV2;
import com.github.nankotsu029.landformcraft.generator.v2.environment.water.WaterConditionFieldModulesV2;
import com.github.nankotsu029.landformcraft.generator.v2.environment.water.WaterConditionFieldSamplerV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterConditionPlanCompilerV2Test {
    private static final Path DELTA =
            Path.of("examples/v2/diagnostic/scenarios/delta.terrain-intent-v2.json");
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void bundledExampleMatchesCompilerContract() throws IOException {
        WaterConditionPlanV2 expected = compile(bounds(400, 400), 128, 827413L);
        assertEquals(expected, codec.readWaterConditionPlan(
                Path.of("examples/v2/environment/water-condition-plan-v2.json")));
    }

    @Test
    void compilesSevenFieldsBoundToHydrologyAndClimate(@TempDir Path directory) throws IOException {
        WorldBlueprintV2.Bounds bounds = bounds(96, 64);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        ClimatePlanV2 climate = climate(bounds, 128, 827413L, hydrology);
        WaterConditionPlanV2 plan = compile(bounds, 128, 827413L, hydrology, climate);

        assertEquals(WaterConditionPlanV2.VERSION, plan.planVersion());
        assertEquals(7, plan.fields().size());
        assertEquals(hydrology.canonicalChecksum(),
                plan.hydrologyBinding().sourceHydrologyPlanChecksum());
        assertEquals(climate.canonicalChecksum(),
                plan.climateBinding().sourceClimatePlanChecksum());
        assertEquals(codec.waterConditionPlanChecksum(plan), plan.canonicalChecksum());
        plan.requireHydrologyPlan(hydrology);
        plan.requireClimatePlan(climate);

        Path artifact = directory.resolve("water-condition-plan-v2.json");
        codec.writeWaterConditionPlan(artifact, plan);
        assertEquals(plan, codec.readWaterConditionPlan(artifact));
        assertEquals(codec.canonicalWaterConditionPlan(plan), Files.readString(artifact));
    }

    @Test
    void riverLakeTideGradientsAndMarineDisconnect() {
        WaterConditionPlanV2 plan = compile(bounds(64, 48), 128, 827413L);
        WaterConditionFieldSamplerV2 sampler = new WaterConditionFieldSamplerV2(plan);
        int x = 16;
        int z = 12;
        var nearRiver = WaterConditionFieldSamplerV2.CellInputs.of(
                55, 500, 0, 64, 64, 64, false, 0, 200, 800);
        var nearLake = WaterConditionFieldSamplerV2.CellInputs.of(
                55, 500, 64, 0, 64, 64, false, 0, 100, 800);
        var nearTideMarine = WaterConditionFieldSamplerV2.CellInputs.of(
                52, 500, 64, 64, 0, 8, true, 900, 100, 700);
        var nearTideDisconnected = WaterConditionFieldSamplerV2.CellInputs.of(
                52, 500, 64, 64, 0, 8, false, 900, 100, 700);
        var farDry = WaterConditionFieldSamplerV2.CellInputs.of(
                120, 200, 64, 64, 64, 64, false, 0, 0, 400);

        assertTrue(sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.WATER_DISTANCE, x, z, nearRiver)
                > sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.WATER_DISTANCE, x, z, farDry));
        assertTrue(sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.WATER_DISTANCE, x, z, nearLake)
                > sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.WATER_DISTANCE, x, z, farDry));
        assertTrue(sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.TIDAL_INFLUENCE, x, z, nearTideMarine)
                > sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.TIDAL_INFLUENCE, x, z, nearTideDisconnected));
        assertEquals(0, sampler.rawValueAt(
                WaterConditionPlanV2.FieldSemantic.SALINITY, x, z, nearTideDisconnected));
        assertTrue(sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.SALINITY, x, z, nearTideMarine) > 0);
        assertTrue(sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.HYDROPERIOD, x, z, nearTideMarine)
                > sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.HYDROPERIOD, x, z, farDry));
        assertTrue(sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.WETNESS, x, z, nearRiver)
                > sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.WETNESS, x, z, farDry));
        assertEquals(
                sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.WETNESS, x, z, nearRiver),
                sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.WETNESS_RESIDUAL, x, z, nearRiver)
                        + nearRiver.moistureRaw());
    }

    @Test
    void rejectsNoDataHardRangeImplicitOceanAndUnboundedDiffusion() {
        WaterConditionPlanV2 plan = compile(bounds(48, 32), 128, 1L);
        WaterConditionFieldSamplerV2 sampler = new WaterConditionFieldSamplerV2(plan);
        var base = WaterConditionFieldSamplerV2.CellInputs.of(
                50, 500, 8, 8, 8, 8, true, 500, 100, 500);

        assertThrows(IllegalArgumentException.class, () -> sampler.rawValueAt(
                WaterConditionPlanV2.FieldSemantic.SALINITY, 0, 0,
                new WaterConditionFieldSamplerV2.CellInputs(
                        50, 500, 8, 8, 8, 8, true, 500, 100, 500, true, false, false)));
        assertThrows(IllegalArgumentException.class, () -> sampler.rawValueAt(
                WaterConditionPlanV2.FieldSemantic.SALINITY, 0, 0,
                new WaterConditionFieldSamplerV2.CellInputs(
                        50, 500, 8, 8, 8, 8, true, 500, 100, 500, false, true, false)));
        assertThrows(IllegalArgumentException.class, () -> sampler.rawValueAt(
                WaterConditionPlanV2.FieldSemantic.SALINITY, 0, 0,
                new WaterConditionFieldSamplerV2.CellInputs(
                        50, 500, 8, 8, 8, 8, true, 500, 100, 500, false, false, true)));
        assertThrows(IllegalArgumentException.class, () -> sampler.rawValueAt(
                WaterConditionPlanV2.FieldSemantic.WATER_DISTANCE, 0, 0,
                WaterConditionFieldSamplerV2.CellInputs.of(
                        50, 500, 65, 8, 8, 8, false, 0, 0, 500)));
        assertThrows(IllegalArgumentException.class, () -> sampler.rawValueAt(
                WaterConditionPlanV2.FieldSemantic.WETNESS, 0, 0,
                WaterConditionFieldSamplerV2.CellInputs.of(
                        50, 1_500, 8, 8, 8, 8, false, 0, 0, 500)));
        assertEquals(0, sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.SALINITY, 0, 0,
                WaterConditionFieldSamplerV2.CellInputs.of(
                        50, 500, 8, 8, 0, 0, false, 900, 0, 500)));
        assertTrue(sampler.rawValueAt(WaterConditionPlanV2.FieldSemantic.WATER_DISTANCE, 0, 0, base) > 0);

        String canonical = codec.canonicalWaterConditionPlan(plan);
        assertThrows(StructuredDataValidationException.class, () -> codec.readWaterConditionPlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future-water-plan"));
        assertThrows(IOException.class, () -> codec.readWaterConditionPlan(
                canonical.replace(plan.canonicalChecksum(), "0".repeat(64)), "tampered-water-plan"));
        HydrologyPlanV2 other = new HydrologyPlanCompilerV2().compile(bounds(49, 32));
        assertThrows(IllegalArgumentException.class, () -> plan.requireHydrologyPlan(other));
    }

    @Test
    void wholeTileOrderThreadLocaleAndTimezoneAreStable() throws Exception {
        WaterConditionPlanV2 plan = compile(bounds(257, 259), 128, 827413L);
        WaterConditionFieldSamplerV2 sampler = new WaterConditionFieldSamplerV2(plan);
        WaterConditionFieldSamplerV2.CellInputSource inputs = (x, z) -> WaterConditionFieldSamplerV2.CellInputs.of(
                20 + Math.floorMod(x * 7 + z * 11, 160),
                Math.floorMod(x * 13 + z * 17, 1_001),
                Math.min(64, Math.floorMod(x * 3 + z, 65)),
                Math.min(64, Math.floorMod(z * 5 + x, 65)),
                Math.min(64, Math.floorMod(x + z * 2, 65)),
                Math.min(64, Math.floorMod(x * 2 + z * 3, 65)),
                Math.floorMod(x + z, 2) == 0,
                Math.floorMod(x * 11, 1_001),
                Math.floorMod(z * 19, 1_001),
                Math.floorMod(x * 23 + z, 1_001));
        int[] whole = sampleWhole(sampler, plan, WaterConditionPlanV2.FieldSemantic.WETNESS, inputs);

        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            WaterConditionPlanV2 reordered = compile(bounds(257, 259), 128, 827413L);
            assertEquals(plan.namedSeed(), reordered.namedSeed());
            assertEquals(plan.canonicalChecksum(), reordered.canonicalChecksum());
            assertArrayEquals(whole, sampleTiled(
                    new WaterConditionFieldSamplerV2(reordered), reordered,
                    WaterConditionPlanV2.FieldSemantic.WETNESS, inputs, 128, false, 1));
            assertArrayEquals(whole, sampleTiled(
                    new WaterConditionFieldSamplerV2(reordered), reordered,
                    WaterConditionPlanV2.FieldSemantic.WETNESS, inputs, 64, true, 4));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
        assertNotEquals(plan.namedSeed(), compile(bounds(257, 259), 128, 827414L).namedSeed());
    }

    @Test
    void thousandSquareUsesBoundedDistanceAndWindows() {
        WaterConditionPlanV2 plan = compile(bounds(1_000, 1_000), 128, 827413L);
        assertEquals(1_000_000L, plan.budget().globalCellCount());
        assertEquals(64, plan.budget().maximumDistanceBlocks());
        assertTrue(plan.budget().estimatedRetainedBytes() <= 64L * 1024L);
        assertTrue(plan.budget().maximumWorkingBytes() <= 1024L * 1024L);
        WaterConditionFieldSamplerV2 sampler = new WaterConditionFieldSamplerV2(plan);
        WaterConditionFieldSamplerV2.CellInputSource inputs = (x, z) -> WaterConditionFieldSamplerV2.CellInputs.of(
                50, 400, 16, 16, 16, 16, true, 400, 100, 500);
        assertThrows(IllegalArgumentException.class, () -> sampler.sampleWindow(
                WaterConditionPlanV2.FieldSemantic.WETNESS, 0, 0, 257, 1, inputs));

        WaterConditionPlanV2.ResourceBudget budget = plan.budget();
        WaterConditionPlanV2.ResourceBudget understated = new WaterConditionPlanV2.ResourceBudget(
                budget.budgetVersion(), budget.maximumFields(), budget.globalCellCount(),
                budget.estimatedCpuWorkUnits(), budget.estimatedRetainedBytes(),
                budget.maximumWindowSize(), budget.maximumDistanceBlocks(),
                budget.maximumWorkingBytes() - 1L, budget.maximumCanonicalBytes());
        assertThrows(IllegalArgumentException.class, () -> copy(plan, understated));
    }

    @Test
    void blueprintBindsWaterConditionStageAfterClimateFinal() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(DELTA);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                request(intent.intentId(), 400, 400), intent);

        assertEquals(blueprint.climatePlan().canonicalChecksum(),
                blueprint.waterConditionPlan().climateBinding().sourceClimatePlanChecksum());
        assertEquals(blueprint.hydrologyPlan().canonicalChecksum(),
                blueprint.waterConditionPlan().hydrologyBinding().sourceHydrologyPlanChecksum());
        assertTrue(blueprint.stages().stream().anyMatch(stage ->
                stage.stageId().equals(WaterConditionFieldModulesV2.STAGE_ID)
                        && stage.dependsOnStageIds().contains(ClimateFieldModulesV2.FINAL_STAGE_ID)
                        && stage.dependsOnStageIds().contains("reconcile.hydrology")));
        for (WaterConditionPlanV2.FieldBinding binding : blueprint.waterConditionPlan().fields()) {
            assertTrue(blueprint.fieldOwnership().stream().anyMatch(owner ->
                    owner.fieldId().equals(binding.fieldId())
                            && owner.moduleId().equals(binding.ownerModuleId())));
        }
    }

    private WaterConditionPlanV2 compile(WorldBlueprintV2.Bounds bounds, int tileSize, long seed) {
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        return compile(bounds, tileSize, seed, hydrology, climate(bounds, tileSize, seed, hydrology));
    }

    private WaterConditionPlanV2 compile(
            WorldBlueprintV2.Bounds bounds,
            int tileSize,
            long seed,
            HydrologyPlanV2 hydrology,
            ClimatePlanV2 climate
    ) {
        return new WaterConditionPlanCompilerV2().compile(bounds, tileSize, seed, hydrology, climate);
    }

    private ClimatePlanV2 climate(
            WorldBlueprintV2.Bounds bounds,
            int tileSize,
            long seed,
            HydrologyPlanV2 hydrology
    ) {
        return new ClimatePlanCompilerV2().compile(
                bounds, tileSize, seed, hydrology, ClimatePlanV2.BaseClimatePreset.TEMPERATE_MARITIME);
    }

    private WaterConditionPlanV2 copy(WaterConditionPlanV2 source, WaterConditionPlanV2.ResourceBudget budget) {
        return codec.sealWaterConditionPlan(new WaterConditionPlanV2(
                source.planVersion(), source.fieldContractVersion(), source.moduleId(), source.moduleVersion(),
                source.stageId(), source.namedSeed(), source.seedNamespace(), source.width(), source.length(),
                source.minY(), source.maxY(), source.referenceWaterY(), source.kernel(),
                source.hydrologyBinding(), source.climateBinding(), source.fields(), budget, "0".repeat(64)));
    }

    private static int[] sampleWhole(
            WaterConditionFieldSamplerV2 sampler,
            WaterConditionPlanV2 plan,
            WaterConditionPlanV2.FieldSemantic semantic,
            WaterConditionFieldSamplerV2.CellInputSource inputs
    ) {
        int[] result = new int[Math.multiplyExact(plan.width(), plan.length())];
        for (int z = 0; z < plan.length(); z++) {
            for (int x = 0; x < plan.width(); x++) {
                result[z * plan.width() + x] = sampler.rawValueAt(semantic, x, z, inputs.at(x, z));
            }
        }
        return result;
    }

    private static int[] sampleTiled(
            WaterConditionFieldSamplerV2 sampler,
            WaterConditionPlanV2 plan,
            WaterConditionPlanV2.FieldSemantic semantic,
            WaterConditionFieldSamplerV2.CellInputSource inputs,
            int tileSize,
            boolean reverse,
            int threads
    ) throws Exception {
        int[] result = new int[Math.multiplyExact(plan.width(), plan.length())];
        List<int[]> tiles = new ArrayList<>();
        for (int startZ = 0; startZ < plan.length(); startZ += tileSize) {
            for (int startX = 0; startX < plan.width(); startX += tileSize) {
                tiles.add(new int[]{
                        startX, startZ,
                        Math.min(tileSize, plan.width() - startX),
                        Math.min(tileSize, plan.length() - startZ)});
            }
        }
        if (reverse) {
            Collections.reverse(tiles);
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int[] tile : tiles) {
                futures.add(executor.submit(() -> {
                    int[] window = sampler.sampleWindow(
                            semantic, tile[0], tile[1], tile[2], tile[3], inputs);
                    for (int localZ = 0; localZ < tile[3]; localZ++) {
                        for (int localX = 0; localX < tile[2]; localX++) {
                            result[(tile[1] + localZ) * plan.width() + tile[0] + localX] =
                                    window[localZ * tile[2] + localX];
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, -64, 255, 50);
    }

    private static DiagnosticCompileRequestV2 request(String requestId, int width, int length) {
        return new DiagnosticCompileRequestV2(
                requestId,
                new com.github.nankotsu029.landformcraft.model.GenerationBounds(width, length, -64, 255, 50),
                128, 827413L, "a".repeat(64), DiagnosticCompileRequestV2.defaultBudget());
    }
}
