package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformLavaTubeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.lavatube.LavaTubeGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.LavaTubePlanV2;
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

class FoundationLavaTubeSliceCompilerV2Test {
    private static final Path POSITIVE =
            Path.of("examples/v2/foundation/lava-tube-positive.terrain-intent-v2.json");
    private static final Path ORPHAN =
            Path.of("examples/v2/foundation/lava-tube-orphan.terrain-intent-v2.json");
    private static final Path VOLCANIC_CONE_PLAN =
            Path.of("examples/v2/foundation/volcanic-cone-plan-v2.json");
    private static final Path CAVE_NETWORK_PLAN =
            Path.of("examples/v2/volume/cave-network-plan-v2.json");
    private static final Path UNDERGROUND_RIVER_PLAN =
            Path.of("examples/v2/foundation/underground-river-plan-v2.json");
    private static final String VOLCANIC_CONE_CANONICAL_CHECKSUM =
            "835388b9c778e95917891361fbe764e219204410fc2ab7b91f3b6cd045b8deae";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationLavaTubeSliceCompilerV2 compiler = new FoundationLavaTubeSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void positiveSliceProducesMetricsRoundTripAndExport() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var slice = compiler.compile(intent, bounds(96, 96, 62), 7107L);
        assertTrue(slice.validation().metrics().hostRelationOk());
        assertTrue(slice.validation().metrics().tubeContinuityOk());
        assertTrue(slice.validation().metrics().roofSupportOk());
        assertTrue(slice.validation().metrics().fluidConflictFree());
        assertTrue(slice.validation().metrics().aabbBudgetOk());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().exportOk());
        assertTrue(slice.validation().metrics().orphanFree());
        assertEquals(64, slice.exportChecksum().length());
        assertEquals(LavaTubePlanV2.ProvenanceKind.CALDERA, slice.tube().provenanceKind());
        assertEquals("cone-core", slice.tube().volcanicConeFeatureId());
        assertEquals("crater-rim", slice.tube().provenanceFeatureId());
        assertEquals(slice.cone().geometryChecksum(), slice.tube().coneGeometryChecksum());

        Path planFile = Path.of("examples/v2/foundation/lava-tube-plan-v2.json");
        codec.writeLavaTubePlan(planFile, slice.tube());
        assertEquals(slice.tube(), codec.readLavaTubePlan(planFile));

        LavaTubeGeneratorV2 generator = new LavaTubeGeneratorV2(slice.tube(), slice.cone());
        assertEquals(generator.exportChecksum(), generator.tileExportChecksum());
        assertEquals(slice.exportChecksum(), generator.exportChecksum());
    }

    @Test
    void sealedPlanStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var slice = compiler.compile(intent, bounds(96, 96, 62), 7108L);
        Path planFile = temp.resolve("lava-tube-plan-v2.json");
        codec.writeLavaTubePlan(planFile, slice.tube());
        assertEquals(slice.tube(), codec.readLavaTubePlan(planFile));
        Files.copy(planFile, Path.of("examples/v2/foundation/lava-tube-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void featureKindClassificationMatchesTaskScope() {
        List<String> kinds = Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .map(Enum::name)
                .toList();
        assertTrue(kinds.contains("LAVA_TUBE"));
        assertFalse(kinds.contains("LAVA_TUBE_ROOF"));
        assertFalse(kinds.contains("LAVA_TUBE_ENTRANCE"));
        assertFalse(kinds.contains("LAVA_TUBE_SEGMENT"));
    }

    @Test
    void moduleRemainsExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformLavaTubeModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.LAVA_TUBE).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.LAVA_TUBE));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformLavaTubeModuleV2.MODULE_ID.equals(module.moduleId())));
    }

    @Test
    void missingWithinConeIsRejected() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(ORPHAN);
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(96, 96, 62), 1L));
        assertEquals("v2.lava-tube-missing-cone", failure.ruleId());
    }

    @Test
    void missingProvenanceIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(),
                List.of(base.relations().stream()
                        .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.WITHIN)
                        .findFirst()
                        .orElseThrow()),
                base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> compiler.compile(intent, bounds(96, 96, 62), 2L));
        assertEquals("v2.lava-tube-missing-provenance", failure.ruleId());
    }

    @Test
    void localeTimezoneAndThreadStability() throws Exception {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
            String baseline = compiler.compile(intent, bounds(96, 96, 62), 7109L).exportChecksum();
            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                Callable<String> task = () -> compiler.compile(intent, bounds(96, 96, 62), 7109L)
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
    void volcanicConeSealedChecksumIsUnchanged() throws Exception {
        assertEquals(VOLCANIC_CONE_CANONICAL_CHECKSUM,
                codec.readVolcanicConePlan(VOLCANIC_CONE_PLAN).canonicalChecksum());
    }

    @Test
    void relatedVolumeFixturesStillLoad() throws Exception {
        codec.readCaveNetworkPlan(CAVE_NETWORK_PLAN);
        codec.readUndergroundRiverPlan(UNDERGROUND_RIVER_PLAN);
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length, int waterLevel) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, waterLevel);
    }
}
