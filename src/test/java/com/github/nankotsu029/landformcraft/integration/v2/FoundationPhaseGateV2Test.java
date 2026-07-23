package com.github.nankotsu029.landformcraft.integration.v2;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationPlainHillSliceCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationFailureCodeV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationPlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformArchipelagoModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformCaveEntranceModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformContinentalShelfModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformContinentalSlopeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformFloodplainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformHillRangeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformMarshModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformMountainRangeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformOceanBasinModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformPlainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformRockyCoastModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSeaCliffModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSingleIslandModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSubmarineCanyonModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformUndergroundRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformValleyModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformVolcanicConeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.hill.HillRangeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.plain.PlainGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-9-14 terrain foundation lifecycle, integration, determinism, and resource evidence. */
class FoundationPhaseGateV2Test {

    /** The strict foundation plan example portfolio; each entry reads and verifies its sealed checksum. */
    private static final Map<String, PlanReader> PORTFOLIO = portfolio();

    /** Every FeatureKind whose foundation vertical slice was added or completed by V2-9-01..13. */
    private static final List<TerrainIntentV2.FeatureKind> FOUNDATION_KINDS = List.of(
            TerrainIntentV2.FeatureKind.PLAIN,
            TerrainIntentV2.FeatureKind.HILL_RANGE,
            TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE,
            TerrainIntentV2.FeatureKind.VALLEY,
            // RIVER moved off the diagnostic module onto HydrologyRiverModuleV2 in V2-15-10 / ADR 0039
            // Candidate A (offline production route); it is no longer diagnostic-only, so it is removed
            // from this V2-9 diagnostic-stability list.
            TerrainIntentV2.FeatureKind.FLOODPLAIN,
            TerrainIntentV2.FeatureKind.MARSH,
            TerrainIntentV2.FeatureKind.ROCKY_COAST,
            TerrainIntentV2.FeatureKind.SEA_CLIFF,
            TerrainIntentV2.FeatureKind.SINGLE_ISLAND,
            TerrainIntentV2.FeatureKind.ARCHIPELAGO,
            TerrainIntentV2.FeatureKind.VOLCANIC_CONE,
            TerrainIntentV2.FeatureKind.OCEAN_BASIN,
            TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF,
            TerrainIntentV2.FeatureKind.CONTINENTAL_SLOPE,
            TerrainIntentV2.FeatureKind.SUBMARINE_CANYON,
            TerrainIntentV2.FeatureKind.CAVE_ENTRANCE,
            TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER,
            TerrainIntentV2.FeatureKind.FLOODED_CAVE);

    private static LandformV2DataCodec codec() {
        return new LandformV2DataCodec();
    }

