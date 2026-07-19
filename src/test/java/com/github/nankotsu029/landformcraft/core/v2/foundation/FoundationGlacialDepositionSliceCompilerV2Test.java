package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformMoraineFieldModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformOutwashPlainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.deposition.MoraineFieldGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.deposition.OutwashPlainGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MoraineFieldPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OutwashPlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PermafrostPlainProfileV2;
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

class FoundationGlacialDepositionSliceCompilerV2Test {
    private static final Path MORAINE =
            Path.of("examples/v2/foundation/moraine-field-positive.terrain-intent-v2.json");
    private static final Path OUTWASH =
            Path.of("examples/v2/foundation/outwash-plain-positive.terrain-intent-v2.json");
    private static final Path PERMAFROST =
            Path.of("examples/v2/foundation/permafrost-plain-profile.terrain-intent-v2.json");
    private static final Path VALLEY_GLACIER =
            Path.of("examples/v2/foundation/valley-glacier-positive.terrain-intent-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationGlacialDepositionSliceCompilerV2 compiler =
            new FoundationGlacialDepositionSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void moraineSliceProducesMetricsRoundTripAndWholeTileChecksum() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(MORAINE);
        var slice = compiler.compileMoraine(intent, bounds(64, 64), 1001L);
        assertTrue(slice.validation().metrics().glacialParentOk());
        assertTrue(slice.validation().metrics().sedimentOwnerOk());
        assertTrue(slice.validation().metrics().ridgeOrFlowOk());
        assertTrue(slice.validation().metrics().profileKindSeparated());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertTrue(slice.validation().metrics().exportOk());
        assertEquals("valley-ice", slice.moraine().glacialParentFeatureId());
        assertFalse(slice.moraine().glacialParentCanonicalChecksum().isBlank());

        Path planFile = Path.of("examples/v2/foundation/moraine-field-plan-v2.json");
        codec.writeMoraineFieldPlan(planFile, slice.moraine());
        assertEquals(slice.moraine(), codec.readMoraineFieldPlan(planFile));
        assertEquals(slice.exportChecksum(), new MoraineFieldGeneratorV2(slice.moraine()).exportChecksum());
    }

    @Test
    void outwashSliceProducesMetricsRoundTripAndWholeTileChecksum() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(OUTWASH);
        var slice = compiler.compileOutwash(intent, bounds(64, 64), 1002L);
        assertTrue(slice.validation().metrics().glacialParentOk());
        assertTrue(slice.validation().metrics().sedimentOwnerOk());
        assertTrue(slice.validation().metrics().ridgeOrFlowOk());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertTrue(slice.validation().metrics().exportOk());
        assertEquals("valley-ice", slice.outwash().glacialParentFeatureId());
        assertFalse(slice.outwash().meltwaterHandoffFeatureId().isBlank());

