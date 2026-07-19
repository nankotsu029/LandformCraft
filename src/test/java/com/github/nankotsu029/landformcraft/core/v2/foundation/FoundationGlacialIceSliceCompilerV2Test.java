package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformGlacialIceModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.glacial.GlacialIceGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.GlacialIcePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.IceFjordPlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationGlacialIceSliceCompilerV2Test {
    private static final Path VALLEY_GLACIER =
            Path.of("examples/v2/foundation/valley-glacier-positive.terrain-intent-v2.json");
    private static final Path ICE_CAP =
            Path.of("examples/v2/foundation/ice-cap-positive.terrain-intent-v2.json");
    private static final Path ICE_SHEET =
            Path.of("examples/v2/foundation/ice-sheet-positive.terrain-intent-v2.json");
    private static final Path ICE_FJORD =
            Path.of("examples/v2/foundation/ice-fjord-composition.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationGlacialIceSliceCompilerV2 compiler = new FoundationGlacialIceSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void valleyGlacierSliceProducesMetricsRoundTripAndWholeTileChecksum() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(VALLEY_GLACIER);
        var slice = compiler.compile(intent, bounds(64, 64), 1001L, TerrainIntentV2.FeatureKind.VALLEY_GLACIER);
        assertEquals(GlacialIcePlanV2.IceKind.VALLEY_GLACIER, slice.ice().iceKind());
        assertTrue(slice.validation().metrics().coldClimateOk());
        assertTrue(slice.validation().metrics().bedContactOk());
        assertTrue(slice.validation().metrics().flowTerminusOk());
        assertTrue(slice.validation().metrics().sparseIceOk());
        assertTrue(slice.validation().metrics().meltwaterHandoffOk());
        assertTrue(slice.validation().metrics().leakFree());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertTrue(slice.validation().metrics().exportOk());
        assertFalse(slice.ice().meltwaterHandoffFeatureId().isBlank());

        Path planFile = Path.of("examples/v2/foundation/glacial-ice-plan-v2.json");
        codec.writeGlacialIcePlan(planFile, slice.ice());
        assertEquals(slice.ice(), codec.readGlacialIcePlan(planFile));
        assertEquals(slice.exportChecksum(), new GlacialIceGeneratorV2(slice.ice()).exportChecksum());
    }

    @Test
    void iceCapAndIceSheetSlicesCompileOnCommonContract(@TempDir Path temp) throws Exception {
        var cap = compiler.compile(codec.readTerrainIntent(ICE_CAP), bounds(64, 64), 2L,
                TerrainIntentV2.FeatureKind.ICE_CAP);
        var sheet = compiler.compile(codec.readTerrainIntent(ICE_SHEET), bounds(64, 64), 3L,
                TerrainIntentV2.FeatureKind.ICE_SHEET);
        assertEquals(GlacialIcePlanV2.IceKind.ICE_CAP, cap.ice().iceKind());
        assertEquals(GlacialIcePlanV2.IceKind.ICE_SHEET, sheet.ice().iceKind());
        assertEquals(GlacialIcePlanV2.CONTRACT, "glacial-ice-plan-contract-v1");
        assertEquals(GlacialIcePlanV2.DEFAULT_SNOW_PROFILE_ID, cap.ice().snowProfileId());
        assertEquals(GlacialIcePlanV2.DEFAULT_SNOW_PROFILE_ID, sheet.ice().snowProfileId());

        codec.writeGlacialIcePlan(temp.resolve("cap.json"), cap.ice());
        codec.writeGlacialIcePlan(temp.resolve("sheet.json"), sheet.ice());
        assertEquals(cap.ice(), codec.readGlacialIcePlan(temp.resolve("cap.json")));
        assertEquals(sheet.ice(), codec.readGlacialIcePlan(temp.resolve("sheet.json")));
    }

    @Test
    void iceFjordPresetComposesFjordValleyGlacierAndColdSnow() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(ICE_FJORD);
        var composition = compiler.compileIceFjord(intent, bounds(64, 64), 4L);
        IceFjordPlanV2 preset = composition.iceFjord();
        assertEquals(IceFjordPlanV2.CONTRACT_VERSION, preset.contractVersion());
        assertEquals("marine-fjord", preset.fjordFeatureId());
        assertEquals(composition.valleyGlacier().ice().featureId(), preset.valleyGlacierFeatureId());
        assertEquals(composition.valleyGlacier().ice().canonicalChecksum(),
                preset.valleyGlacierPlanChecksum());
        assertTrue(GlacialIcePlanV2.COLD_CLIMATE_PRESETS.contains(preset.climatePreset()));
        assertEquals(IceFjordPlanV2.DEFAULT_SNOW_PROFILE_ID, preset.snowProfileId());

        Path planFile = Path.of("examples/v2/foundation/ice-fjord-plan-v2.json");
        codec.writeIceFjordPlan(planFile, preset);
        assertEquals(preset, codec.readIceFjordPlan(planFile));
    }

    @Test
    void noIceFjordFeatureKindIntroduced() {
        assertFalse(java.util.Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .anyMatch(kind -> kind.name().equals("ICE_FJORD")));
    }

    @Test
    void moduleRemainsExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformGlacialIceModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.VALLEY_GLACIER).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.ICE_CAP).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.ICE_SHEET).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.VALLEY_GLACIER));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformGlacialIceModuleV2.MODULE_ID.equals(module.moduleId())));
    }

    @Test
    void warmClimateIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(VALLEY_GLACIER);
        TerrainIntentV2 intent = withEnvironmentClimate(base, "TEMPERATE_HUMID");
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 64), 5L, TerrainIntentV2.FeatureKind.VALLEY_GLACIER));
        assertEquals("v2.glacial-ice-warm-climate", failure.ruleId());
    }

    @Test
    void unsupportedBedHostIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(VALLEY_GLACIER);
        List<TerrainIntentV2.Relation> relations = base.relations().stream()
                .filter(relation -> !relation.id().equals("ice-supported-by-valley"))
                .toList();
        List<TerrainIntentV2.Relation> mutated = new ArrayList<>(relations);
        mutated.add(new TerrainIntentV2.Relation(
                "ice-supported-by-river",
                TerrainIntentV2.RelationKind.SUPPORTED_BY,
                "feature:valley-ice",
                "feature:melt-outwash",
                TerrainIntentV2.Strength.HARD));
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), mutated, base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 64), 6L, TerrainIntentV2.FeatureKind.VALLEY_GLACIER));
        assertEquals("v2.glacial-ice-missing-bed", failure.ruleId());
    }

    @Test
    void meltwaterHintWithoutRelationIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(VALLEY_GLACIER);
        List<TerrainIntentV2.Relation> relations = base.relations().stream()
                .filter(relation -> !relation.id().equals("ice-drains-to-river"))
                .toList();
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), relations, base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 64), 7L, TerrainIntentV2.FeatureKind.VALLEY_GLACIER));
        assertEquals("v2.glacial-ice-meltwater-missing", failure.ruleId());
    }

    @Test
    void wholeTileThreadAndLocaleChecksumsAreStable() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(VALLEY_GLACIER);
        var first = compiler.compile(intent, bounds(64, 64), 8L, TerrainIntentV2.FeatureKind.VALLEY_GLACIER);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var second = compiler.compile(intent, bounds(64, 64), 8L, TerrainIntentV2.FeatureKind.VALLEY_GLACIER);
            assertEquals(first.ice().canonicalChecksum(), second.ice().canonicalChecksum());
            assertEquals(first.exportChecksum(), second.exportChecksum());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = List.of(
                    () -> compiler.compile(intent, bounds(64, 64), 8L,
                            TerrainIntentV2.FeatureKind.VALLEY_GLACIER).exportChecksum(),
                    () -> compiler.compile(intent, bounds(64, 64), 8L,
                            TerrainIntentV2.FeatureKind.VALLEY_GLACIER).exportChecksum(),
                    () -> compiler.compile(intent, bounds(64, 64), 8L,
                            TerrainIntentV2.FeatureKind.VALLEY_GLACIER).exportChecksum(),
                    () -> compiler.compile(intent, bounds(64, 64), 8L,
                            TerrainIntentV2.FeatureKind.VALLEY_GLACIER).exportChecksum());
            List<Future<String>> futures = pool.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertEquals(first.exportChecksum(), future.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sealedPlanStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(VALLEY_GLACIER);
        var slice = compiler.compile(intent, bounds(64, 64), 9L, TerrainIntentV2.FeatureKind.VALLEY_GLACIER);
        Path planFile = temp.resolve("glacial-ice-plan-v2.json");
        codec.writeGlacialIcePlan(planFile, slice.ice());
        assertEquals(slice.ice(), codec.readGlacialIcePlan(planFile));
        Files.copy(planFile, Path.of("examples/v2/foundation/glacial-ice-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
        Path validation = temp.resolve("validation.json");
        codec.writeFoundationGlacialIceValidationArtifact(validation, slice.validation());
        assertEquals(slice.validation(), codec.readFoundationGlacialIceValidationArtifact(validation));
    }

    private static TerrainIntentV2 withEnvironmentClimate(TerrainIntentV2 base, String climate) {
        return new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), base.relations(), base.constraints(),
                new TerrainIntentV2.EnvironmentDescriptor(
                        base.environment().geologyPreset(), climate, base.environment().ecologyPreset()),
                base.mapReferences(), base.structures(), base.provenance());
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }
}
