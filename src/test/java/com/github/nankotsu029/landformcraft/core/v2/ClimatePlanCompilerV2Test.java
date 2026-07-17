package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.climate.ClimateFieldModulesV2;
import com.github.nankotsu029.landformcraft.generator.v2.climate.ClimateFieldSamplerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.DeterministicHydrologyRoutingSolverV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingRequestV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingResultV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.ProvisionalSurfaceV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
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

class ClimatePlanCompilerV2Test {
    private static final Path DELTA =
            Path.of("examples/v2/diagnostic/scenarios/delta.terrain-intent-v2.json");
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void bundledExampleMatchesCompilerContract() throws IOException {
        ClimatePlanV2 expected = compile(bounds(400, 400), 128, 827413L,
                ClimatePlanV2.BaseClimatePreset.TEMPERATE_MARITIME);
        assertEquals(expected, codec.readClimatePlan(
                Path.of("examples/v2/climate/climate-plan-v2.json")));
    }

    @Test
    void compilesSeparatePriorAndFinalFieldsWithExplicitHydrologyTransition(@TempDir Path directory)
            throws IOException {
        WorldBlueprintV2.Bounds bounds = bounds(96, 64);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        ClimatePlanV2 plan = compile(bounds, 128, 827413L, hydrology,
                ClimatePlanV2.BaseClimatePreset.TEMPERATE_HUMID);

        assertEquals(ClimatePlanV2.VERSION, plan.planVersion());
        assertEquals(2, plan.fields().stream()
                .filter(field -> field.phase() == ClimatePlanV2.FieldPhase.PRIOR).count());
        assertEquals(2, plan.fields().stream()
                .filter(field -> field.phase() == ClimatePlanV2.FieldPhase.FINAL).count());
        assertEquals(hydrology.canonicalChecksum(),
                plan.hydrologyHandoff().sourceHydrologyPlanChecksum());
        assertEquals(HydrologyPlanV2.FixedPriors.CHECKSUM,
                plan.hydrologyHandoff().sourceHydrologyPriorChecksum());
        assertEquals(plan.coarsePrior().priorChecksum(),
                plan.hydrologyHandoff().replacementClimatePriorChecksum());
        assertEquals(ClimatePlanV2.HydrologyRunoffHandoff.SOURCE_GENERATOR_VERSION,
                plan.hydrologyHandoff().sourceGeneratorVersion());
        assertEquals(ClimatePlanV2.HydrologyRunoffHandoff.TARGET_GENERATOR_VERSION,
                plan.hydrologyHandoff().targetGeneratorVersion());
        assertEquals(codec.climatePlanChecksum(plan), plan.canonicalChecksum());
        plan.requireHydrologyPlan(hydrology);

        Path artifact = directory.resolve("climate-plan-v2.json");
        codec.writeClimatePlan(artifact, plan);
        assertEquals(plan, codec.readClimatePlan(artifact));
        assertEquals(codec.canonicalClimatePlan(plan), Files.readString(artifact));
    }

    @Test
    void finalTemperatureAndMoistureUseLapseExposureAndHydrologyWithoutMutatingPrior() {
        ClimatePlanV2 plan = compile(bounds(96, 65), 128, 827413L,
                ClimatePlanV2.BaseClimatePreset.TEMPERATE_MARITIME);
        ClimateFieldSamplerV2 sampler = new ClimateFieldSamplerV2(plan);
        int x = 48;
        int z = 32;
        var lowSheltered = new ClimateFieldSamplerV2.FinalInputs(50, 0, 0);
        var highExposed = new ClimateFieldSamplerV2.FinalInputs(150, 1_000_000, 0);
        var wetValley = new ClimateFieldSamplerV2.FinalInputs(50, 0, 1_000_000);

        int prior = sampler.rawValueAt(ClimatePlanV2.FieldSemantic.PRIOR_PRECIPITATION, x, z, null);
        int runoff = sampler.rawValueAt(ClimatePlanV2.FieldSemantic.PRIOR_RUNOFF, x, z, null);
        assertEquals(prior, sampler.rawValueAt(
                ClimatePlanV2.FieldSemantic.PRIOR_PRECIPITATION, x, z, highExposed));
        assertTrue(sampler.rawValueAt(
                ClimatePlanV2.FieldSemantic.FINAL_TEMPERATURE, x, z, highExposed)
                < sampler.rawValueAt(
                ClimatePlanV2.FieldSemantic.FINAL_TEMPERATURE, x, z, lowSheltered));
        assertTrue(sampler.rawValueAt(
                ClimatePlanV2.FieldSemantic.FINAL_MOISTURE, x, z, highExposed)
                < sampler.rawValueAt(
                ClimatePlanV2.FieldSemantic.FINAL_MOISTURE, x, z, lowSheltered));
        assertTrue(sampler.rawValueAt(
                ClimatePlanV2.FieldSemantic.FINAL_MOISTURE, x, z, wetValley)
                > sampler.rawValueAt(
                ClimatePlanV2.FieldSemantic.FINAL_MOISTURE, x, z, lowSheltered));
        assertNotEquals(prior, runoff);
    }

