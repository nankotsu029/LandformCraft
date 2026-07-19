package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformOxbowLakeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.oxbow.OxbowLakeGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OxbowLakePlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
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

class FoundationOxbowLakeSliceCompilerV2Test {
    private static final Path POSITIVE =
            Path.of("examples/v2/foundation/oxbow-lake-positive.terrain-intent-v2.json");
    private static final Path ORPHAN =
            Path.of("examples/v2/foundation/oxbow-lake-orphan.terrain-intent-v2.json");
    private static final Path RIVER_PLAN =
            Path.of("examples/v2/foundation/river-plan-v2.json");
    private static final Path RIVER_GRAPH_ROLES_PLAN =
            Path.of("examples/v2/foundation/river-graph-roles-plan-v2.json");
    private static final Path ADVANCED_RIVER_LAKE_SPLIT =
            Path.of("examples/v2/foundation/advanced-river-lake-split-contract-v2.json");
    private static final Path OPEN_SPILL_LAKE =
            Path.of("examples/v2/hydrology/open-spill-lake.terrain-intent-v2.json");
    private static final String OPEN_SPILL_LAKE_SHA256 =
            "1b7615a159ad8afb6a5bcc0a1084d2c2f9600796095fa0d20efa650b03137948";
    private static final String RIVER_PLAN_CANONICAL_CHECKSUM =
            "269ca42102418401385f7d29fd554fe7cbd6bed9bf7628ced675238d629851fe";
    private static final String RIVER_GRAPH_ROLES_CANONICAL_CHECKSUM =
            "f461d8bca0a35e48b6de072eced663793ff2163f0cc9651681742890fd87469e";
    private static final String ADVANCED_RIVER_LAKE_SPLIT_CHECKSUM =
            "8c2adc7e0f9839e1ea1d85ba176db176757479a3ec422d997aba20840fa395e7";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationOxbowLakeSliceCompilerV2 compiler = new FoundationOxbowLakeSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void positiveSliceProducesMetricsRoundTripAndExport() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var slice = compiler.compile(intent, bounds(64, 48, 62), 10110L);
        assertTrue(slice.validation().metrics().cutoffOwnershipOk());
        assertTrue(slice.validation().metrics().parentRiverBindOk());
        assertTrue(slice.validation().metrics().stagnantLevelOk());
        assertTrue(slice.validation().metrics().rimClosedOk());
        assertTrue(slice.validation().metrics().wetlandHandoffOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().exportOk());
        assertTrue(slice.validation().metrics().orphanFree());
        assertEquals(64, slice.exportChecksum().length());
        assertEquals("oxbow-basin", slice.oxbow().featureId());
        assertEquals("main-stem", slice.oxbow().parentRiverFeatureId());
        assertEquals("host-floodplain", slice.oxbow().hostSurfaceFeatureId());
        assertEquals("reach-main-stem-1", slice.oxbow().cutoffReachId());
        assertEquals(OxbowLakePlanV2.TERMINAL_POLICY, slice.oxbow().terminalPolicy());
        assertEquals(slice.river().canonicalChecksum(), slice.oxbow().parentRiverPlanChecksum());
        assertEquals(slice.river().canonicalChecksum(), slice.oxbow().parentRiverPlanChecksum());

        Path planFile = Path.of("examples/v2/foundation/oxbow-lake-plan-v2.json");
        codec.writeOxbowLakePlan(planFile, slice.oxbow());
        assertEquals(slice.oxbow(), codec.readOxbowLakePlan(planFile));

