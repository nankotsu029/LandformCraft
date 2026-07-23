package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.catalog.CompositionProfileRegistryV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.composition.CompositionStageV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-07 production evidence for the macro foundation producer tier: what the stage-level unit test
 * cannot cover — the fail-closed cases a full export has to reject, the terrain semantic checksum
 * invariance of a request that declares no producer, and the adjudicated reachability of the offline
 * ADR 0037 foundation adapter.
 */
class MacroFoundationProducerV2Test {
    private static final Path DIAGNOSTIC = Path.of("examples/v2/diagnostic");
    private static final SurfaceBaselineV2 BASELINE =
            new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46);
    /**
     * Tile semantic checksum of the producer-free 64×64 fixture, measured on the tree immediately
     * before V2-19-07. Registering the plain module changes container bytes (blueprint and manifest
     * checksums, ADR 0038 D9) but must not move a single block of a Release that declares no
     * producer, and the tile semantic checksum is exactly the canonical block stream's identity.
     */
    private static final String PRODUCER_FREE_TILE_SEMANTIC_CHECKSUM =
            "20318e6cef8e224a1c369292e354b9116b3bb706f5fc524b7a1b4dfb343b48b2";

    private static GenerationExecutors executors;

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @BeforeAll
    static void startExecutors() {
        executors = GenerationExecutors.createDefault(2);
    }

    @AfterAll
    static void stopExecutors() {
        executors.shutdown(Duration.ofSeconds(30));
        executors.close();
    }

    @Test
    void aProducerFreeReleaseKeepsItsTerrainSemanticChecksums(@TempDir Path root) throws Exception {
        Path release = export(root, "harbor-cove-64-honored", "producer-free-export");

        List<String> semanticChecksums = new ArrayList<>();
        try (Stream<Path> tiles = Files.list(release.resolve("tiles"))) {
            for (Path tile : tiles.sorted().toList()) {
                if (!tile.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                OfflineTileArtifactV2 artifact = new OfflineTileArtifactCodecV2().read(tile);
                semanticChecksums.add(artifact.semanticChecksum());
            }
        }

        assertEquals(List.of(PRODUCER_FREE_TILE_SEMANTIC_CHECKSUM), semanticChecksums);
    }

    @Test
    void aProducerOverDeclaredWaterIsRejectedBeforeAnythingIsPublished(@TempDir Path root)
            throws Exception {
        // The HARD mask is the land-water authority (ADR 0038 D2-3). A plain stretched over the open
        // sea of the fixture is two HARD sources disagreeing, so the export must fail closed instead
        // of quietly turning declared water into land.
        Path workspace = relocatePlain(root);
        Path intent = workspace.resolve("flooded-intent.json");
        codec.writeTerrainIntent(intent, withPlainRings(
                codec.readTerrainIntent(workspace.resolve("terrain-intent-v2.json")),
                List.of(ring(0.20, 0.80, 0.60, 0.95))));
        Path exports = root.resolve("exports");

        IOException rejected = assertThrows(IOException.class, () -> exportWorkspace(
                root, workspace.resolve("request-v2.json"), intent, "flooded-plain-export"));

        assertTrue(rejected.getMessage().contains(MacroFoundationV2.RULE_PRODUCER_MASK_MEDIUM_CONFLICT),
                rejected::getMessage);
        assertTrue(Files.notExists(exports) || isEmpty(exports), "a rejected export published bytes");
    }

    @Test
    void twoOverlappingProducersAreRejectedBeforeAnythingIsPublished(@TempDir Path root)
            throws Exception {
        // Producer-producer overlap needs a declared interaction; without one it stays
        // UNDECLARED_OVERLAP at the export spine, never a last-write-wins resolution.
        Path workspace = relocatePlain(root);
        Path intent = workspace.resolve("overlapping-intent.json");
        TerrainIntentV2 declared = codec.readTerrainIntent(workspace.resolve("terrain-intent-v2.json"));
        codec.writeTerrainIntent(intent, withSecondPlain(declared, ring(0.30, 0.05, 0.70, 0.12)));
        Path exports = root.resolve("exports");

        IOException rejected = assertThrows(IOException.class, () -> exportWorkspace(
                root, workspace.resolve("request-v2.json"), intent, "overlapping-plain-export"));

        assertTrue(rejected.getMessage().contains("UNDECLARED_OVERLAP"), rejected::getMessage);
        assertTrue(Files.notExists(exports) || isEmpty(exports), "a rejected export published bytes");
    }

    @Test
    void aProducerOnlyIntentStillCannotSelectTheSurfacePath(@TempDir Path root) throws Exception {
        // The surface path still requires the four coastal contributors (V2-2), which is why PLAIN's
        // standalone_usage column stays PARTIAL. Relaxing that requirement is V2-19-09's Task; until
        // then the limitation is fail-closed and stated, never a silent partial Release.
        Path workspace = relocatePlain(root);
        Path intent = workspace.resolve("producer-only-intent.json");
        TerrainIntentV2 declared = codec.readTerrainIntent(workspace.resolve("terrain-intent-v2.json"));
        codec.writeTerrainIntent(intent, new TerrainIntentV2(
                declared.intentVersion(), declared.intentId(), declared.theme(),
                declared.coordinateSystem(),
                declared.features().stream()
                        .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.PLAIN)
                        .toList(),
                List.of(), List.of(), declared.environment(), declared.mapReferences(),
                List.of(), declared.provenance()));

        Exception rejected = assertThrows(Exception.class, () -> exportWorkspace(
                root, workspace.resolve("request-v2.json"), intent, "producer-only-export"));

        assertTrue(rejected.getMessage().contains("coastal contributors")
                        || rejected.getMessage().contains("coastal transition plan"),
                rejected::getMessage);
    }

    @Test
    void everyWiredProducerKindIsPubliclyRoutedAndFoundationEligible() {
        // Drift guard for the tier: a kind the stage builds a producer for must be publicly
        // dispatchable (otherwise the wiring is unreachable) and must carry a FOUNDATION composition
        // profile (otherwise it is not a foundation owner at all, ADR 0038 D3/D4).
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();
        CompositionProfileRegistryV2 profiles = CompositionProfileRegistryV2.builtIn();

        assertEquals(EnumSet.of(TerrainIntentV2.FeatureKind.PLAIN),
                EnumSet.copyOf(MacroFoundationStageV2.WIRED_PRODUCER_KINDS));
        Set<TerrainIntentV2.FeatureKind> routed = registry.routes().stream()
                .map(ProductionDispatchRegistryV2.Route::featureKind)
                .collect(java.util.stream.Collectors.toCollection(
                        () -> EnumSet.noneOf(TerrainIntentV2.FeatureKind.class)));
        for (TerrainIntentV2.FeatureKind kind : MacroFoundationStageV2.WIRED_PRODUCER_KINDS) {
            assertTrue(routed.contains(kind), kind::name);
            assertTrue(profiles.profile(kind).foundationEligible(), kind::name);
            assertTrue(profiles.profile(kind).stages().contains(CompositionStageV2.FOUNDATION),
                    kind::name);
        }
        // …and the reverse: a routed foundation-eligible kind with no producer here would be
        // plan-only wiring, the exact defect V2-19-01 forbids.
        for (TerrainIntentV2.FeatureKind kind : routed) {
            if (profiles.profile(kind).foundationEligible()) {
                assertTrue(MacroFoundationStageV2.WIRED_PRODUCER_KINDS.contains(kind), kind::name);
            }
        }
    }

    @Test
    void theOfflineFoundationAdapterStaysOutsidePublicDispatch() {
        // V2-19-07 adjudication of the ADR 0037 adapter's reachability: it is kept as an offline,
        // test-only projection of the V2-9/V2-10 merge output and is deliberately NOT a second
        // ["surface-2_5d"] production pipeline (ADR 0039 freeze 4). The public route for a foundation
        // kind is the producer tier inside the coastal surface pipeline, which is what PLAIN takes.
        ProductionDispatchRegistryV2 registry = ProductionDispatchRegistryV2.builtIn();

        assertEquals(CoastalSurfaceExportPipelineV2.PIPELINE_ID, registry.routes().stream()
                .filter(route -> route.featureKind() == TerrainIntentV2.FeatureKind.PLAIN)
                .map(ProductionDispatchRegistryV2.Route::pipelineId)
                .findFirst().orElseThrow());
        assertEquals(ProductionDispatchRegistryV2.RouteClass.OFFLINE_PRODUCTION, registry.routes().stream()
                .filter(route -> route.featureKind() == TerrainIntentV2.FeatureKind.PLAIN)
                .map(ProductionDispatchRegistryV2.Route::routeClass)
                .findFirst().orElseThrow());
        assertFalse(registry.routes().stream()
                        .anyMatch(route -> route.pipelineId().equals(FoundationSurfaceExportAdapterV2.ADAPTER_ID)),
                "the ADR 0037 adapter must not appear as a public dispatch route");
    }

    private Path export(Path root, String fixture, String releaseId) throws Exception {
        return exportWorkspace(root, DIAGNOSTIC.resolve(fixture + ".request-v2.json"),
                DIAGNOSTIC.resolve(fixture + ".terrain-intent-v2.json"), releaseId);
    }

    private Path exportWorkspace(Path root, Path request, Path intent, String releaseId)
            throws Exception {
        return new Release2ExportApplicationServiceV2(executors).exportNow(new Release2ExportRequestV2(
                        request, intent, root.resolve("work").resolve(releaseId),
                        root.resolve("exports"), releaseId, BASELINE))
                .releaseDirectory();
    }

    /** Copies the producer fixture and its mask so a modified intent can be exported beside them. */
    private static Path relocatePlain(Path root) throws IOException {
        Path workspace = Files.createDirectories(root.resolve("plain"));
        Files.createDirectories(workspace.resolve("maps"));
        Files.copy(DIAGNOSTIC.resolve("maps").resolve("harbor-cove-64-honored-land-water-u8.png"),
                workspace.resolve("maps").resolve("harbor-cove-64-honored-land-water-u8.png"));
        Files.copy(DIAGNOSTIC.resolve("harbor-cove-64-honored-plain.request-v2.json"),
                workspace.resolve("request-v2.json"));
        Files.copy(DIAGNOSTIC.resolve("harbor-cove-64-honored-plain.terrain-intent-v2.json"),
                workspace.resolve("terrain-intent-v2.json"));
        return workspace;
    }

    private static TerrainIntentV2 withPlainRings(
            TerrainIntentV2 intent,
            List<List<TerrainIntentV2.Point2>> rings
    ) {
        List<TerrainIntentV2.Feature> features = intent.features().stream()
                .map(feature -> feature.kind() != TerrainIntentV2.FeatureKind.PLAIN
                        ? feature
                        : new TerrainIntentV2.Feature(feature.id(), feature.kind(),
                                new TerrainIntentV2.PolygonGeometry(rings), feature.parameters(),
                                feature.priority(), feature.provenance()))
                .toList();
        return withFeatures(intent, features);
    }

    private static TerrainIntentV2 withSecondPlain(
            TerrainIntentV2 intent,
            List<TerrainIntentV2.Point2> ring
    ) {
        TerrainIntentV2.Feature declared = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.PLAIN)
                .findFirst().orElseThrow();
        List<TerrainIntentV2.Feature> features = new ArrayList<>(intent.features());
        features.add(new TerrainIntentV2.Feature(
                declared.id() + "-second", declared.kind(),
                new TerrainIntentV2.PolygonGeometry(List.of(ring)), declared.parameters(),
                declared.priority(), declared.provenance()));
        return withFeatures(intent, List.copyOf(features));
    }

    private static TerrainIntentV2 withFeatures(
            TerrainIntentV2 intent,
            List<TerrainIntentV2.Feature> features
    ) {
        return new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                features, intent.relations(), intent.constraints(), intent.environment(),
                intent.mapReferences(), intent.structures(), intent.provenance());
    }

    /** Closed axis-aligned ring in normalized XZ, in the order the example fixtures use. */
    private static List<TerrainIntentV2.Point2> ring(double minX, double minZ, double maxX, double maxZ) {
        return List.of(point(minX, minZ), point(maxX, minZ), point(maxX, maxZ), point(minX, maxZ),
                point(minX, minZ));
    }

    private static TerrainIntentV2.Point2 point(double x, double z) {
        return new TerrainIntentV2.Point2(
                Math.toIntExact(Math.round(x * TerrainIntentV2.FIXED_SCALE)),
                Math.toIntExact(Math.round(z * TerrainIntentV2.FIXED_SCALE)));
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }
}
