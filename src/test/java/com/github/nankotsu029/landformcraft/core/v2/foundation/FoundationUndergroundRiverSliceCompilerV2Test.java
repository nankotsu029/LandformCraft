package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformCaveEntranceModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformUndergroundRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.cave.UndergroundRiverGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cave.CaveNetworkGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cave.CaveNetworkPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.water.UndergroundLakeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.water.UndergroundLakePlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.UndergroundLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationUndergroundRiverSliceCompilerV2Test {
    private static final Path POSITIVE =
            Path.of("examples/v2/foundation/underground-river-positive.terrain-intent-v2.json");
    private static final Path CAVE_NETWORK_EXAMPLE =
            Path.of("examples/v2/volume/cave-network-plan-v2.json");
    private static final Path UNDERGROUND_LAKE_EXAMPLE =
            Path.of("examples/v2/volume/underground-lake-plan-v2.json");
    private static final String KNOWN_CAVE_CHECKSUM =
            "3f0a132fc643a41d66cc100fcb44d9ab8e89b5c814eb7a3560ce870469d21750";
    private static final String KNOWN_LAKE_CHECKSUM =
            "d1eec8fb1734fcfd3beae7f8dbd2370ba11cbe8c0bc79462879328182189ff2f";
    private static final long M = 1_000_000L;

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationUndergroundRiverSliceCompilerV2 riverCompiler =
            new FoundationUndergroundRiverSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void positiveSliceProducesMetricsRoundTripAndSceneChecksum() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var hosts = fixtureHosts();
        var slice = riverCompiler.compile(intent, bounds(64, 64, 0), 911L, hosts.cave(), hosts.lake());
        assertTrue(slice.validation().metrics().reachable());
        assertTrue(slice.validation().metrics().downGradientOk());
        assertTrue(slice.validation().metrics().singleFluidOwner());
        assertTrue(slice.validation().metrics().leakFree());
        assertTrue(slice.validation().metrics().airPocketOk());
        assertTrue(slice.validation().metrics().fluidOrderOk());
        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().budgetOk());
        assertTrue(slice.validation().metrics().sceneExportOk());
        assertEquals(64, slice.sceneExportChecksum().length());
        assertEquals(64, slice.exportChecksum().length());
        assertEquals(KNOWN_CAVE_CHECKSUM, slice.river().hostCaveCanonicalChecksum());
        assertEquals(KNOWN_LAKE_CHECKSUM, slice.river().hostLakeCanonicalChecksum());

        Path planFile = Path.of("examples/v2/foundation/underground-river-plan-v2.json");
        codec.writeUndergroundRiverPlan(planFile, slice.river());
        assertEquals(slice.river(), codec.readUndergroundRiverPlan(planFile));
        UndergroundRiverGeneratorV2 generator = new UndergroundRiverGeneratorV2(
                slice.river(), hosts.cave(), hosts.lake());
        assertEquals(slice.sceneExportChecksum(), generator.sceneChecksum());
        assertEquals(generator.exportChecksum(), generator.tileExportChecksum());
    }

    @Test
    void sealedPlanStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var hosts = fixtureHosts();
        var slice = riverCompiler.compile(intent, bounds(64, 64, 0), 101L, hosts.cave(), hosts.lake());
        Path planFile = temp.resolve("underground-river-plan-v2.json");
        codec.writeUndergroundRiverPlan(planFile, slice.river());
        assertEquals(slice.river(), codec.readUndergroundRiverPlan(planFile));
        Files.copy(planFile, Path.of("examples/v2/foundation/underground-river-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void moduleRemainsExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformUndergroundRiverModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER).moduleId());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.FLOODED_CAVE).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformUndergroundRiverModuleV2.MODULE_ID.equals(module.moduleId())));
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformCaveEntranceModuleV2().descriptor().lifecycleStatus());
    }

    @Test
    void missingWithinRelationIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), List.of(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        var hosts = fixtureHosts();
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> riverCompiler.compile(intent, bounds(64, 64, 0), 1L, hosts.cave(), hosts.lake()));
        assertEquals("v2.underground-river-missing-relation", failure.ruleId());
    }

    @Test
    void unreachablePathIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = withRiverParameters(base, new TerrainIntentV2.UndergroundRiverParameters(
                new TerrainIntentV2.IntRange(2, 3),
                new TerrainIntentV2.IntRange(2, 4),
                2,
                "n.missing",
                "n.chamber",
                "fluid.underground-lake"));
        var hosts = fixtureHosts();
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> riverCompiler.compile(intent, bounds(64, 64, 0), 2L, hosts.cave(), hosts.lake()));
        assertEquals("v2.underground-river-unreachable", failure.ruleId());
    }

    @Test
    void upGradientPathIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = withRiverParameters(base, new TerrainIntentV2.UndergroundRiverParameters(
                new TerrainIntentV2.IntRange(2, 3),
                new TerrainIntentV2.IntRange(2, 4),
                2,
                "n.source",
                "n.chamber",
                "fluid.underground-lake"));
        // Uphill source→chamber while still emptying into the lake chamber.
        var cave = CaveNetworkPlanCompilerV2.compile(
                "cave.fixture-network",
                List.of(
                        node("n.entrance", CaveNetworkPlanV2.NodeKind.ENTRANCE, 0, 18, 0, 3),
                        node("n.source", CaveNetworkPlanV2.NodeKind.JUNCTION, 12, 6, 0, 3),
                        node("n.chamber", CaveNetworkPlanV2.NodeKind.CHAMBER, 24, 8, 0, 4)),
                List.of(
                        edge("e.1", "n.entrance", "n.source", 2),
                        edge("e.2", "n.source", "n.chamber", 2)),
                List.of("n.entrance"),
                20,
                CaveNetworkPlanV2.Kernel.standard());
        var lake = UndergroundLakePlanCompilerV2.compile(
                "cave.underground-lake",
                cave.plan(),
                "n.chamber",
                "n.entrance",
                4 * M,
                8,
                "fluid.underground-lake",
                2,
                UndergroundLakePlanV2.Kernel.standard());
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> riverCompiler.compile(intent, bounds(64, 64, 0), 3L, cave.plan(), lake.plan()));
        assertEquals("v2.underground-river-up-gradient", failure.ruleId());
    }

    @Test
    void fluidOwnerConflictIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = withRiverParameters(base, new TerrainIntentV2.UndergroundRiverParameters(
                new TerrainIntentV2.IntRange(2, 3),
                new TerrainIntentV2.IntRange(2, 4),
                2,
                "n.junction",
                "n.chamber",
                "fluid.other-owner"));
        var hosts = fixtureHosts();
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> riverCompiler.compile(intent, bounds(64, 64, 0), 4L, hosts.cave(), hosts.lake()));
        assertEquals("v2.underground-river-fluid-owner-conflict", failure.ruleId());
    }

    @Test
    void leakEscapingCaveAabbIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = withRiverParameters(base, new TerrainIntentV2.UndergroundRiverParameters(
                new TerrainIntentV2.IntRange(8, 8),
                new TerrainIntentV2.IntRange(2, 4),
                2,
                "n.junction",
                "n.chamber",
                "fluid.underground-lake"));
        var hosts = fixtureHosts();
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> riverCompiler.compile(intent, bounds(64, 64, 0), 5L, hosts.cave(), hosts.lake()));
        assertEquals("v2.underground-river-leak", failure.ruleId());
    }

    @Test
    void frozenHostChecksumsRemainUnchangedAndHostGeneratorsPass() throws Exception {
        CaveNetworkPlanV2 caveExample = codec.readCaveNetworkPlan(CAVE_NETWORK_EXAMPLE);
        UndergroundLakePlanV2 lakeExample = codec.readUndergroundLakePlan(UNDERGROUND_LAKE_EXAMPLE);
        assertEquals(KNOWN_CAVE_CHECKSUM, caveExample.canonicalChecksum());
        assertEquals(KNOWN_LAKE_CHECKSUM, lakeExample.canonicalChecksum());

        var hosts = fixtureHosts();
        assertEquals(KNOWN_CAVE_CHECKSUM, hosts.cave().canonicalChecksum());
        assertEquals(KNOWN_LAKE_CHECKSUM, hosts.lake().canonicalChecksum());

        CaveNetworkGeneratorV2 caveGenerator = new CaveNetworkGeneratorV2(
                hosts.compiledCave().plan(),
                hosts.compiledCave().sdfPlan(),
                hosts.compiledCave().csgPlan());
        assertTrue(caveGenerator.validate().carvedSamples() > 0);

        UndergroundLakeGeneratorV2 lakeGenerator = new UndergroundLakeGeneratorV2(
                hosts.compiledLake().plan(),
                hosts.cave(),
                hosts.compiledLake().sdfPlan(),
                hosts.compiledCave().sdfPlan(),
                hosts.compiledLake().csgPlan());
        assertTrue(lakeGenerator.validate().fluidSamples() > 0);
    }

    private static TerrainIntentV2 withRiverParameters(
            TerrainIntentV2 base,
            TerrainIntentV2.UndergroundRiverParameters parameters
    ) {
        List<TerrainIntentV2.Feature> features = base.features().stream()
                .map(feature -> feature.kind() == TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER
                        ? new TerrainIntentV2.Feature(
                        feature.id(), feature.kind(), feature.geometry(), parameters,
                        feature.priority(), feature.provenance())
                        : feature)
                .toList();
        return new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                features, base.relations(), base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
    }

    private static WorldBlueprintV2.Bounds bounds(int width, int length, int waterLevel) {
        return new WorldBlueprintV2.Bounds(width, length, 0, 256, waterLevel);
    }

    private HostBundle fixtureHosts() {
        var cave = CaveNetworkPlanCompilerV2.compile(
                "cave.fixture-network",
                List.of(
                        node("n.entrance", CaveNetworkPlanV2.NodeKind.ENTRANCE, 0, 18, 0, 3),
                        node("n.junction", CaveNetworkPlanV2.NodeKind.JUNCTION, 12, 10, 0, 3),
                        node("n.chamber", CaveNetworkPlanV2.NodeKind.CHAMBER, 24, 8, 0, 4)),
                List.of(
                        edge("e.1", "n.entrance", "n.junction", 2),
                        edge("e.2", "n.junction", "n.chamber", 2)),
                List.of("n.entrance"),
                20,
                CaveNetworkPlanV2.Kernel.standard());
        var lake = UndergroundLakePlanCompilerV2.compile(
                "cave.underground-lake",
                cave.plan(),
                "n.chamber",
                "n.entrance",
                4 * M,
                8,
                "fluid.underground-lake",
                2,
                UndergroundLakePlanV2.Kernel.standard());
        return new HostBundle(cave, lake);
    }

    private static CaveNetworkPlanV2.Node node(
            String id,
            CaveNetworkPlanV2.NodeKind kind,
            int x,
            int y,
            int z,
            int radius
    ) {
        return new CaveNetworkPlanV2.Node(id, kind, new VolumeSdfVec3V2(x * M, y * M, z * M), radius * M);
    }

    private static CaveNetworkPlanV2.Edge edge(String id, String from, String to, int radius) {
        return new CaveNetworkPlanV2.Edge(id, from, to, radius * M);
    }

    private record HostBundle(
            CaveNetworkPlanCompilerV2.CompiledCaveNetworkV2 compiledCave,
            UndergroundLakePlanCompilerV2.CompiledUndergroundLakeV2 compiledLake
    ) {
        CaveNetworkPlanV2 cave() {
            return compiledCave.plan();
        }

        UndergroundLakePlanV2 lake() {
            return compiledLake.plan();
        }
    }
}