        OxbowLakeGeneratorV2 generator = new OxbowLakeGeneratorV2(slice.oxbow());
        assertEquals(generator.exportChecksum(slice.river()), generator.tileExportChecksum(slice.river()));
        assertEquals(slice.exportChecksum(), generator.exportChecksum(slice.river()));
    }

    @Test
    void sealedPlanStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var slice = compiler.compile(intent, bounds(64, 48, 62), 10111L);
        Path planFile = temp.resolve("oxbow-lake-plan-v2.json");
        codec.writeOxbowLakePlan(planFile, slice.oxbow());
        assertEquals(slice.oxbow(), codec.readOxbowLakePlan(planFile));
        Files.copy(planFile, Path.of("examples/v2/foundation/oxbow-lake-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void featureKindClassificationMatchesTaskScope() {
        List<String> kinds = Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .map(Enum::name)
                .toList();
        assertTrue(kinds.contains("OXBOW_LAKE"));
        assertTrue(kinds.contains("LAKE"));
        assertTrue(kinds.contains("SPRING"));
        assertFalse(kinds.contains("DAM_RESERVOIR"));
        assertFalse(kinds.contains("BRAIDED_RIVER"));
        assertFalse(kinds.contains("ESTUARY"));
    }

    @Test
    void moduleRemainsExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformOxbowLakeModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.OXBOW_LAKE).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.OXBOW_LAKE));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformOxbowLakeModuleV2.MODULE_ID.equals(module.moduleId())));
    }

    @Test
    void orphanMissingRiverIsRejected() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(ORPHAN);
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 48, 62), 1L));
        assertEquals("v2.oxbow-missing-river", failure.ruleId());
    }

    @Test
    void missingSupportedByIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(),
                List.of(base.relations().stream()
                        .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ORIGINATES_AT)
                        .findFirst()
                        .orElseThrow()),
                base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 48, 62), 2L));
        assertEquals("v2.oxbow-missing-host", failure.ruleId());
    }

    @Test
    void disconnectedOxbowFarFromRiverIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2.Feature disconnectedOxbow = base.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.OXBOW_LAKE)
                .findFirst()
                .orElseThrow();
        TerrainIntentV2.Feature movedOxbow = new TerrainIntentV2.Feature(
                disconnectedOxbow.id(),
                disconnectedOxbow.kind(),
                new TerrainIntentV2.PolygonGeometry(List.of(List.of(
                        new TerrainIntentV2.Point2(900_000, 900_000),
                        new TerrainIntentV2.Point2(920_000, 900_000),
                        new TerrainIntentV2.Point2(920_000, 920_000),
                        new TerrainIntentV2.Point2(900_000, 920_000),
                        new TerrainIntentV2.Point2(900_000, 900_000)
                ))),
                disconnectedOxbow.parameters(),
                disconnectedOxbow.priority(),
                disconnectedOxbow.provenance());
        List<TerrainIntentV2.Feature> features = base.features().stream()
                .map(feature -> feature.id().equals(movedOxbow.id()) ? movedOxbow : feature)
                .toList();
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                features, base.relations(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(64, 48, 62), 3L));
        assertEquals("v2.oxbow-disconnected", failure.ruleId());
    }

    @Test
    void localeTimezoneAndThreadStability() throws Exception {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
            String baseline = compiler.compile(intent, bounds(64, 48, 62), 10112L).exportChecksum();
            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                Callable<String> task = () -> compiler.compile(intent, bounds(64, 48, 62), 10112L)
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
        assertEquals(RIVER_PLAN_CANONICAL_CHECKSUM,
                codec.readRiverPlan(RIVER_PLAN).canonicalChecksum());
        assertEquals(RIVER_GRAPH_ROLES_CANONICAL_CHECKSUM,
                codec.readRiverPlan(RIVER_GRAPH_ROLES_PLAN).canonicalChecksum());
        assertEquals(ADVANCED_RIVER_LAKE_SPLIT_CHECKSUM,
                codec.readAdvancedRiverLakeSplitContract(ADVANCED_RIVER_LAKE_SPLIT).canonicalChecksum());
    }

    @Test
    void openSpillLakeFixtureIsByteIdenticalAndStillLoads() throws Exception {
        assertEquals(OPEN_SPILL_LAKE_SHA256, sha256(Files.readAllBytes(OPEN_SPILL_LAKE)));
        TerrainIntentV2 intent = codec.readTerrainIntent(OPEN_SPILL_LAKE);
        TerrainIntentV2.Feature lake = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.LAKE)
                .findFirst()
                .orElseThrow();
        assertTrue(lake.parameters() instanceof TerrainIntentV2.LakeParameters parameters
                && parameters.terminalPolicy() == TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length, int waterLevel) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, waterLevel);
    }
}
