package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformCaveEntranceModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cave.CaveNetworkGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cave.CaveNetworkPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationCaveEntranceSliceCompilerV2Test {
    private static final Path POSITIVE =
            Path.of("examples/v2/foundation/cave-entrance-positive.terrain-intent-v2.json");
    private static final Path ORPHAN =
            Path.of("examples/v2/foundation/cave-entrance-orphan.terrain-intent-v2.json");
    private static final Path CAVE_NETWORK_EXAMPLE =
            Path.of("examples/v2/volume/cave-network-plan-v2.json");
    private static final String KNOWN_CAVE_CHECKSUM =
            "3f0a132fc643a41d66cc100fcb44d9ab8e89b5c814eb7a3560ce870469d21750";
    private static final long M = 1_000_000L;

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationCaveEntranceSliceCompilerV2 entranceCompiler =
            new FoundationCaveEntranceSliceCompilerV2();
    private final BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();

    @Test
    void positiveSliceProducesMetricsRoundTripAndSeamlessChecksum() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var host = fixtureHostCave();
        var slice = entranceCompiler.compile(intent, bounds(64, 64, 0), 910L, host.plan());
        assertTrue(slice.validation().metrics().singleSurfaceHost());
        assertTrue(slice.validation().metrics().singleCaveTarget());
        assertTrue(slice.validation().metrics().reachable());
        assertTrue(slice.validation().metrics().roofOk());
        assertTrue(slice.validation().metrics().floodLeakFree());
        assertTrue(slice.validation().metrics().ownerConflictFree());
        assertTrue(slice.validation().metrics().aabbBudgetOk());
        assertTrue(slice.validation().metrics().seamlessExportOk());
        assertEquals(64, slice.seamlessQueryChecksum().length());
        assertEquals(64, slice.exportChecksum().length());
        assertEquals(host.plan().canonicalChecksum(), slice.entrance().hostCaveCanonicalChecksum());

        Path planFile = Path.of("examples/v2/foundation/cave-entrance-plan-v2.json");
        codec.writeCaveEntrancePlan(planFile, slice.entrance());
        assertEquals(slice.entrance(), codec.readCaveEntrancePlan(planFile));
        assertEquals(slice.seamlessQueryChecksum(),
                new com.github.nankotsu029.landformcraft.generator.v2.foundation.cave.CaveEntranceGeneratorV2(
                        slice.entrance(), host.plan(), slice.entrance().surfaceHostGeometryChecksum())
                        .seamlessQueryChecksum());
    }

    @Test
    void sealedPlanStrictRoundTrip(@TempDir Path temp) throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(POSITIVE);
        var host = fixtureHostCave();
        var slice = entranceCompiler.compile(intent, bounds(64, 64, 0), 101L, host.plan());
        Path planFile = temp.resolve("cave-entrance-plan-v2.json");
        codec.writeCaveEntrancePlan(planFile, slice.entrance());
        assertEquals(slice.entrance(), codec.readCaveEntrancePlan(planFile));
        Files.copy(planFile, Path.of("examples/v2/foundation/cave-entrance-plan-v2.json"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void moduleRemainsExperimentalWithDiagnosticCatalogBinding() {
        assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                new LandformCaveEntranceModuleV2().descriptor().lifecycleStatus());
        assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID,
                catalog.requireFor(TerrainIntentV2.FeatureKind.CAVE_ENTRANCE).moduleId());
        assertFalse(catalog.hasValidatorCapability(TerrainIntentV2.FeatureKind.CAVE_ENTRANCE));
        assertTrue(catalog.modules().stream()
                .noneMatch(module -> LandformCaveEntranceModuleV2.MODULE_ID.equals(module.moduleId())));
    }

    @Test
    void orphanMissingEntranceOfIsRejected() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(ORPHAN);
        var host = fixtureHostCave();
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> entranceCompiler.compile(intent, bounds(64, 64, 0), 1L, host.plan()));
        assertEquals("v2.cave-entrance-orphan", failure.ruleId());
    }

    @Test
    void unreachableTargetEntranceNodeIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = withEntranceParameters(base, new TerrainIntentV2.CaveEntranceParameters(
                -4, 3, 5, 1, "n.chamber"));
        var host = fixtureHostCave();
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> entranceCompiler.compile(intent, bounds(64, 64, 0), 2L, host.plan()));
        assertEquals("v2.cave-entrance-unreachable", failure.ruleId());
    }

    @Test
    void thinRoofIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = withEntranceParameters(base, new TerrainIntentV2.CaveEntranceParameters(
                -4, 3, 5, 16, "n.entrance"));
        var host = fixtureHostCave();
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> entranceCompiler.compile(intent, bounds(64, 64, 0), 3L, host.plan()));
        assertEquals("v2.cave-entrance-thin-roof", failure.ruleId());
    }

    @Test
    void floodLeakOpeningBelowWaterIsRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        TerrainIntentV2 intent = withEntranceParameters(base, new TerrainIntentV2.CaveEntranceParameters(
                -64, 3, 5, 1, "n.entrance"));
        var host = fixtureHostCave();
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> entranceCompiler.compile(intent, bounds(64, 64, 0), 4L, host.plan()));
        assertEquals("v2.cave-entrance-flood-leak", failure.ruleId());
    }

    @Test
    void ambiguousSurfaceHostsAreRejected() throws Exception {
        TerrainIntentV2 base = codec.readTerrainIntent(POSITIVE);
        List<TerrainIntentV2.Feature> features = new ArrayList<>(base.features());
        features.add(new TerrainIntentV2.Feature(
                "host-valley",
                TerrainIntentV2.FeatureKind.VALLEY,
                new TerrainIntentV2.SplineGeometry(
                        List.of(new TerrainIntentV2.Point2(0, 0),
                                new TerrainIntentV2.Point2(350_000, 250_000),
                                new TerrainIntentV2.Point2(700_000, 550_000)),
                        TerrainIntentV2.Interpolation.POLYLINE),
                new TerrainIntentV2.ValleyParameters(
                        TerrainIntentV2.ValleyCrossSection.U_PROFILE,
                        new TerrainIntentV2.IntRange(18, 22),
                        new TerrainIntentV2.IntRange(12, 16),
                        new TerrainIntentV2.IntRange(8, 12),
                        8,
                        TerrainIntentV2.ValleyConnectionRole.NONE),
                25,
                base.features().getFirst().provenance()));
        List<TerrainIntentV2.Relation> relations = new ArrayList<>(base.relations());
        relations.add(new TerrainIntentV2.Relation(
                "entrance-supported-by-valley",
                TerrainIntentV2.RelationKind.SUPPORTED_BY,
                "feature:mouth-entrance",
                "feature:host-valley",
                TerrainIntentV2.Strength.HARD));
        TerrainIntentV2 ambiguous = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                features, relations, base.constraints(), base.environment(),
                base.mapReferences(), base.structures(), base.provenance());
        var host = fixtureHostCave();
        FoundationSliceException failure = assertThrows(FoundationSliceException.class,
                () -> entranceCompiler.compile(ambiguous, bounds(64, 64, 0), 5L, host.plan()));
        assertEquals("v2.cave-entrance-ambiguous-host", failure.ruleId());
    }

    @Test
    void caveNetworkFixtureChecksumRemainsUnchangedAndValidates() throws Exception {
        CaveNetworkPlanV2 example = codec.readCaveNetworkPlan(CAVE_NETWORK_EXAMPLE);
        assertEquals(KNOWN_CAVE_CHECKSUM, example.canonicalChecksum());
        var compiled = fixtureHostCave();
        assertEquals(KNOWN_CAVE_CHECKSUM, compiled.plan().canonicalChecksum());
        CaveNetworkGeneratorV2 generator = new CaveNetworkGeneratorV2(
                compiled.plan(), compiled.sdfPlan(), compiled.csgPlan());
        CaveNetworkGeneratorV2.CaveNetworkMetricsV2 metrics = generator.validate();
        assertTrue(metrics.carvedSamples() > 0);
        assertEquals(0, metrics.thinRoofSamples());
        assertEquals(0, metrics.breakthroughSamples());
    }

    private static CaveNetworkPlanCompilerV2.CompiledCaveNetworkV2 fixtureHostCave() {
        return CaveNetworkPlanCompilerV2.compile(
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
    }

    private static TerrainIntentV2 withEntranceParameters(
            TerrainIntentV2 base,
            TerrainIntentV2.CaveEntranceParameters parameters
    ) {
        List<TerrainIntentV2.Feature> features = base.features().stream()
                .map(feature -> feature.kind() == TerrainIntentV2.FeatureKind.CAVE_ENTRANCE
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
}
