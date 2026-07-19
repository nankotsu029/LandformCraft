package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSpringModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.spring.SpringGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SpringPlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

class FoundationSpringSliceCompilerV2Test {
    private static final Path POSITIVE =
            Path.of("examples/v2/foundation/spring-positive.terrain-intent-v2.json");
    private static final Path ORPHAN =
            Path.of("examples/v2/foundation/spring-orphan.terrain-intent-v2.json");
    private static final Path KARST_SPRING_PLAN =
            Path.of("examples/v2/foundation/karst-spring-plan-v2.json");
    private static final Path RIVER_PLAN =
            Path.of("examples/v2/foundation/river-plan-v2.json");
    private static final Path RIVER_GRAPH_ROLES_PLAN =
            Path.of("examples/v2/foundation/river-graph-roles-plan-v2.json");
    private static final Path ADVANCED_RIVER_LAKE_SPLIT =
            Path.of("examples/v2/foundation/advanced-river-lake-split-contract-v2.json");
    private static final String KARST_SPRING_CANONICAL_CHECKSUM =
            "4c3447afeae77251cad1cad0312bc63de39d91e8680b5c8f73eecbb67bbe4ab8";
    private static final String RIVER_PLAN_CANONICAL_CHECKSUM =
            "269ca42102418401385f7d29fd554fe7cbd6bed9bf7628ced675238d629851fe";
    private static final String RIVER_GRAPH_ROLES_CANONICAL_CHECKSUM =
            "f461d8bca0a35e48b6de072eced663793ff2163f0cc9651681742890fd87469e";
    private static final String ADVANCED_RIVER_LAKE_SPLIT_CHECKSUM =
            "8c2adc7e0f9839e1ea1d85ba176db176757479a3ec422d997aba20840fa395e7";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationSpringSliceCompilerV2 compiler = new FoundationSpringSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void positiveSliceProducesMetricsRoundTripAndExport() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var slice = compiler.compile(intent, bounds(64, 48, 62), 8110L);
        assertTrue(slice.validation().metrics().sourceOwnershipOk());
        assertTrue(slice.validation().metrics().riverSourceBindOk());
        assertTrue(slice.validation().metrics().outflowContinuityOk());
        assertTrue(slice.validation().metrics().hydrologySpringNodeOk());
        assertTrue(slice.validation().metrics().graphReachableOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().exportOk());
        assertTrue(slice.validation().metrics().orphanFree());
        assertEquals(64, slice.exportChecksum().length());
        assertEquals("spring-head", slice.spring().featureId());
        assertEquals("main-stem", slice.spring().riverFeatureId());
        assertEquals("host-plain", slice.spring().outletSurfaceFeatureId());
        assertEquals("hydro.spring.spring-head", slice.spring().hydrologyNodeId());
        assertEquals(SpringPlanV2.HYDROLOGY_NODE_KIND, slice.spring().hydrologyNodeKind());
        assertEquals(slice.river().canonicalChecksum(), slice.spring().riverPlanChecksum());
        assertEquals(slice.river().sourceBedYMillionths(), slice.spring().sourceBedYMillionths());

        Path planFile = Path.of("examples/v2/foundation/spring-plan-v2.json");
        codec.writeSpringPlan(planFile, slice.spring());
        assertEquals(slice.spring(), codec.readSpringPlan(planFile));

        SpringGeneratorV2 generator = new SpringGeneratorV2(slice.spring(), slice.river());
        assertEquals(generator.exportChecksum(), generator.tileExportChecksum());
        assertEquals(slice.exportChecksum(), generator.exportChecksum());
    }

    @Test
    void sealedPlanStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var slice = compiler.compile(intent, bounds(64, 48, 62), 8111L);
        Path planFile = temp.resolve("spring-plan-v2.json");
        codec.writeSpringPlan(planFile, slice.spring());
        assertEquals(slice.spring(), codec.readSpringPlan(planFile));
        Files.copy(planFile, Path.of("examples/v2/foundation/spring-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void featureKindClassificationMatchesTaskScope() {
        List<String> kinds = Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .map(Enum::name)
                .toList();
        assertTrue(kinds.contains("SPRING"));
        assertTrue(kinds.contains("KARST_SPRING"));
        assertFalse(kinds.contains("SPRING_OUTFLOW"));
        assertFalse(kinds.contains("SURFACE_SPRING"));
    }

    @Test
    void moduleRemainsExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformSpringModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.SPRING).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.SPRING));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformSpringModuleV2.MODULE_ID.equals(module.moduleId())));
    }

    @Test
    void orphanMissingRiverIsRejected() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(ORPHAN);
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 48, 62), 1L));
        assertEquals("v2.spring-missing-river", failure.ruleId());
    }

    @Test
    void missingSupportedByIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(),
                List.of(base.relations().stream()
                        .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO)
                        .findFirst()
                        .orElseThrow()),
                base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 48, 62), 2L));
        assertEquals("v2.spring-missing-host", failure.ruleId());
    }

    @Test
    void disconnectedSpringFarFromRiverSourceIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2.Feature disconnectedSpring = base.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.SPRING)
                .findFirst()
                .orElseThrow();
        TerrainIntentV2.Feature movedSpring = new TerrainIntentV2.Feature(
                disconnectedSpring.id(),
                disconnectedSpring.kind(),
                new TerrainIntentV2.PointGeometry(new TerrainIntentV2.Point2(900_000, 900_000)),
                disconnectedSpring.parameters(),
                disconnectedSpring.priority(),
                disconnectedSpring.provenance());
        List<TerrainIntentV2.Feature> features = base.features().stream()
                .map(feature -> feature.id().equals(movedSpring.id()) ? movedSpring : feature)
                .toList();
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                features, base.relations(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 48, 62), 3L));
        assertEquals("v2.spring-disconnected", failure.ruleId());
    }

    @Test
    void localeTimezoneAndThreadStability() throws Exception {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
            String baseline = compiler.compile(intent, bounds(64, 48, 62), 8112L).exportChecksum();
            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                Callable<String> task = () -> compiler.compile(intent, bounds(64, 48, 62), 8112L)
                        .exportChecksum();
                List<Future<String>> futures = pool.invokeAll(List.of(task, task, task, task));
                for (Future<String> future : futures) {
                    assertEquals(baseline, future.get());
                }
            } finally {
                pool.shutdownNow();
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void protectedSealedChecksumsAreUnchanged() throws Exception {
        assertEquals(KARST_SPRING_CANONICAL_CHECKSUM,
                codec.readKarstSpringPlan(KARST_SPRING_PLAN).canonicalChecksum());
        assertEquals(RIVER_PLAN_CANONICAL_CHECKSUM,
                codec.readRiverPlan(RIVER_PLAN).canonicalChecksum());
        assertEquals(RIVER_GRAPH_ROLES_CANONICAL_CHECKSUM,
                codec.readRiverPlan(RIVER_GRAPH_ROLES_PLAN).canonicalChecksum());
        assertEquals(ADVANCED_RIVER_LAKE_SPLIT_CHECKSUM,
                codec.readAdvancedRiverLakeSplitContract(ADVANCED_RIVER_LAKE_SPLIT).canonicalChecksum());
    }

    @Test
    void karstSpringFixtureStillLoads() throws Exception {
        codec.readKarstSpringPlan(KARST_SPRING_PLAN);
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length, int waterLevel) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, waterLevel);
    }
}
