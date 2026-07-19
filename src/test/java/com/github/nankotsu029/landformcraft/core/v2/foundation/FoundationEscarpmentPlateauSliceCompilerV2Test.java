package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformEscarpmentModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformPlateauModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.escarpment.EscarpmentGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.plateau.PlateauGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.DryLandModifierContractV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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

class FoundationEscarpmentPlateauSliceCompilerV2Test {
    private static final Path SLICE =
            Path.of("examples/v2/foundation/plateau-escarpment-slice.terrain-intent-v2.json");
    private static final Path PLAIN_PLAN = Path.of("examples/v2/foundation/plain-plan-v2.json");
    private static final String PLAIN_CANONICAL_CHECKSUM =
            "1748e6ca5953465e06af039af50df9d1b48c44f0ee41b2724f2a4bc4da55af6b";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationEscarpmentPlateauSliceCompilerV2 compiler =
            new FoundationEscarpmentPlateauSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void sliceProducesMetricsRoundTripAndExport(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SLICE);
        var slice = compiler.compile(intent, bounds(64, 48), 3001L);
        assertTrue(slice.validation().metrics().longScarpOwnerOk());
        assertTrue(slice.validation().metrics().capFloorTalusOk());
        assertTrue(slice.validation().metrics().transitionOk());
        assertTrue(slice.validation().metrics().materialHandoffOk());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertTrue(slice.validation().metrics().exportOk());
        assertTrue(slice.validation().metrics().dryLandModifiersSeparated());

        Path escarpmentFile = temp.resolve("escarpment-plan-v2.json");
        Path plateauFile = temp.resolve("plateau-plan-v2.json");
        Path contractFile = temp.resolve("dry-land-modifier-contract-v2.json");
        codec.writeEscarpmentPlan(escarpmentFile, slice.escarpment());
        codec.writePlateauPlan(plateauFile, slice.plateau());
        codec.writeDryLandModifierContract(contractFile, slice.dryLandContract());
        assertEquals(slice.escarpment(), codec.readEscarpmentPlan(escarpmentFile));
        assertEquals(slice.plateau(), codec.readPlateauPlan(plateauFile));
        assertEquals(slice.dryLandContract(), codec.readDryLandModifierContract(contractFile));

        codec.writeEscarpmentPlan(Path.of("examples/v2/foundation/escarpment-plan-v2.json"), slice.escarpment());
        codec.writePlateauPlan(Path.of("examples/v2/foundation/plateau-plan-v2.json"), slice.plateau());
        codec.writeDryLandModifierContract(
                Path.of("examples/v2/foundation/dry-land-modifier-contract-v2.json"), slice.dryLandContract());

        String expected = digestPair(
                new EscarpmentGeneratorV2(slice.escarpment()).exportChecksum(),
                new PlateauGeneratorV2(slice.plateau()).exportChecksum());
        assertEquals(expected, slice.exportChecksum());
    }

    @Test
    void featureKindClassificationMatchesTaskScope() {
        List<String> kinds = Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .map(Enum::name)
                .toList();
        assertTrue(kinds.contains("ESCARPMENT"));
        assertTrue(kinds.contains("PLATEAU"));
        assertFalse(kinds.contains("MESA"));
        assertFalse(kinds.contains("BUTTE"));
        assertFalse(kinds.contains("DUNE"));
        assertFalse(kinds.contains("BADLANDS"));
        assertFalse(kinds.contains("SALT_FLAT"));
        assertFalse(kinds.contains("DESERT_FLAT"));
        assertFalse(kinds.contains("COASTAL_DUNE_FIELD"));
        assertFalse(kinds.contains("INLAND_DUNE_FIELD"));
        assertFalse(kinds.contains("DRY_CANYON"));
    }

    @Test
    void missingTransitionIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(SLICE);
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), List.of(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 48), 3002L));
        assertEquals("v2.escarpment-plateau-missing-transition", failure.ruleId());
    }

    @Test
    void shortEscarpmentIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(SLICE);
        List<TerrainIntentV2.Feature> features = new ArrayList<>(base.features());
        TerrainIntentV2.Feature escarpment = features.stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.ESCARPMENT)
                .findFirst()
                .orElseThrow();
        features.set(features.indexOf(escarpment), new TerrainIntentV2.Feature(
                escarpment.id(),
                escarpment.kind(),
                new TerrainIntentV2.SplineGeometry(
                        List.of(new TerrainIntentV2.Point2(400_000, 800_000),
                                new TerrainIntentV2.Point2(410_000, 800_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                escarpment.parameters(),
                escarpment.priority(),
                escarpment.provenance()));
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                List.copyOf(features), base.relations(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 48), 3003L));
        assertEquals("v2.escarpment-too-short", failure.ruleId());
    }

    @Test
    void dryLandContractSealsAndListsModifiersWithoutFeatureKinds() {
        DryLandModifierContractV2 contract = compiler.compileDryLandModifiers();
        assertEquals(DryLandModifierContractV2.REQUIRED_MODIFIER_KINDS.size(), contract.modifiers().size());
        for (DryLandModifierContractV2.Modifier modifier : contract.modifiers()) {
            assertFalse(DryLandModifierContractV2.isForbiddenFeatureKindName(modifier.kind()));
            assertEquals(DryLandModifierContractV2.Disposition.MODIFIER_OR_FIELD, modifier.disposition());
        }
        assertEquals(DryLandModifierContractV2.DECISION_ID, contract.decisionId());
    }

    @Test
    void modulesRemainExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformEscarpmentModuleV2().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformPlateauModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.ESCARPMENT).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.PLATEAU).moduleId());
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformEscarpmentModuleV2.MODULE_ID.equals(module.moduleId())));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformPlateauModuleV2.MODULE_ID.equals(module.moduleId())));
    }

    @Test
    void wholeTileThreadAndLocaleChecksumsAreStable() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(SLICE);
        var first = compiler.compile(intent, bounds(64, 48), 3004L);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var second = compiler.compile(intent, bounds(64, 48), 3004L);
            assertEquals(first.escarpment().canonicalChecksum(), second.escarpment().canonicalChecksum());
            assertEquals(first.plateau().canonicalChecksum(), second.plateau().canonicalChecksum());
            assertEquals(first.exportChecksum(), second.exportChecksum());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                tasks.add(() -> compiler.compile(intent, bounds(64, 48), 3004L).exportChecksum());
            }
            List<Future<String>> futures = pool.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertEquals(first.exportChecksum(), future.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void plainRegressionPlanRemainsUnchanged() throws Exception {
        PlainPlanV2 plain = codec.readPlainPlan(PLAIN_PLAN);
        assertEquals(PLAIN_CANONICAL_CHECKSUM, plain.canonicalChecksum());
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }

    private static String digestPair(String left, String right) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(left.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(right.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
