package com.github.nankotsu029.landformcraft.integration.v2;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta.HydrologyDeltaModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.HydrologyLakeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal.HydrologyTidalModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.LandformCanyonModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.fjord.LandformFjordModuleV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyValidationPreviewModuleV2;
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

/** V2-3-15 parent-phase lifecycle and scenario-integration evidence; Paper mutation remains disabled. */
class HydrologyPhaseGateV2Test {
    private static final List<Scenario> SCENARIOS = List.of(
            scenario("meandering-river", 129, 193),
            scenario("open-spill-lake", 97, 97),
            scenario("canyon-river-skeleton", 160, 192),
            scenario("waterfall-2_5d-skeleton", 129, 193),
            scenario("delta-distributary-fan", 160, 160),
            scenario("tidal-channel-network", 128, 128),
            scenario("fjord-glacial-u", 160, 192),
            scenario("alpine-ridge-skeleton", 160, 160),
            scenario("volcanic-archipelago-skeleton", 257, 257));

    private static final Set<TerrainIntentV2.FeatureKind> SUPPORTED_KINDS = Set.of(
            TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
            TerrainIntentV2.FeatureKind.LAKE,
            TerrainIntentV2.FeatureKind.CANYON,
            TerrainIntentV2.FeatureKind.DELTA,
            TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK,
            TerrainIntentV2.FeatureKind.FJORD);

    private static final Set<String> SUPPORTED_HYDROLOGY_MODULES = Set.of(
            HydrologyIrModuleV2.MODULE_ID,
            HydrologyRiverModuleV2.MODULE_ID,
            HydrologyLakeModuleV2.MODULE_ID,
            LandformCanyonModuleV2.MODULE_ID,
            HydrologyDeltaModuleV2.MODULE_ID,
            HydrologyTidalModuleV2.MODULE_ID,
            LandformFjordModuleV2.MODULE_ID,
            HydrologyReconciliationModuleV2.MODULE_ID,
            HydrologyValidationPreviewModuleV2.MODULE_ID);

    @Test
    void completeOfflineKindsAndInfrastructureAreSupportedButDeferredKindsRemainExperimental() {
        BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();
        assertEquals(SUPPORTED_HYDROLOGY_MODULES, catalog.modules().stream()
                .filter(module -> module.lifecycleStatus() == ModuleDescriptorV2.LifecycleStatus.SUPPORTED)
                .map(ModuleDescriptorV2::moduleId)
                .filter(moduleId -> moduleId.startsWith("v2.hydrology.")
                        || moduleId.equals(LandformCanyonModuleV2.MODULE_ID)
                        || moduleId.equals(LandformFjordModuleV2.MODULE_ID))
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        for (TerrainIntentV2.FeatureKind kind : SUPPORTED_KINDS) {
            assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                    catalog.requireFor(kind).lifecycleStatus(), kind.name());
        }
        for (TerrainIntentV2.FeatureKind kind : Set.of(
                TerrainIntentV2.FeatureKind.WATERFALL,
                TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE,
                TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE,
                TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO)) {
            assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                    catalog.requireFor(kind).lifecycleStatus(), kind.name());
        }
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                catalog.hydrologyWaterfallModule().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                catalog.landformMountainModule().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                catalog.landformVolcanicModule().descriptor().lifecycleStatus());
        assertEquals(List.of(ReleaseArtifactCatalogV2.HYDROLOGY_PLAN,
                        ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE);
    }

    @Test
    void scenarioBlueprintsAreStableAcrossOrderThreadsLocaleAndTimezone() throws Exception {
        Map<Path, String> baseline = compile(SCENARIOS, 1);
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

    private static Map<Path, String> compile(List<Scenario> scenarios, int threads) throws Exception {
        List<Callable<Map.Entry<Path, String>>> tasks = scenarios.stream()
                .<Callable<Map.Entry<Path, String>>>map(scenario -> () -> Map.entry(
                        scenario.path(), compile(scenario)))
                .toList();
        Map<Path, String> result = new LinkedHashMap<>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (var future : executor.invokeAll(tasks)) {
                Map.Entry<Path, String> entry = future.get();
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static String compile(Scenario scenario) throws Exception {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        TerrainIntentV2 intent = codec.readTerrainIntent(scenario.path());
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                new DiagnosticCompileRequestV2(
                        intent.intentId(), new GenerationBounds(
                                scenario.width(), scenario.length(), -64, 255, 50), 64,
                        827413L, "a".repeat(64), DiagnosticCompileRequestV2.defaultBudget()),
                intent);
        for (TerrainIntentV2.Feature feature : intent.features()) {
            ModuleDescriptorV2.LifecycleStatus expected = SUPPORTED_KINDS.contains(feature.kind())
                    ? ModuleDescriptorV2.LifecycleStatus.SUPPORTED
                    : ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL;
            assertEquals(expected, new BuiltInLandformModuleCatalogV2()
                    .requireFor(feature.kind()).lifecycleStatus(), scenario.path() + " " + feature.kind());
        }
        return blueprint.canonicalChecksum();
    }

    private static Scenario scenario(String name, int width, int length) {
        return new Scenario(Path.of("examples/v2/hydrology/" + name + ".terrain-intent-v2.json"), width, length);
    }

    private record Scenario(Path path, int width, int length) {
    }
}
