package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformKarstSpringModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSinkholeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.karst.KarstHydrologyGraphGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstHydrologyGraphPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SinkholePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationKarstHydrologySliceCompilerV2Test {
    private static final Path POSITIVE =
            Path.of("examples/v2/foundation/karst-hydrology-positive.terrain-intent-v2.json");
    private static final Path HOST_CAVE =
            Path.of("examples/v2/volume/cave-network-plan-v2.json");

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationKarstHydrologySliceCompilerV2 compiler =
            new FoundationKarstHydrologySliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void positiveSliceProducesMetricsRoundTripAndExportChecksum() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        CaveNetworkPlanV2 hostCave = codec.readCaveNetworkPlan(HOST_CAVE);
        var slice = compiler.compile(intent, bounds(64, 64), 2001L, hostCave);

        assertTrue(slice.validation().metrics().drainageReachable());
        assertTrue(slice.validation().metrics().lossSpringBalanced());
        assertTrue(slice.validation().metrics().collapseRoofOk());
        assertTrue(slice.validation().metrics().fluidOwnerOk());
        assertTrue(slice.validation().metrics().graphAcyclic());
        assertTrue(slice.validation().metrics().csgBudgetOk());
        assertTrue(slice.validation().metrics().leakFree());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertTrue(slice.validation().metrics().exportOk());
        assertEquals("cave.fixture-network", slice.hostCave().featureId());
        assertEquals(120, slice.sinkhole().lossVolumeBlocks());
        assertEquals(120, slice.spring().springDischargeBlocks());
        assertNotNull(slice.cenote());
        assertEquals("fluid.karst-cenote", slice.cenote().fluidBodyId());

        assertEquals(slice.exportChecksum(), new KarstHydrologyGraphGeneratorV2(
                slice.graph(), slice.sinkhole(), slice.spring(), java.util.Optional.of(slice.cenote()))
                .exportChecksum());
    }

    @Test
    void noKarstCaveSystemOrCenoteFeatureKindIntroduced() {
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            assertFalse(kind.name().equals("KARST_CAVE_SYSTEM"), "must not add KARST_CAVE_SYSTEM FeatureKind");
            assertFalse(kind.name().equals("CENOTE"), "must not add CENOTE FeatureKind");
        }
    }

    @Test
    void orphanSinkholeMissingCaveRelationIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        List<TerrainIntentV2.Relation> relations = base.relations().stream()
                .filter(relation -> !relation.id().equals("sinkhole-entrance-of-cave"))
                .toList();
        TerrainIntentV2 intent = copyIntent(base, relations);
        CaveNetworkPlanV2 hostCave = codec.readCaveNetworkPlan(HOST_CAVE);
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 64), 2002L, hostCave));
        assertEquals("v2.sinkhole-missing-cave", failure.ruleId());
    }

    @Test
    void graphCycleFromMutualUpstreamIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        List<TerrainIntentV2.Relation> relations = base.relations().stream()
                .filter(relation -> !relation.id().equals("sinkhole-upstream-of-spring"))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        relations.add(new TerrainIntentV2.Relation(
                "spring-empties-south",
                TerrainIntentV2.RelationKind.EMPTIES_INTO,
                "feature:karst-spring",
                "boundary:SOUTH",
                TerrainIntentV2.Strength.HARD));
        relations.add(new TerrainIntentV2.Relation(
                "spring-upstream-of-sinkhole",
                TerrainIntentV2.RelationKind.UPSTREAM_OF,
                "feature:karst-spring",
                "feature:karst-sinkhole",
                TerrainIntentV2.Strength.HARD));
        TerrainIntentV2 intent = copyIntent(base, relations);
        CaveNetworkPlanV2 hostCave = codec.readCaveNetworkPlan(HOST_CAVE);
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 64), 2003L, hostCave));
        assertEquals("v2.karst-graph-cycle", failure.ruleId());
    }

    @Test
    void lossSpringImbalanceIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        List<TerrainIntentV2.Feature> features = new ArrayList<>();
        for (TerrainIntentV2.Feature feature : base.features()) {
            if (feature.kind() == TerrainIntentV2.FeatureKind.KARST_SPRING
                    && feature.parameters() instanceof TerrainIntentV2.KarstSpringParameters params) {
                features.add(new TerrainIntentV2.Feature(
                        feature.id(), feature.kind(), feature.geometry(),
                        new TerrainIntentV2.KarstSpringParameters(
                                new TerrainIntentV2.IntRange(999, 999), params.outletHint()),
                        feature.priority(), feature.provenance()));
            } else {
                features.add(feature);
            }
        }
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                features, base.relations(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        CaveNetworkPlanV2 hostCave = codec.readCaveNetworkPlan(HOST_CAVE);
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 64), 2004L, hostCave));
        assertEquals("v2.karst-loss-spring-imbalance", failure.ruleId());
    }

    @Test
    void nonLimestoneGeologyIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = withGeology(base, "GRANITE_SHIELD");
        CaveNetworkPlanV2 hostCave = codec.readCaveNetworkPlan(HOST_CAVE);
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 64), 2005L, hostCave));
        assertEquals("v2.sinkhole-non-limestone", failure.ruleId());
    }

    @Test
    void modulesRemainExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformSinkholeModuleV2().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformKarstSpringModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.SINKHOLE).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.KARST_SPRING).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.SINKHOLE));
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.KARST_SPRING));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformSinkholeModuleV2.MODULE_ID.equals(module.moduleId())));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformKarstSpringModuleV2.MODULE_ID.equals(module.moduleId())));
    }

    @Test
    void wholeTileThreadAndLocaleChecksumsAreStable() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        CaveNetworkPlanV2 hostCave = codec.readCaveNetworkPlan(HOST_CAVE);
        var first = compiler.compile(intent, bounds(64, 64), 2006L, hostCave);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var second = compiler.compile(intent, bounds(64, 64), 2006L, hostCave);
            assertEquals(first.graph().canonicalChecksum(), second.graph().canonicalChecksum());
            assertEquals(first.exportChecksum(), second.exportChecksum());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                tasks.add(() -> compiler.compile(intent, bounds(64, 64), 2006L, hostCave).exportChecksum());
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
    void parentCaveFixtureChecksumUnchanged() throws Exception {
        CaveNetworkPlanV2 hostCave = codec.readCaveNetworkPlan(HOST_CAVE);
        assertEquals("cave.fixture-network", hostCave.featureId());
        assertEquals("3f0a132fc643a41d66cc100fcb44d9ab8e89b5c814eb7a3560ce870469d21750",
                hostCave.canonicalChecksum());
        assertEquals(SinkholePlanV2.CONTRACT, "sinkhole-plan-contract-v1");
        assertEquals(KarstHydrologyGraphPlanV2.CONTRACT, "karst-hydrology-graph-plan-contract-v1");
    }

    @Test
    void sealedPlansStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        CaveNetworkPlanV2 hostCave = codec.readCaveNetworkPlan(HOST_CAVE);
        var slice = compiler.compile(intent, bounds(64, 64), 2007L, hostCave);

        Path sinkholeFile = temp.resolve("sinkhole-plan-v2.json");
        codec.writeSinkholePlan(sinkholeFile, slice.sinkhole());
        assertEquals(slice.sinkhole(), codec.readSinkholePlan(sinkholeFile));
        Files.copy(sinkholeFile, Path.of("examples/v2/foundation/sinkhole-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        Path springFile = temp.resolve("karst-spring-plan-v2.json");
        codec.writeKarstSpringPlan(springFile, slice.spring());
        assertEquals(slice.spring(), codec.readKarstSpringPlan(springFile));
        Files.copy(springFile, Path.of("examples/v2/foundation/karst-spring-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        Path graphFile = temp.resolve("karst-hydrology-graph-plan-v2.json");
        codec.writeKarstHydrologyGraphPlan(graphFile, slice.graph());
        assertEquals(slice.graph(), codec.readKarstHydrologyGraphPlan(graphFile));
        Files.copy(graphFile, Path.of("examples/v2/foundation/karst-hydrology-graph-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);

        Path cenoteFile = temp.resolve("cenote-plan-v2.json");
        codec.writeCenotePlan(cenoteFile, slice.cenote());
        assertEquals(slice.cenote(), codec.readCenotePlan(cenoteFile));
        Files.copy(cenoteFile, Path.of("examples/v2/foundation/cenote-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static TerrainIntentV2 copyIntent(
            TerrainIntentV2 base,
            List<TerrainIntentV2.Relation> relations
    ) {
        return new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), relations, base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
    }

    private static TerrainIntentV2 withGeology(TerrainIntentV2 base, String geology) {
        return new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), base.relations(), base.constraints(),
                new TerrainIntentV2.EnvironmentDescriptor(
                        geology, base.environment().climatePreset(), base.environment().ecologyPreset()),
                base.mapReferences(), base.structures(), base.provenance());
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, 62);
    }
}