    @Test
    void rejectsImplicitUnknownFutureAndMismatchedVersions() {
        ClimatePlanV2 plan = compile(bounds(64, 48), 128, 1L,
                ClimatePlanV2.BaseClimatePreset.TEMPERATE_MARITIME);
        String canonical = codec.canonicalClimatePlan(plan);

        assertThrows(IllegalArgumentException.class, () -> ClimatePlanCompilerV2.requirePreset(null));
        assertThrows(IllegalArgumentException.class, () -> ClimatePlanCompilerV2.requirePreset(""));
        assertThrows(IllegalArgumentException.class,
                () -> ClimatePlanCompilerV2.requirePreset("IMPLICIT_DEFAULT"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readClimatePlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future-climate-plan"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readClimatePlan(
                canonical.replace(ClimatePlanV2.HydrologyRunoffHandoff.TARGET_GENERATOR_VERSION,
                        "hydrology-priority-flood-climate-prior-v2"), "future-climate-handoff"));
        assertThrows(IOException.class, () -> codec.readClimatePlan(
                canonical.replace(plan.canonicalChecksum(), "0".repeat(64)), "tampered-climate-plan"));

        HydrologyPlanV2 otherHydrology = new HydrologyPlanCompilerV2().compile(bounds(65, 48));
        assertThrows(IllegalArgumentException.class, () -> plan.requireHydrologyPlan(otherHydrology));
    }

    @Test
    void wholeTileOrderThreadLocaleAndTimezoneAreStable() throws Exception {
        ClimatePlanV2 plan = compile(bounds(257, 259), 128, 827413L,
                ClimatePlanV2.BaseClimatePreset.COLD_MARITIME);
        ClimateFieldSamplerV2 sampler = new ClimateFieldSamplerV2(plan);
        ClimateFieldSamplerV2.FinalInputSource inputs = (x, z) -> new ClimateFieldSamplerV2.FinalInputs(
                20 + Math.floorMod(x * 7 + z * 11, 160),
                Math.floorMod(x * 31 - z * 17, 1_000_001),
                Math.floorMod(x * 13 + z * 19, 1_000_001));
        int[] whole = sampleWhole(sampler, plan, ClimatePlanV2.FieldSemantic.FINAL_MOISTURE, inputs);

        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            ClimatePlanV2 reordered = compile(bounds(257, 259), 128, 827413L,
                    ClimatePlanV2.BaseClimatePreset.COLD_MARITIME);
            assertEquals(plan.namedSeed(), reordered.namedSeed());
            assertEquals(plan.canonicalChecksum(), reordered.canonicalChecksum());
            assertArrayEquals(whole, sampleTiled(
                    new ClimateFieldSamplerV2(reordered), reordered,
                    ClimatePlanV2.FieldSemantic.FINAL_MOISTURE, inputs, 128, false, 1));
            assertArrayEquals(whole, sampleTiled(
                    new ClimateFieldSamplerV2(reordered), reordered,
                    ClimatePlanV2.FieldSemantic.FINAL_MOISTURE, inputs, 64, true, 4));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
        assertNotEquals(plan.namedSeed(), compile(bounds(257, 259), 128, 827414L,
                ClimatePlanV2.BaseClimatePreset.COLD_MARITIME).namedSeed());
    }