        Path planFile = Path.of("examples/v2/foundation/outwash-plain-plan-v2.json");
        codec.writeOutwashPlainPlan(planFile, slice.outwash());
        assertEquals(slice.outwash(), codec.readOutwashPlainPlan(planFile));
        assertEquals(slice.exportChecksum(), new OutwashPlainGeneratorV2(slice.outwash()).exportChecksum());
    }

    @Test
    void permafrostProfileCompilesWithoutPermafrostPlainFeatureKind() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(PERMAFROST);
        PermafrostPlainProfileV2 profile = compiler.compilePermafrostProfile(intent, bounds(64, 64), 1003L);
        assertEquals(PermafrostPlainProfileV2.CONTRACT_VERSION, profile.contractVersion());
        assertEquals("cold-plain", profile.plainFeatureId());
        assertTrue(profile.plainPlanChecksum().matches("^[0-9a-f]{64}$"));
        assertEquals("COLD_ALPINE", profile.climatePreset());

        Path profileFile = Path.of("examples/v2/foundation/permafrost-plain-profile-v2.json");
        codec.writePermafrostPlainProfile(profileFile, profile);
        assertEquals(profile, codec.readPermafrostPlainProfile(profileFile));
    }

    @Test
    void noPermafrostPlainFeatureKindIntroduced() {
        assertFalse(java.util.Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .anyMatch(kind -> kind.name().equals("PERMAFROST_PLAIN")));
    }

    @Test
    void missingGlacialParentIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(MORAINE);
        List<TerrainIntentV2.Relation> relations = base.relations().stream()
                .filter(relation -> !relation.id().equals("moraine-originates-at-ice"))
                .toList();
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), relations, base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compileMoraine(intent, bounds(64, 64), 1004L));
        assertEquals("v2.moraine-missing-glacial-parent", failure.ruleId());
    }

    @Test
    void warmClimateIsRejectedForPermafrost() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(PERMAFROST);
        TerrainIntentV2 intent = withEnvironmentClimate(base, "TEMPERATE_HUMID");
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compilePermafrostProfile(intent, bounds(64, 64), 1005L));
        assertEquals("v2.permafrost-warm-climate", failure.ruleId());
    }

    @Test
    void modulesRemainExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformMoraineFieldModuleV2().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformOutwashPlainModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.MORAINE_FIELD).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.OUTWASH_PLAIN).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.MORAINE_FIELD));
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.OUTWASH_PLAIN));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformMoraineFieldModuleV2.MODULE_ID.equals(module.moduleId())));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformOutwashPlainModuleV2.MODULE_ID.equals(module.moduleId())));
    }

    @Test
    void wholeTileThreadAndLocaleChecksumsAreStable() throws Exception {
        TerrainIntentV2 moraineIntent = codec.readTerrainIntent(MORAINE);
        var firstMoraine = compiler.compileMoraine(moraineIntent, bounds(64, 64), 1006L);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var secondMoraine = compiler.compileMoraine(moraineIntent, bounds(64, 64), 1006L);
            assertEquals(firstMoraine.moraine().canonicalChecksum(), secondMoraine.moraine().canonicalChecksum());
            assertEquals(firstMoraine.exportChecksum(), secondMoraine.exportChecksum());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        TerrainIntentV2 outwashIntent = codec.readTerrainIntent(OUTWASH);
        var firstOutwash = compiler.compileOutwash(outwashIntent, bounds(64, 64), 1007L);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                tasks.add(() -> compiler.compileOutwash(outwashIntent, bounds(64, 64), 1007L).exportChecksum());
            }
            List<Future<String>> futures = pool.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertEquals(firstOutwash.exportChecksum(), future.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parentGlacialIceFixtureStillLoads() throws Exception {
        assertFalse(codec.readTerrainIntent(VALLEY_GLACIER).features().isEmpty());
        assertEquals(MoraineFieldPlanV2.CONTRACT, "moraine-field-plan-contract-v1");
        assertEquals(OutwashPlainPlanV2.CONTRACT, "outwash-plain-plan-contract-v1");
    }

    @Test
    void sealedPlansStrictRoundTrip(@TempDir Path temp) throws Exception {
        var moraine = compiler.compileMoraine(codec.readTerrainIntent(MORAINE), bounds(64, 64), 1008L);
        Path moraineFile = temp.resolve("moraine-field-plan-v2.json");
        codec.writeMoraineFieldPlan(moraineFile, moraine.moraine());
        assertEquals(moraine.moraine(), codec.readMoraineFieldPlan(moraineFile));
        Files.copy(moraineFile, Path.of("examples/v2/foundation/moraine-field-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        var outwash = compiler.compileOutwash(codec.readTerrainIntent(OUTWASH), bounds(64, 64), 1009L);
        Path outwashFile = temp.resolve("outwash-plain-plan-v2.json");
        codec.writeOutwashPlainPlan(outwashFile, outwash.outwash());
        assertEquals(outwash.outwash(), codec.readOutwashPlainPlan(outwashFile));
        Files.copy(outwashFile, Path.of("examples/v2/foundation/outwash-plain-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        Path validation = temp.resolve("validation.json");
        codec.writeFoundationGlacialDepositionValidationArtifact(validation, moraine.validation());
        assertEquals(moraine.validation(), codec.readFoundationGlacialDepositionValidationArtifact(validation));
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
