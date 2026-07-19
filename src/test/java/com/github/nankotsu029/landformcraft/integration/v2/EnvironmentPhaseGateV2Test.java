package com.github.nankotsu029.landformcraft.integration.v2;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.EcologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.FeatureMaterialProfilePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.MaterialProfilePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.MinecraftPalettePlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.climate.ClimateFieldModulesV2;
import com.github.nankotsu029.landformcraft.generator.v2.environment.snow.SnowFieldModulesV2;
import com.github.nankotsu029.landformcraft.generator.v2.environment.water.WaterConditionFieldModulesV2;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.feature.FeatureMaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-4-15 offline environment lifecycle, integration, determinism, and resource evidence. */
class EnvironmentPhaseGateV2Test {
    private static final long MIB = 1024L * 1024L;
    private static final List<Scenario> SCENARIOS = List.of(
            scenario("snowy-mountains", 257, 257),
            scenario("mangrove-wetland", 257, 257),
            scenario("coral-reef", 257, 257),
            scenario("volcanic-archipelago", 257, 257),
            scenario("canyon-waterfall", 257, 257));

    private static final Set<TerrainIntentV2.FeatureKind> SUPPORTED_ENVIRONMENT_KINDS = Set.of(
            TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE,
            TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE,
            TerrainIntentV2.FeatureKind.MANGROVE_WETLAND,
            TerrainIntentV2.FeatureKind.CORAL_REEF,
            TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO);

    @Test
    void environmentInfrastructureAndCompletedKindsAreSupportedWhileDeferredKindsRemainExperimental() {
        BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();
        Set<String> supportedEnvironmentModules = catalog.modules().stream()
                .filter(module -> module.lifecycleStatus() == ModuleDescriptorV2.LifecycleStatus.SUPPORTED)
                .map(ModuleDescriptorV2::moduleId)
                .filter(moduleId -> moduleId.startsWith("v2.environment."))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(Set.of(
                GeologyFoundationModuleV2.MODULE_ID,
                ClimateFieldModulesV2.PRIOR_MODULE_ID,
                ClimateFieldModulesV2.FINAL_MODULE_ID,
                WaterConditionFieldModulesV2.MODULE_ID), supportedEnvironmentModules);
        assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                new SnowFieldModulesV2().descriptor().lifecycleStatus());

