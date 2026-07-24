package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridReaderV1;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-09 production evidence for ADR 0040: the {@code surface-2_5d} contributor set is any subset
 * of the four V2-2 coastal kinds, including none.
 *
 * <p>The 2026-07-23 cross-cutting audit (§2.3) measured that a "beach on its own" could not be
 * exported at all, because {@code CoastalGeneratorRuntimeV2} demanded all four contributors. That
 * demand was a coverage requirement on the modifier tier, which ADR 0038 D5-3 forbids and which the
 * V2-18-10 foundation owner gate already enforces once, correctly, at the foundation tier. These
 * tests pin the relaxation together with the invariants it must not disturb.</p>
 */
class CoastalContributorSubsetV2Test {
    private static final Path DIAGNOSTIC = Path.of("examples/v2/diagnostic");
    private static final SurfaceBaselineV2 BASELINE =
            new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46);

    /**
     * ADR 0040 D6: the tile semantic checksum of the unchanged four-contributor fixture, measured on
     * the tree immediately before V2-19-09. Deleting a runtime <em>check</em> must not move a single
     * block of a Release that still declares all four contributors, and the tile semantic checksum is
     * exactly the canonical block stream's identity. A change here is an ADR 0040 D9 stop condition,
     * not a value to refresh.
     */
    private static final String FOUR_CONTRIBUTOR_TILE_SEMANTIC_CHECKSUM =
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
    void theFourContributorReleaseIsUnchangedByTheRelaxation(@TempDir Path root) throws Exception {
        Path release = export(root, "harbor-cove-64-honored", "four-contributor-export");

        assertEquals(List.of(FOUR_CONTRIBUTOR_TILE_SEMANTIC_CHECKSUM), tileSemanticChecksums(release));
    }

    @Test
    void aBeachOnlySubsetExports(@TempDir Path root) throws Exception {
        Path release = export(root, "harbor-cove-64-honored-beach", "beach-only-export");

        TerrainIntentV2 published = codec.readTerrainIntent(
                release.resolve("source/terrain-intent.json"));
        assertEquals(List.of(TerrainIntentV2.FeatureKind.SANDY_BEACH),
                published.features().stream()
                        .map(TerrainIntentV2.Feature::kind)
                        .filter(CoastalGeneratorRuntimeV2.supportedKinds()::contains)
                        .toList());
        assertTrue(Files.isRegularFile(release.resolve("manifest.json")));
    }

    @Test
    void anIntentWithNoCoastalContributorAtAllExports(@TempDir Path root) throws Exception {
        // ADR 0040 D1 size 0: the macro foundation alone owns the surface. The mask is byte-identical
        // to the four-contributor fixture, so the published land-water field must reproduce it cell
        // for cell — no modifier is left to reshape anything.
        Path release = export(root, "harbor-cove-64-honored-coastless", "coastless-export");

        WorldBlueprintV2 blueprint = codec.readWorldBlueprint(
                release.resolve("blueprint/world-blueprint.json"));
        assertTrue(blueprint.coastalTransitionPlans().isEmpty(),
                "a coastless intent must seal no coastal transition plan");
        assertEquals(List.of(), blueprint.coastalFeaturePlans());

        int[] published = readActualLandWater(release);
        int[] declared = readMask(
                DIAGNOSTIC.resolve("maps/harbor-cove-64-honored-land-water-u8.png"), 64, 64);
        assertArrayEquals(declared, published,
                "with no modifier the published land-water field is the declared mask");
    }

    @Test
    void aCoastlessReleaseStillCarriesEveryPerKindDescriptorField(@TempDir Path root) throws Exception {
        // ADR 0040 D3: the sidecar shape does not depend on which kinds an intent declares. A cell of
        // an undeclared kind carries that kind's canonical OUTSIDE value, never a new sentinel.
        Path release = export(root, "harbor-cove-64-honored-coastless", "coastless-fields-export");

        ConstraintFieldIndexV2 index = new ConstraintFieldIndexCodecV2()
                .readAndVerify(release.resolve("constraints/index.json"), release.resolve("constraints"));
        assertEquals(Set.of(
                        FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER),
                index.fields().stream().map(field -> field.definition().semantic())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void aSubsetOnTheLegacyBaselinePathIsRejectedByTheOwnerGateNotByTheRuntime(@TempDir Path root)
            throws Exception {
        // ADR 0040 D2: coverage is enforced once, at the foundation tier. A request with no explicit
        // foundation input is rejected for the honest reason — no cell has a foundation owner — for
        // every contributor count, so no second coverage rule survives inside the coastal runtime.
        Path workspace = Files.createDirectories(root.resolve("legacy"));
        GenerationRequestV2 declared = codec.readGenerationRequest(
                DIAGNOSTIC.resolve("harbor-cove-64-honored-beach.request-v2.json"));
        Path request = workspace.resolve("request-v2.json");
        codec.writeGenerationRequest(request, withoutFoundationInput(declared));
        Files.createDirectories(workspace.resolve("maps"));
        Files.copy(DIAGNOSTIC.resolve("maps/harbor-cove-64-honored-beach-land-water-u8.png"),
                workspace.resolve("maps/harbor-cove-64-honored-beach-land-water-u8.png"));
        Path intent = workspace.resolve("terrain-intent-v2.json");
        Files.copy(DIAGNOSTIC.resolve("harbor-cove-64-honored-beach.terrain-intent-v2.json"), intent);

        IOException rejected = assertThrows(IOException.class,
                () -> exportWorkspace(root, request, intent, "legacy-subset-export"));

        assertTrue(rejected instanceof SurfaceFoundationOwnerRejectedV2, rejected::toString);
        assertEquals(SurfaceFoundationOwnerGateV2.RULE_FOUNDATION_OWNER_COVERAGE_INCOMPLETE,
                ((SurfaceFoundationOwnerRejectedV2) rejected).ruleId());
        assertTrue(Files.notExists(root.resolve("exports")) || isEmpty(root.resolve("exports")),
                "a rejected export published bytes");
    }

    @Test
    void twoContributorsOfTheSameKindAreStillRejected() throws Exception {
        // ADR 0040 freeze 1: the subset rule relaxes cardinality across kinds, never the one
        // contributor per kind uniqueness the compositor's owner index depends on.
        TerrainIntentV2 declared = codec.readTerrainIntent(
                DIAGNOSTIC.resolve("harbor-cove-64-honored-beach.terrain-intent-v2.json"));
        TerrainIntentV2.Feature beach = declared.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.SANDY_BEACH)
                .findFirst().orElseThrow();
        List<TerrainIntentV2.Feature> twice = new ArrayList<>(declared.features());
        twice.add(new TerrainIntentV2.Feature(beach.id() + "-second", beach.kind(), beach.geometry(),
                beach.parameters(), beach.priority(), beach.provenance()));
        GenerationRequestV2 request = codec.readGenerationRequest(
                DIAGNOSTIC.resolve("harbor-cove-64-honored-beach.request-v2.json"));

        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> CoastalGeneratorRuntimeV2.create(compile(request, withFeatures(declared, twice))));

        assertTrue(rejected.getMessage().contains("one contributor per coastal kind"),
                rejected::getMessage);
    }

    @Test
    void everyPipelineNowRequiresNoCompanionKind() {
        // ADR 0040 D5: the V2-19-08 runtime precondition table keeps its shape, API and rule id; the
        // single requirement it ever carried is gone because the runtime no longer imposes it.
        assertEquals(Set.of(), ProductionRoutePreconditionsV2.requiredCompanionKinds());
        assertEquals(Set.of(), ProductionRoutePreconditionsV2.requiredCompanionKinds(
                CoastalSurfaceExportPipelineV2.PIPELINE_ID));
        assertEquals(Set.of(), ProductionRoutePreconditionsV2.requiredCompanionKinds(
                HydrologyPlanExportPipelineV2.PIPELINE_ID));
    }

    private WorldBlueprintV2 compile(GenerationRequestV2 request, TerrainIntentV2 intent) {
        GenerationRequestV2.Bounds bounds = request.bounds();
        return new DiagnosticBlueprintCompilerV2().compile(new DiagnosticCompileRequestV2(
                request.requestId(),
                new GenerationBounds(bounds.width(), bounds.length(), bounds.minY(), bounds.maxY(),
                        bounds.waterLevel()),
                request.generation().tileSize(),
                request.generation().globalSeed(),
                codec.generationRequestChecksum(request),
                DiagnosticCompileRequestV2.defaultBudget()), intent);
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

    /**
     * ADR 0040 D2: the same request with its declared per-medium base levels removed. The mask stays
     * declared and bound, so what is missing is exactly the second half of the explicit foundation
     * input (ADR 0038 D2-2b) and the run falls back to the legacy surface baseline path.
     */
    private static GenerationRequestV2 withoutFoundationInput(GenerationRequestV2 request) {
        return new GenerationRequestV2(
                request.requestVersion(), request.requestId(), request.bounds(), request.prompt(),
                request.referenceImages(), request.constraintMaps(), request.generation(),
                request.constraintMapBudget(), Optional.empty(), Optional.empty(),
                java.util.Optional.empty());
    }

    private static int[] readActualLandWater(Path release) throws IOException {
        Path constraintRoot = release.resolve("constraints");
        ConstraintFieldIndexV2 index = new ConstraintFieldIndexCodecV2()
                .readAndVerify(constraintRoot.resolve("index.json"), constraintRoot);
        FieldArtifactDescriptorV2 field = index.fields().stream()
                .filter(candidate -> candidate.definition().semantic()
                        == FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER)
                .findFirst().orElseThrow();
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(constraintRoot, field)) {
            return reader.readWindow(0, 0, field.definition().width(), field.definition().length())
                    .toRawArray();
        }
    }

    private static int[] readMask(Path png, int width, int length) throws IOException {
        BufferedImage image = ImageIO.read(png.toFile());
        int[] values = new int[Math.multiplyExact(width, length)];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                values[z * width + x] = image.getRaster().getSample(x, z, 0);
            }
        }
        return values;
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
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

    private static List<String> tileSemanticChecksums(Path release) throws IOException {
        List<String> checksums = new ArrayList<>();
        try (Stream<Path> tiles = Files.list(release.resolve("tiles"))) {
            for (Path tile : tiles.sorted().toList()) {
                if (!tile.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                OfflineTileArtifactV2 artifact = new OfflineTileArtifactCodecV2().read(tile);
                checksums.add(artifact.semanticChecksum());
            }
        }
        return checksums;
    }
}