    @Test
    void sameClimatePriorKeepsHydrologyGraphStableWhileFinalFieldsCanChange() {
        WorldBlueprintV2.Bounds bounds = bounds(64, 48);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        ClimatePlanV2 firstPlan = compile(bounds, 128, 827413L, hydrology,
                ClimatePlanV2.BaseClimatePreset.TEMPERATE_HUMID);
        ClimatePlanV2 secondPlan = compile(bounds, 64, 827413L, hydrology,
                ClimatePlanV2.BaseClimatePreset.TEMPERATE_HUMID);
        assertEquals(firstPlan.coarsePrior().priorChecksum(), secondPlan.coarsePrior().priorChecksum());
        assertEquals(firstPlan.hydrologyHandoff(), secondPlan.hydrologyHandoff());

        ClimateFieldSamplerV2 sampler = new ClimateFieldSamplerV2(firstPlan);
        String firstFinal = sampler.checksum(ClimatePlanV2.FieldSemantic.FINAL_TEMPERATURE,
                (x, z) -> new ClimateFieldSamplerV2.FinalInputs(50, 0, 0));
        String secondFinal = sampler.checksum(ClimatePlanV2.FieldSemantic.FINAL_TEMPERATURE,
                (x, z) -> new ClimateFieldSamplerV2.FinalInputs(100, 500_000, 0));
        assertNotEquals(firstFinal, secondFinal);

        ProvisionalSurfaceV2 surface = ProvisionalSurfaceV2.routable(
                (x, z) -> 40_000_000 + Math.floorMod(x * 31 + z * 17, 2_000) * 1_000);
        var outlets = List.of(new HydrologyRoutingArtifactV2.Outlet(
                "west", 0, 24, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY));
        HydrologyRoutingRequestV2 request = HydrologyRoutingRequestV2.create(
                bounds.width(), bounds.length(), hydrology, surface, outlets);
        DeterministicHydrologyRoutingSolverV2 solver = new DeterministicHydrologyRoutingSolverV2();
        HydrologyRoutingResultV2 forward = solver.solve(request,
                new HydrologyRoutingRequestV2.ExecutionProfile(
                        32, HydrologyRoutingRequestV2.TileOrder.FORWARD, 1), () -> false);
        HydrologyRoutingResultV2 reverse = solver.solve(request,
                new HydrologyRoutingRequestV2.ExecutionProfile(
                        64, HydrologyRoutingRequestV2.TileOrder.REVERSE, 4), () -> false);
        assertEquals(forward.graphChecksum(), reverse.graphChecksum());
        assertEquals(forward.fixedPriorChecksum(), reverse.fixedPriorChecksum());
        assertEquals(HydrologyPlanV2.FixedPriors.CHECKSUM, forward.fixedPriorChecksum());
    }

    @Test
    void thousandSquareUsesCoarsePriorAndBoundedWindows() {
        ClimatePlanV2 plan = compile(bounds(1_000, 1_000), 128, 827413L,
                ClimatePlanV2.BaseClimatePreset.WARM_HUMID);

        assertEquals(1_000_000L, plan.budget().globalCellCount());
        assertEquals(1_089L, plan.budget().coarseCellCount());
        assertTrue(plan.budget().estimatedRetainedBytes() < 80L * 1024L);
        assertTrue(plan.budget().maximumWorkingBytes() <= 1024L * 1024L);
        assertTrue(plan.budget().estimatedCpuWorkUnits() <= 4_100_000L);
        ClimateFieldSamplerV2 sampler = new ClimateFieldSamplerV2(plan);
        assertThrows(IllegalArgumentException.class, () -> sampler.sampleWindow(
                ClimatePlanV2.FieldSemantic.PRIOR_PRECIPITATION, 0, 0, 257, 1, null));

        ClimatePlanV2.ResourceBudget budget = plan.budget();
        ClimatePlanV2.ResourceBudget understated = new ClimatePlanV2.ResourceBudget(
                budget.budgetVersion(), budget.maximumFields(), budget.globalCellCount(),
                budget.coarseCellCount(), budget.estimatedCpuWorkUnits(), budget.estimatedRetainedBytes(),
                budget.maximumWindowSize(), budget.maximumWorkingBytes() - 1L,
                budget.maximumCanonicalBytes());
        assertThrows(IllegalArgumentException.class, () -> copy(plan, understated));
    }

    @Test
    void blueprintBindsClimateStagesFieldsAndRejectsMissingOrUnknownPreset() throws IOException {
        TerrainIntentV2 intent = codec.readTerrainIntent(DELTA);
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                request(intent.intentId(), 400, 400), intent);

        assertEquals(intent.environment().climatePreset(), blueprint.climatePlan().baseClimatePreset().name());
        assertTrue(blueprint.stages().stream().anyMatch(stage ->
                stage.stageId().equals(ClimateFieldModulesV2.PRIOR_STAGE_ID)
                        && stage.dependsOnStageIds().contains("generate.geology-foundation")));
        assertTrue(blueprint.stages().stream().anyMatch(stage ->
                stage.stageId().equals("compile.hydrology-ir")
                        && stage.dependsOnStageIds().contains(ClimateFieldModulesV2.PRIOR_STAGE_ID)));
        assertTrue(blueprint.stages().stream().anyMatch(stage ->
                stage.stageId().equals(ClimateFieldModulesV2.FINAL_STAGE_ID)
                        && stage.dependsOnStageIds().contains("reconcile.hydrology")));
        for (ClimatePlanV2.FieldBinding binding : blueprint.climatePlan().fields()) {
            assertTrue(blueprint.fieldOwnership().stream().anyMatch(owner ->
                    owner.fieldId().equals(binding.fieldId())
                            && owner.moduleId().equals(binding.ownerModuleId())));
        }

