package com.github.nankotsu029.landformcraft.integration.v2;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationEscarpmentPlateauSliceCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.scale.ScaleAdmissionExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.scale.ScaleAdmissionFailureCodeV2;
import com.github.nankotsu029.landformcraft.core.v2.scale.ScaleAdmissionV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformAbyssalPlainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformEscarpmentModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformGlacialIceModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformKarstSpringModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformLavaTubeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformMoraineFieldModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformOutwashPlainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformOxbowLakeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformPlateauModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSeamountModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSinkholeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSpringModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.escarpment.EscarpmentGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.plateau.PlateauGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-10-09 deferred terrain family lifecycle, integration, determinism, and resource evidence. */
class DeferredTerrainPhaseGateV2Test {

    /** The strict V2-10 sealed plan/contract portfolio; each entry reads and verifies its checksum. */
    private static final Map<String, PlanReader> PORTFOLIO = portfolio();

    /** Every FeatureKind whose deferred-family vertical slice was added by V2-10-01..08/10/11. */
    private static final List<TerrainIntentV2.FeatureKind> DEFERRED_KINDS = List.of(
            TerrainIntentV2.FeatureKind.VALLEY_GLACIER,
            TerrainIntentV2.FeatureKind.ICE_CAP,
            TerrainIntentV2.FeatureKind.ICE_SHEET,
            TerrainIntentV2.FeatureKind.MORAINE_FIELD,
            TerrainIntentV2.FeatureKind.OUTWASH_PLAIN,
            TerrainIntentV2.FeatureKind.SINKHOLE,
            TerrainIntentV2.FeatureKind.KARST_SPRING,
            TerrainIntentV2.FeatureKind.ABYSSAL_PLAIN,
            TerrainIntentV2.FeatureKind.SEAMOUNT,
            TerrainIntentV2.FeatureKind.ESCARPMENT,
            TerrainIntentV2.FeatureKind.PLATEAU,
            TerrainIntentV2.FeatureKind.LAVA_TUBE,
            TerrainIntentV2.FeatureKind.SPRING,
            TerrainIntentV2.FeatureKind.OXBOW_LAKE);

    /**
     * Profiles, presets, graph roles, and deferred candidates that V2-10 deliberately kept out of
     * the FeatureKind enum. The gate fails if any of them is silently promoted to a public kind.
     */
    private static final List<String> NON_KIND_NAMES = List.of(
            "ICE_FJORD",
            "PERMAFROST_PLAIN",
            "KARST_CAVE_SYSTEM",
            "CENOTE",
            "OCEAN_TRENCH",
            "MID_OCEAN_RIDGE",
            "SUBMARINE_VOLCANO",
            "RIVER_TERRACE",
            "ALLUVIAL_FAN",
            "ESTUARY",
            "BRAIDED_RIVER",
            "DAM_RESERVOIR",
            "MESA",
            "BUTTE",
            "BARRIER_ISLAND",
            "ATOLL",
            "FLOATING_REEF");

    private static final Path FOUNDATION = Path.of("examples/v2/foundation");
    private static final String PLAIN_CANONICAL_CHECKSUM =
            "1748e6ca5953465e06af039af50df9d1b48c44f0ee41b2724f2a4bc4da55af6b";
    private static final String OCEAN_BASIN_CANONICAL_CHECKSUM =
            "51b0acafa65953d09bacc3734e8ef18c5b43af29135f00999d2abe7cf5d0eb22";
    private static final String VOLCANIC_CONE_CANONICAL_CHECKSUM =
            "835388b9c778e95917891361fbe764e219204410fc2ab7b91f3b6cd045b8deae";
    private static final String SINGLE_ISLAND_CANONICAL_CHECKSUM =
            "a38974fcd83d8d3dd16366b1411c69fa94e41e35a6ed125df0301a4fb5982d73";
    private static final String RIVER_PLAN_CANONICAL_CHECKSUM =
            "269ca42102418401385f7d29fd554fe7cbd6bed9bf7628ced675238d629851fe";
    private static final String RIVER_GRAPH_ROLES_CANONICAL_CHECKSUM =
            "f461d8bca0a35e48b6de072eced663793ff2163f0cc9651681742890fd87469e";
    private static final String ADVANCED_RIVER_LAKE_SPLIT_CHECKSUM =
            "8c2adc7e0f9839e1ea1d85ba176db176757479a3ec422d997aba20840fa395e7";
    private static final String OPEN_SPILL_LAKE_SHA256 =
            "1b7615a159ad8afb6a5bcc0a1084d2c2f9600796095fa0d20efa650b03137948";