    @Test
    void foundationKindsStayDiagnosticWithoutFalsePromotion() {
        BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

        // V2-9 fixes the offline plan-level capabilities only. Public intent dispatch stays on the
        // diagnostic module so DiagnosticBlueprintCompiler / WorldBlueprint checksums are unchanged,
        // and no foundation kind is promoted into catalog validator/preview dispatch by this gate.
        for (TerrainIntentV2.FeatureKind kind : FOUNDATION_KINDS) {
            ModuleDescriptorV2 module = catalog.requireFor(kind);
            assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID, module.moduleId(), kind.name());
            assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, module.lifecycleStatus(), kind.name());
            assertFalse(catalog.hasValidatorCapability(kind), kind.name());
            assertFalse(catalog.hasPreviewCapability(kind), kind.name());
        }

        for (Map.Entry<String, Supplier<ModuleDescriptorV2>> module : dedicatedModules().entrySet()) {
            assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                    module.getValue().get().lifecycleStatus(), module.getKey());
        }

        // V2-9 adds no Release 2 capability: the canonical capability list is unchanged, so the
        // foundation plans remain sealed-JSON plan artifacts without a Release container path.
        assertEquals(List.of(
                        ReleaseArtifactCatalogV2.ENVIRONMENT_FIELDS,
                        ReleaseArtifactCatalogV2.HYDROLOGY_PLAN,
                        ReleaseArtifactCatalogV2.SPARSE_VOLUME,
                        ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT);
    }

    @Test
    void foundationExamplePortfolioIsStableAcrossOrderThreadsLocaleAndTimezone() throws Exception {
        Map<String, String> baseline = read(List.copyOf(PORTFOLIO.keySet()), 1);
        List<String> reversed = new ArrayList<>(PORTFOLIO.keySet());
        Collections.reverse(reversed);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(baseline, read(reversed, 4));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void representativePlainHillScenarioRecompilesToIdenticalWholeAndTileChecksums() throws Exception {
        TerrainIntentV2 intent = codec().readTerrainIntent(
                Path.of("examples/v2/foundation/plain-hill-slice.terrain-intent-v2.json"));
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(64, 48, 0, 256, 62);
        FoundationPlainHillSliceCompilerV2 compiler = new FoundationPlainHillSliceCompilerV2();

        FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 first =
                compiler.compile(intent, bounds, 4242L);
        FoundationPlainHillSliceCompilerV2.FoundationPlainHillSliceV2 second =
                compiler.compile(intent, bounds, 4242L);
        assertNotNull(first.plain());
        assertNotNull(first.hill());
        assertEquals(first.plain().canonicalChecksum(), second.plain().canonicalChecksum());
        assertEquals(first.hill().canonicalChecksum(), second.hill().canonicalChecksum());
        assertTrue(first.validation().metrics().plainHillTransitionOk());

        SurfaceFoundationPlanV2 plan = first.foundation();
        SurfaceFoundationMergeCompilerV2 merge = new SurfaceFoundationMergeCompilerV2(plan, List.of(
                new PlainGeneratorV2(first.plain()).toOwnerLayer(plan.owners().get(0)),
                new HillRangeGeneratorV2(first.hill()).toOwnerLayer(plan.owners().get(1))));
        TilePlanV2 tiles = TilePlanV2.of(64, 48, ScaleProfileV2.defaults(ScaleClassV2.SMALL));
        assertEquals(merge.wholeFieldChecksums(), merge.tiledFieldChecksums(tiles));
    }

    @Test
    void thousandSquareFoundationMergeIsAdmittedBoundedAndDeterministic() {
        SurfaceFoundationPlanCompilerV2 compiler = new SurfaceFoundationPlanCompilerV2();

        // The 1000-square merge must pass ScaleProfile admission (V2-8-01 contract) and produce
        // identical field checksums for the whole pass and the canonical row-major tile pass.
        ScaleProfileV2 profile = ScaleProfileV2.defaults(ScaleClassV2.forDimensions(1_000, 1_000));
        SurfaceFoundationPlanV2 plan = compiler.compile(
                1_000, 1_000, 20260718L,
                List.of(
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "gate-plain", 1, 10, 0,
                                SurfaceFoundationPlanV2.SurfaceClassCode.PLAIN),
                        new SurfaceFoundationPlanCompilerV2.OwnerSpec(
                                "gate-hills", 2, 20, 1,
                                SurfaceFoundationPlanV2.SurfaceClassCode.HILL)),
                List.of(new SurfaceFoundationPlanCompilerV2.InteractionSpec(
                        "gate-blend", "gate-plain", "gate-hills", 8)),
                profile);
        SurfaceFoundationMergeCompilerV2 merge = new SurfaceFoundationMergeCompilerV2(plan, List.of(
                rectLayer(plan.owners().get(0), 0, 0, 620, 1_000, 1_000_000),
                rectLayer(plan.owners().get(1), 600, 0, 400, 1_000, 2_000_000)));
        assertEquals(merge.wholeFieldChecksums(),
                merge.tiledFieldChecksums(TilePlanV2.of(1_000, 1_000, profile)));

        // Dimensions beyond the declared profile are rejected up front instead of degrading;
        // LARGE stays gated behind the V2-8 budget tasks and is not silently enabled here.
        SurfaceFoundationExceptionV2 rejected = assertThrows(
                SurfaceFoundationExceptionV2.class,
                () -> compiler.compile(600, 600, 1L, List.of(), List.of(),
                        ScaleProfileV2.defaults(ScaleClassV2.SMALL)));
        assertEquals(SurfaceFoundationFailureCodeV2.SCALE_ADMISSION_REJECTED, rejected.failureCode());
    }

    private static Map<String, String> read(List<String> names, int threads) throws Exception {
        List<Callable<Map.Entry<String, String>>> tasks = names.stream()
                .<Callable<Map.Entry<String, String>>>map(name -> () ->
                        Map.entry(name, PORTFOLIO.get(name).canonicalChecksum()))
                .toList();
        Map<String, String> result = new LinkedHashMap<>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (var future : executor.invokeAll(tasks)) {
                Map.Entry<String, String> entry = future.get();
                result.put(entry.getKey(), entry.getValue());
            }
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String name : PORTFOLIO.keySet()) {
            ordered.put(name, result.get(name));
        }
        return ordered;
    }

    private static Map<String, PlanReader> portfolio() {
        Map<String, PlanReader> readers = new LinkedHashMap<>();
        Path foundation = Path.of("examples/v2/foundation");
        readers.put("surface-foundation", () ->
                codec().readSurfaceFoundationPlan(foundation.resolve("surface-foundation-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("plain", () ->
                codec().readPlainPlan(foundation.resolve("plain-plan-v2.json")).canonicalChecksum());
        readers.put("hill-range", () ->
                codec().readHillRangePlan(foundation.resolve("hill-range-plan-v2.json")).canonicalChecksum());
        readers.put("mountain-range", () ->
                codec().readMountainRangePlan(foundation.resolve("mountain-range-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("valley", () ->
                codec().readValleyPlan(foundation.resolve("valley-plan-v2.json")).canonicalChecksum());
        readers.put("river", () ->
                codec().readRiverPlan(foundation.resolve("river-plan-v2.json")).canonicalChecksum());
        readers.put("river-graph-roles", () ->
                codec().readRiverPlan(foundation.resolve("river-graph-roles-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("waterfall-chain", () ->
                codec().readWaterfallChainPlan(foundation.resolve("waterfall-chain-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("floodplain", () ->
                codec().readFloodplainPlan(foundation.resolve("floodplain-plan-v2.json")).canonicalChecksum());
        readers.put("marsh", () ->
                codec().readMarshPlan(foundation.resolve("marsh-plan-v2.json")).canonicalChecksum());
        readers.put("rocky-coast", () ->
                codec().readRockyCoastPlan(foundation.resolve("rocky-coast-plan-v2.json")).canonicalChecksum());
        readers.put("sea-cliff", () ->
                codec().readSeaCliffPlan(foundation.resolve("sea-cliff-plan-v2.json")).canonicalChecksum());
        readers.put("single-island", () ->
                codec().readSingleIslandPlan(foundation.resolve("single-island-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("archipelago", () ->
                codec().readArchipelagoPlan(foundation.resolve("archipelago-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("volcanic-cone", () ->
                codec().readVolcanicConePlan(foundation.resolve("volcanic-cone-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("ocean-basin", () ->
                codec().readOceanBasinPlan(foundation.resolve("ocean-basin-plan-v2.json")).canonicalChecksum());
        readers.put("continental-shelf", () ->
                codec().readContinentalShelfPlan(foundation.resolve("continental-shelf-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("continental-slope", () ->
                codec().readContinentalSlopePlan(foundation.resolve("continental-slope-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("submarine-canyon", () ->
                codec().readSubmarineCanyonPlan(foundation.resolve("submarine-canyon-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("cave-entrance", () ->
                codec().readCaveEntrancePlan(foundation.resolve("cave-entrance-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("underground-river", () ->
                codec().readUndergroundRiverPlan(foundation.resolve("underground-river-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("macro-land-water-topology", () ->
                codec().readMacroLandWaterTopologyPlan(
                        foundation.resolve("macro-land-water-topology-plan-v2.json"))
                        .canonicalChecksum());
        return Collections.unmodifiableMap(readers);
    }

    private static Map<String, Supplier<ModuleDescriptorV2>> dedicatedModules() {
        Map<String, Supplier<ModuleDescriptorV2>> modules = new LinkedHashMap<>();
        modules.put("plain", () -> new LandformPlainModuleV2().descriptor());
        modules.put("hill-range", () -> new LandformHillRangeModuleV2().descriptor());
        modules.put("mountain-range", () -> new LandformMountainRangeModuleV2().descriptor());
        modules.put("valley", () -> new LandformValleyModuleV2().descriptor());
        modules.put("river", () -> new LandformRiverModuleV2().descriptor());
        modules.put("floodplain", () -> new LandformFloodplainModuleV2().descriptor());
        modules.put("marsh", () -> new LandformMarshModuleV2().descriptor());
        modules.put("rocky-coast", () -> new LandformRockyCoastModuleV2().descriptor());
        modules.put("sea-cliff", () -> new LandformSeaCliffModuleV2().descriptor());
        modules.put("single-island", () -> new LandformSingleIslandModuleV2().descriptor());
        modules.put("archipelago", () -> new LandformArchipelagoModuleV2().descriptor());
        modules.put("volcanic-cone", () -> new LandformVolcanicConeModuleV2().descriptor());
        modules.put("ocean-basin", () -> new LandformOceanBasinModuleV2().descriptor());
        modules.put("continental-shelf", () -> new LandformContinentalShelfModuleV2().descriptor());
        modules.put("continental-slope", () -> new LandformContinentalSlopeModuleV2().descriptor());
        modules.put("submarine-canyon", () -> new LandformSubmarineCanyonModuleV2().descriptor());
        modules.put("cave-entrance", () -> new LandformCaveEntranceModuleV2().descriptor());
        modules.put("underground-river", () -> new LandformUndergroundRiverModuleV2().descriptor());
        return Collections.unmodifiableMap(modules);
    }

    private static SurfaceFoundationMergeCompilerV2.OwnerLayer rectLayer(
            SurfaceFoundationPlanV2.OwnerDescriptor owner,
            int minX,
            int minZ,
            int width,
            int length,
            int elevation
    ) {
        return new SurfaceFoundationMergeCompilerV2.OwnerLayer(
                owner,
                packed -> {
                    long value = packed;
                    int x = (int) value;
                    int z = (int) (value >>> 32);
                    return x >= minX && z >= minZ && x < minX + width && z < minZ + length;
                },
                (x, z) -> elevation);
    }

    @FunctionalInterface
    private interface PlanReader {
        String canonicalChecksum() throws Exception;
    }
}