        DiagnosticCompilationException missing = assertThrows(DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(
                        request(intent.intentId(), 400, 400), withClimate(intent, null)));
        assertEquals("v2.unknown-climate", missing.ruleId());
        DiagnosticCompilationException unknown = assertThrows(DiagnosticCompilationException.class,
                () -> new DiagnosticBlueprintCompilerV2().compile(
                        request(intent.intentId(), 400, 400), withClimate(intent, "IMPLICIT_DEFAULT")));
        assertEquals("v2.unknown-climate", unknown.ruleId());
    }

    private ClimatePlanV2 compile(
            WorldBlueprintV2.Bounds bounds,
            int tileSize,
            long seed,
            ClimatePlanV2.BaseClimatePreset preset
    ) {
        return compile(bounds, tileSize, seed, new HydrologyPlanCompilerV2().compile(bounds), preset);
    }

    private ClimatePlanV2 compile(
            WorldBlueprintV2.Bounds bounds,
            int tileSize,
            long seed,
            HydrologyPlanV2 hydrology,
            ClimatePlanV2.BaseClimatePreset preset
    ) {
        return new ClimatePlanCompilerV2().compile(bounds, tileSize, seed, hydrology, preset);
    }

    private ClimatePlanV2 copy(ClimatePlanV2 source, ClimatePlanV2.ResourceBudget budget) {
        return codec.sealClimatePlan(new ClimatePlanV2(
                source.planVersion(), source.fieldContractVersion(), source.baseClimatePreset(),
                source.priorModuleId(), source.priorModuleVersion(), source.priorStageId(),
                source.finalModuleId(), source.finalModuleVersion(), source.finalStageId(),
                source.namedSeed(), source.seedNamespace(), source.width(), source.length(),
                source.minY(), source.maxY(), source.referenceElevationY(), source.coarsePrior(),
                source.finalKernel(), source.hydrologyHandoff(), source.fields(), budget, "0".repeat(64)));
    }

    private static int[] sampleWhole(
            ClimateFieldSamplerV2 sampler,
            ClimatePlanV2 plan,
            ClimatePlanV2.FieldSemantic semantic,
            ClimateFieldSamplerV2.FinalInputSource inputs
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
            ClimateFieldSamplerV2 sampler,
            ClimatePlanV2 plan,
            ClimatePlanV2.FieldSemantic semantic,
            ClimateFieldSamplerV2.FinalInputSource inputs,
            int tileSize,
            boolean reverse,
            int threads
    ) throws Exception {
        List<Tile> tiles = new ArrayList<>();
        for (int z = 0; z < plan.length(); z += tileSize) {
            for (int x = 0; x < plan.width(); x += tileSize) {
                tiles.add(new Tile(x, z, Math.min(tileSize, plan.width() - x),
                        Math.min(tileSize, plan.length() - z), null));
            }
        }
        if (reverse) Collections.reverse(tiles);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Tile>> futures = new ArrayList<>();
            for (Tile tile : tiles) {
                futures.add(executor.submit(() -> new Tile(
                        tile.x(), tile.z(), tile.width(), tile.length(),
                        sampler.sampleWindow(semantic, tile.x(), tile.z(), tile.width(), tile.length(), inputs))));
            }
            int[] result = new int[Math.multiplyExact(plan.width(), plan.length())];
            for (Future<Tile> future : futures) {
                Tile tile = future.get();
                for (int localZ = 0; localZ < tile.length(); localZ++) {
                    System.arraycopy(tile.values(), localZ * tile.width(), result,
                            (tile.z() + localZ) * plan.width() + tile.x(), tile.width());
                }
            }
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    private static TerrainIntentV2 withClimate(TerrainIntentV2 source, String climatePreset) {
        return new TerrainIntentV2(
                source.intentVersion(), source.intentId(), source.theme(), source.coordinateSystem(),
                source.features(), source.relations(), source.constraints(),
                new TerrainIntentV2.EnvironmentDescriptor(
                        source.environment().geologyPreset(), climatePreset, source.environment().ecologyPreset()),
                source.mapReferences(), source.structures(), source.provenance());
    }

    private static DiagnosticCompileRequestV2 request(String requestId, int width, int length) {
        return new DiagnosticCompileRequestV2(
                requestId, new GenerationBounds(width, length, -64, 255, 50), 128, 827413L,
                "a".repeat(64), DiagnosticCompileRequestV2.defaultBudget());
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, -64, 255, 50);
    }

    private record Tile(int x, int z, int width, int length, int[] values) {
    }
}