    private static LandformV2DataCodec codec() {
        return new LandformV2DataCodec();
    }

    @Test
    void deferredKindsStayDiagnosticWithoutFalsePromotion() {
        BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

        // V2-10 fixes offline plan-level capabilities only. Public intent dispatch stays on the
        // diagnostic module so DiagnosticBlueprintCompiler / WorldBlueprint checksums are unchanged,
        // and no deferred kind gains catalog validator/preview dispatch through this gate.
        for (TerrainIntentV2.FeatureKind kind : DEFERRED_KINDS) {
            ModuleDescriptorV2 module = catalog.requireFor(kind);
            assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID, module.moduleId(), kind.name());
            assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, module.lifecycleStatus(), kind.name());
            assertFalse(catalog.hasValidatorCapability(kind), kind.name());
            assertFalse(catalog.hasPreviewCapability(kind), kind.name());
        }

        Set<String> registeredModuleIds = catalog.modules().stream()
                .map(ModuleDescriptorV2::moduleId)
                .collect(Collectors.toSet());
        for (Map.Entry<String, DedicatedModule> module : dedicatedModules().entrySet()) {
            assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                    module.getValue().descriptor().get().lifecycleStatus(), module.getKey());
            assertFalse(registeredModuleIds.contains(module.getValue().moduleId()), module.getKey());
        }
    }

    @Test
    void profilesPresetsAndDeferredCandidatesAreNotFeatureKinds() {
        // ICE_FJORD / CENOTE / BARRIER_ISLAND / ATOLL are COMPOSITE_PRESETs, PERMAFROST_PLAIN and
        // MESA / BUTTE are profiles, KARST_CAVE_SYSTEM is a graph node role, and the remaining
        // names are deferred candidates without ownership evidence. None may appear as a kind.
        Set<String> kinds = Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        for (String name : NON_KIND_NAMES) {
            assertFalse(kinds.contains(name), name);
        }
    }

    @Test
    void releaseCapabilityCatalogIsUnchanged() {
        // V2-10 adds no Release 2 capability: deferred-family plans remain sealed-JSON artifacts
        // without a Release container path, and the canonical capability list is unchanged.
        assertEquals(List.of(
                        ReleaseArtifactCatalogV2.ENVIRONMENT_FIELDS,
                        ReleaseArtifactCatalogV2.HYDROLOGY_PLAN,
                        ReleaseArtifactCatalogV2.SPARSE_VOLUME,
                        ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT);
    }

    @Test
    void deferredExamplePortfolioIsStableAcrossOrderThreadsLocaleAndTimezone() throws Exception {
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
    void representativeEscarpmentPlateauScenarioRecompilesToIdenticalChecksums() throws Exception {
        TerrainIntentV2 intent = codec().readTerrainIntent(
                FOUNDATION.resolve("plateau-escarpment-slice.terrain-intent-v2.json"));
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(64, 48, 0, 256, 62);
        FoundationEscarpmentPlateauSliceCompilerV2 compiler = new FoundationEscarpmentPlateauSliceCompilerV2();

        var first = compiler.compile(intent, bounds, 20260718L);
        var second = compiler.compile(intent, bounds, 20260718L);
        assertEquals(first.escarpment().canonicalChecksum(), second.escarpment().canonicalChecksum());
        assertEquals(first.plateau().canonicalChecksum(), second.plateau().canonicalChecksum());
        assertEquals(first.exportChecksum(), second.exportChecksum());
        assertTrue(first.validation().metrics().transitionOk());
        assertTrue(first.validation().metrics().wholeTileOk());
        assertTrue(first.validation().metrics().exportOk());

        EscarpmentGeneratorV2 escarpment = new EscarpmentGeneratorV2(first.escarpment());
        PlateauGeneratorV2 plateau = new PlateauGeneratorV2(first.plateau());
        assertEquals(escarpment.exportChecksum(), escarpment.tileExportChecksum());
        assertEquals(plateau.exportChecksum(), plateau.tileExportChecksum());
    }

    @Test
    void protectedHostAndRegressionChecksumsAreUnchanged() throws Exception {
        LandformV2DataCodec codec = codec();
        assertEquals(PLAIN_CANONICAL_CHECKSUM,
                codec.readPlainPlan(FOUNDATION.resolve("plain-plan-v2.json")).canonicalChecksum());
        assertEquals(OCEAN_BASIN_CANONICAL_CHECKSUM,
                codec.readOceanBasinPlan(FOUNDATION.resolve("ocean-basin-plan-v2.json")).canonicalChecksum());
        assertEquals(VOLCANIC_CONE_CANONICAL_CHECKSUM,
                codec.readVolcanicConePlan(FOUNDATION.resolve("volcanic-cone-plan-v2.json"))
                        .canonicalChecksum());
        assertEquals(SINGLE_ISLAND_CANONICAL_CHECKSUM,
                codec.readSingleIslandPlan(FOUNDATION.resolve("single-island-plan-v2.json"))
                        .canonicalChecksum());
        assertEquals(RIVER_PLAN_CANONICAL_CHECKSUM,
                codec.readRiverPlan(FOUNDATION.resolve("river-plan-v2.json")).canonicalChecksum());
        assertEquals(RIVER_GRAPH_ROLES_CANONICAL_CHECKSUM,
                codec.readRiverPlan(FOUNDATION.resolve("river-graph-roles-plan-v2.json"))
                        .canonicalChecksum());
        assertEquals(ADVANCED_RIVER_LAKE_SPLIT_CHECKSUM,
                codec.readAdvancedRiverLakeSplitContract(
                        FOUNDATION.resolve("advanced-river-lake-split-contract-v2.json"))
                        .canonicalChecksum());
        assertEquals(OPEN_SPILL_LAKE_SHA256, sha256(Files.readAllBytes(
                Path.of("examples/v2/hydrology/open-spill-lake.terrain-intent-v2.json"))));
    }

    @Test
    void thousandSquareAdmissionHoldsAndOversizedProfilesAreRejected() {
        // The V2-8-01 ScaleProfile admission contract stays the only sizing gate: a 1000-square
        // deferred-family plan admits under its own class, and dimensions beyond the declared
        // profile are rejected before allocation. LARGE stays gated behind the V2-8 tasks.
        ScaleAdmissionV2.admit(1_000, 1_000,
                ScaleProfileV2.defaults(ScaleClassV2.forDimensions(1_000, 1_000)));
        ScaleAdmissionExceptionV2 rejected = assertThrows(
                ScaleAdmissionExceptionV2.class,
                () -> ScaleAdmissionV2.admit(600, 600, ScaleProfileV2.defaults(ScaleClassV2.SMALL)));
        assertEquals(ScaleAdmissionFailureCodeV2.SCALE_CLASS_EXCEEDED, rejected.failureCode());
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
        readers.put("glacial-ice", () ->
                codec().readGlacialIcePlan(FOUNDATION.resolve("glacial-ice-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("ice-fjord", () ->
                codec().readIceFjordPlan(FOUNDATION.resolve("ice-fjord-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("moraine-field", () ->
                codec().readMoraineFieldPlan(FOUNDATION.resolve("moraine-field-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("outwash-plain", () ->
                codec().readOutwashPlainPlan(FOUNDATION.resolve("outwash-plain-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("permafrost-plain-profile", () ->
                codec().readPermafrostPlainProfile(FOUNDATION.resolve("permafrost-plain-profile-v2.json"))
                        .canonicalChecksum());
        readers.put("karst-hydrology-graph", () ->
                codec().readKarstHydrologyGraphPlan(FOUNDATION.resolve("karst-hydrology-graph-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("sinkhole", () ->
                codec().readSinkholePlan(FOUNDATION.resolve("sinkhole-plan-v2.json")).canonicalChecksum());
        readers.put("karst-spring", () ->
                codec().readKarstSpringPlan(FOUNDATION.resolve("karst-spring-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("cenote", () ->
                codec().readCenotePlan(FOUNDATION.resolve("cenote-plan-v2.json")).canonicalChecksum());
        readers.put("abyssal-plain", () ->
                codec().readAbyssalPlainPlan(FOUNDATION.resolve("abyssal-plain-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("seamount", () ->
                codec().readSeamountPlan(FOUNDATION.resolve("seamount-plan-v2.json")).canonicalChecksum());
        readers.put("advanced-river-lake-split-contract", () ->
                codec().readAdvancedRiverLakeSplitContract(
                        FOUNDATION.resolve("advanced-river-lake-split-contract-v2.json"))
                        .canonicalChecksum());
        readers.put("escarpment", () ->
                codec().readEscarpmentPlan(FOUNDATION.resolve("escarpment-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("plateau", () ->
                codec().readPlateauPlan(FOUNDATION.resolve("plateau-plan-v2.json")).canonicalChecksum());
        readers.put("dry-land-modifier-contract", () ->
                codec().readDryLandModifierContract(FOUNDATION.resolve("dry-land-modifier-contract-v2.json"))
                        .canonicalChecksum());
        readers.put("lava-tube", () ->
                codec().readLavaTubePlan(FOUNDATION.resolve("lava-tube-plan-v2.json")).canonicalChecksum());
        readers.put("barrier-island", () ->
                codec().readBarrierIslandPlan(FOUNDATION.resolve("barrier-island-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("atoll", () ->
                codec().readAtollPlan(FOUNDATION.resolve("atoll-plan-v2.json")).canonicalChecksum());
        readers.put("advanced-island-reef-catalog-contract", () ->
                codec().readAdvancedIslandReefCatalogContract(
                        FOUNDATION.resolve("advanced-island-reef-catalog-contract-v2.json"))
                        .canonicalChecksum());
        readers.put("spring", () ->
                codec().readSpringPlan(FOUNDATION.resolve("spring-plan-v2.json")).canonicalChecksum());
        readers.put("oxbow-lake", () ->
                codec().readOxbowLakePlan(FOUNDATION.resolve("oxbow-lake-plan-v2.json"))
                        .canonicalChecksum());
        return Collections.unmodifiableMap(readers);
    }

    private static Map<String, DedicatedModule> dedicatedModules() {
        Map<String, DedicatedModule> modules = new LinkedHashMap<>();
        modules.put("glacial-ice", new DedicatedModule(
                LandformGlacialIceModuleV2.MODULE_ID, () -> new LandformGlacialIceModuleV2().descriptor()));
        modules.put("moraine-field", new DedicatedModule(
                LandformMoraineFieldModuleV2.MODULE_ID, () -> new LandformMoraineFieldModuleV2().descriptor()));
        modules.put("outwash-plain", new DedicatedModule(
                LandformOutwashPlainModuleV2.MODULE_ID, () -> new LandformOutwashPlainModuleV2().descriptor()));
        modules.put("sinkhole", new DedicatedModule(
                LandformSinkholeModuleV2.MODULE_ID, () -> new LandformSinkholeModuleV2().descriptor()));
        modules.put("karst-spring", new DedicatedModule(
                LandformKarstSpringModuleV2.MODULE_ID, () -> new LandformKarstSpringModuleV2().descriptor()));
        modules.put("abyssal-plain", new DedicatedModule(
                LandformAbyssalPlainModuleV2.MODULE_ID, () -> new LandformAbyssalPlainModuleV2().descriptor()));
        modules.put("seamount", new DedicatedModule(
                LandformSeamountModuleV2.MODULE_ID, () -> new LandformSeamountModuleV2().descriptor()));
        modules.put("escarpment", new DedicatedModule(
                LandformEscarpmentModuleV2.MODULE_ID, () -> new LandformEscarpmentModuleV2().descriptor()));
        modules.put("plateau", new DedicatedModule(
                LandformPlateauModuleV2.MODULE_ID, () -> new LandformPlateauModuleV2().descriptor()));
        modules.put("lava-tube", new DedicatedModule(
                LandformLavaTubeModuleV2.MODULE_ID, () -> new LandformLavaTubeModuleV2().descriptor()));
        modules.put("spring", new DedicatedModule(
                LandformSpringModuleV2.MODULE_ID, () -> new LandformSpringModuleV2().descriptor()));
        modules.put("oxbow-lake", new DedicatedModule(
                LandformOxbowLakeModuleV2.MODULE_ID, () -> new LandformOxbowLakeModuleV2().descriptor()));
        return Collections.unmodifiableMap(modules);
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private record DedicatedModule(String moduleId, Supplier<ModuleDescriptorV2> descriptor) {
    }

    @FunctionalInterface
    private interface PlanReader {
        String canonicalChecksum() throws Exception;
    }
}