        for (TerrainIntentV2.FeatureKind kind : SUPPORTED_ENVIRONMENT_KINDS) {
            assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                    catalog.requireFor(kind).lifecycleStatus(), kind.name());
        }
        assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                catalog.requireFor(TerrainIntentV2.FeatureKind.WATERFALL).lifecycleStatus(),
                "WATERFALL is SUPPORTED since the V2-5 phase gate");
        for (TerrainIntentV2.FeatureKind kind : Set.of(
                TerrainIntentV2.FeatureKind.GLACIAL_CIRQUE_FIELD,
                TerrainIntentV2.FeatureKind.LAGOON,
                TerrainIntentV2.FeatureKind.REEF_PASS,
                TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA,
                TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD)) {
            assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                    catalog.requireFor(kind).lifecycleStatus(), kind.name());
        }
        assertEquals(List.of(
                        ReleaseArtifactCatalogV2.ENVIRONMENT_FIELDS,
                        ReleaseArtifactCatalogV2.HYDROLOGY_PLAN,
                        ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE);
    }

    @Test
    void scenarioPortfolioIsStableAcrossOrderThreadsModuleOrderLocaleAndTimezone() throws Exception {
        Map<Path, PortfolioDigest> baseline = compile(SCENARIOS, 1);
        List<Scenario> reversed = new ArrayList<>(SCENARIOS);
        Collections.reverse(reversed);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(baseline, compile(reversed, 4));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void thousandSquareStagePeakAndSparseDescriptorBudgetsRemainBounded() throws Exception {
        Portfolio portfolio = portfolio(scenario("snowy-mountains", 1_000, 1_000));
        assertEquals(1_000_000L, portfolio.snow().budget().globalCellCount());
        assertEquals(1_000_000L, portfolio.material().budget().globalCellCount());
        assertEquals(1_000_000L, portfolio.ecology().budget().globalCellCount());
        assertEquals(1_000_000L, portfolio.featureMaterial().budget().globalCellCount());

        long stagePeak = java.util.stream.LongStream.of(
                Math.addExact(portfolio.blueprint().geologyPlan().budget().estimatedRetainedBytes(),
                        portfolio.blueprint().geologyPlan().budget().maximumWorkingBytes()),
                Math.addExact(portfolio.blueprint().climatePlan().budget().estimatedRetainedBytes(),
                        portfolio.blueprint().climatePlan().budget().maximumWorkingBytes()),
                Math.addExact(portfolio.blueprint().waterConditionPlan().budget().estimatedRetainedBytes(),
                        portfolio.blueprint().waterConditionPlan().budget().maximumWorkingBytes()),
                Math.addExact(portfolio.snow().budget().estimatedRetainedBytes(),
                        portfolio.snow().budget().maximumWorkingBytes()),
                Math.addExact(portfolio.material().budget().estimatedRetainedBytes(),
                        portfolio.material().budget().maximumWorkingBytes()),
                Math.addExact(portfolio.ecology().budget().estimatedRetainedBytes(),
                        portfolio.ecology().budget().maximumWorkingBytes()),
                Math.addExact(portfolio.featureMaterial().budget().estimatedRetainedBytes(),
                        portfolio.featureMaterial().budget().maximumWorkingBytes()),
                portfolio.palette().budget().estimatedRetainedBytes()).max().orElseThrow();
        assertTrue(stagePeak <= 32L * MIB, "environment stage peak must remain bounded");
        assertTrue(portfolio.ecology().budget().maximumPlacementDescriptorsPerWindow() <= 65_536L);
        assertTrue(portfolio.ecology().budget().maximumWorkingBytes() <= 4L * MIB);
        assertTrue(portfolio.featureMaterial().budget().maximumWorkingBytes() <= 4L * MIB);
        assertTrue(portfolio.palette().budget().maximumPaletteRetainedBytes() <= 16L * MIB);
    }

    private static Map<Path, PortfolioDigest> compile(List<Scenario> scenarios, int threads) throws Exception {
        List<Callable<Map.Entry<Path, PortfolioDigest>>> tasks = scenarios.stream()
                .<Callable<Map.Entry<Path, PortfolioDigest>>>map(scenario -> () -> {
                    Portfolio portfolio = portfolio(scenario);
                    WorldBlueprintV2 reordered = reorderModuleAndStageInputs(portfolio.blueprint());
                    assertEquals(portfolio.blueprint().canonicalChecksum(), reordered.canonicalChecksum());
                    return Map.entry(scenario.path(), portfolio.digest());
                }).toList();
        Map<Path, PortfolioDigest> result = new LinkedHashMap<>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (var future : executor.invokeAll(tasks)) {
                Map.Entry<Path, PortfolioDigest> entry = future.get();
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static Portfolio portfolio(Scenario scenario) throws Exception {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        TerrainIntentV2 intent = codec.readTerrainIntent(scenario.path());
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                new DiagnosticCompileRequestV2(
                        intent.intentId(),
                        new GenerationBounds(scenario.width(), scenario.length(), -64, 255, 63),
                        128,
                        827_413L,
                        "a".repeat(64),
                        DiagnosticCompileRequestV2.defaultBudget()),
                intent);
        SnowPlanV2 snow = snowPlan(codec, blueprint);
        MaterialProfilePlanV2 material = new MaterialProfilePlanCompilerV2().compile(
                blueprint.geologyPlan(), blueprint.lithologyPlan(), blueprint.strataPlan(),
                blueprint.waterConditionPlan(), snow);
        MinecraftPalettePlanV2 palette = new MinecraftPalettePlanCompilerV2().compile(material);
        EcologyPlanV2 ecology = new EcologyPlanCompilerV2().compile(
                blueprint.climatePlan(), blueprint.waterConditionPlan(), snow,
                EcologyPlanV2.EcologyPreset.valueOf(intent.environment().ecologyPreset()));
        FeatureMaterialProfilePlanV2 featureMaterial = new FeatureMaterialProfilePlanCompilerV2().compile(
                material, blueprint.geologyPlan(), blueprint.lithologyPlan(), blueprint.strataPlan(),
                blueprint.volcanicPlans(), blueprint.canyonPlans());
        return new Portfolio(blueprint, snow, material, palette, ecology, featureMaterial);
    }

    private static SnowPlanV2 snowPlan(LandformV2DataCodec codec, WorldBlueprintV2 blueprint) {
        int width = blueprint.space().bounds().width();
        int length = blueprint.space().bounds().length();
        long cells = Math.multiplyExact((long) width, length);
        int windowSize = Math.min(256, Math.max(width, length));
        long workingBytes = Math.max(1L, Math.multiplyExact(
                Math.multiplyExact((long) Math.min(width, windowSize), Math.min(length, windowSize)),
                2L * Integer.BYTES));
        SnowPlanV2 draft = new SnowPlanV2(
                SnowPlanV2.VERSION,
                SnowPlanV2.FIELD_CONTRACT_VERSION,
                SnowFieldModulesV2.MODULE_ID,
                SnowFieldModulesV2.MODULE_VERSION,
                SnowFieldModulesV2.STAGE_ID,
                blueprint.climatePlan().namedSeed(),
                SnowPlanV2.SEED_NAMESPACE,
                width,
                length,
                blueprint.space().bounds().minY(),
                blueprint.space().bounds().maxY(),
                SnowPlanV2.Kernel.standard(),
                new SnowPlanV2.ClimateBinding(
                        SnowPlanV2.ClimateBinding.VERSION,
                        blueprint.climatePlan().canonicalChecksum(),
                        SnowPlanV2.ClimateBinding.TEMPERATURE_FIELD_ID,
                        SnowPlanV2.ClimateBinding.MOISTURE_FIELD_ID,
                        SnowPlanV2.ClimateBinding.CONTRACT_VERSION),
                new SnowFieldModulesV2().fieldBindings(),
                new SnowPlanV2.ResourceBudget(
                        SnowPlanV2.ResourceBudget.VERSION,
                        2,
                        cells,
                        Math.multiplyExact(cells, 2L),
                        32_768L,
                        windowSize,
                        workingBytes,
                        131_072L),
                "0".repeat(64));
        SnowPlanV2 sealed = codec.sealSnowPlan(draft);
        sealed.requireClimatePlan(blueprint.climatePlan());
        return sealed;
    }

    private static WorldBlueprintV2 reorderModuleAndStageInputs(WorldBlueprintV2 blueprint) {
        List<ModuleDescriptorV2> modules = new ArrayList<>(blueprint.modules());
        List<WorldBlueprintV2.StageDescriptor> stages = new ArrayList<>(blueprint.stages());
        Collections.reverse(modules);
        Collections.reverse(stages);
        WorldBlueprintV2 reordered = new WorldBlueprintV2(
                blueprint.identity(), blueprint.space(), blueprint.determinism(), modules, stages,
                blueprint.fields(), blueprint.fieldOwnership(), blueprint.featurePlans(),
                blueprint.coastalFeaturePlans(), blueprint.sandyBeachPlans(), blueprint.harborBasinPlans(),
                blueprint.breakwaterHarborPlans(), blueprint.rockyCapePlans(), blueprint.coastalTransitionPlans(),
                blueprint.meanderingRiverPlans(), blueprint.lakePlans(), blueprint.canyonPlans(),
                blueprint.waterfallPlans(), blueprint.deltaPlans(), blueprint.tidalChannelPlans(),
                blueprint.mangroveWetlandPlans(), blueprint.coralReefPlans(), blueprint.fjordPlans(),
                blueprint.mountainPlans(), blueprint.volcanicPlans(), blueprint.geologyPlan(),
                blueprint.lithologyPlan(), blueprint.strataPlan(), blueprint.climatePlan(),
                blueprint.waterConditionPlan(), blueprint.hydrologyPlan(), blueprint.hydrologyReconciliationPlan(),
                blueprint.validationTargets(), blueprint.budgets(), blueprint.diagnosticIssues(), "0".repeat(64));
        return new LandformV2DataCodec().sealWorldBlueprint(reordered);
    }

    private static Scenario scenario(String name, int width, int length) {
        return new Scenario(
                Path.of("examples/v2/diagnostic/scenarios/" + name + ".terrain-intent-v2.json"),
                width,
                length);
    }

    private record Scenario(Path path, int width, int length) {
    }

    private record Portfolio(
            WorldBlueprintV2 blueprint,
            SnowPlanV2 snow,
            MaterialProfilePlanV2 material,
            MinecraftPalettePlanV2 palette,
            EcologyPlanV2 ecology,
            FeatureMaterialProfilePlanV2 featureMaterial
    ) {
        private PortfolioDigest digest() {
            return new PortfolioDigest(
                    blueprint.canonicalChecksum(), snow.canonicalChecksum(), material.canonicalChecksum(),
                    palette.canonicalChecksum(), ecology.canonicalChecksum(), featureMaterial.canonicalChecksum());
        }
    }

    private record PortfolioDigest(
            String blueprintChecksum,
            String snowChecksum,
            String materialChecksum,
            String paletteChecksum,
            String ecologyChecksum,
            String featureMaterialChecksum
    ) {
    }
}
