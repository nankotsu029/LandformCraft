package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoder;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfaceVerifierV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-06 production evidence for the {@code HEIGHT_GUIDE} macro foundation consumer.
 *
 * <p>Covers what the stage-level unit test cannot: the constraint map cardinality relaxation as the
 * published Release sees it (one applied binding and three sidecars per honored map), the fail-closed
 * cases a full export has to reject, and the byte invariance of a request that declares no guide.</p>
 */
class HeightGuideMacroFoundationV2Test {
    private static final Path DIAGNOSTIC = Path.of("examples/v2/diagnostic");
    private static final SurfaceBaselineV2 BASELINE =
            new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46);
    /**
     * Manifest checksum of the unguided 64×64 fixture. V2-19-06 pinned it to prove that relaxing the
     * constraint-map cardinality changed no output; V2-19-07 re-measured it once, because registering
     * the V2-9-02 plain module in the built-in catalog adds its four field descriptors to
     * <em>every</em> blueprint and therefore to every manifest (a container-byte change, ADR 0038 D9).
     * The terrain semantic checksums the tiles carry are unaffected, which
     * {@link #anUnguidedReleaseKeepsItsTerrainSemanticChecksums} measures separately, and the pin's
     * job is unchanged: a request that declares no guide must still publish exactly these bytes.
     */
    private static final String UNGUIDED_MANIFEST_CHECKSUM =
            "4456ae847d0f6a7733284832404174cffdadd0d4e258edeca33aa22ebcc20920";

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
    void theGuidedReleasePublishesOneBindingAndThreeSidecarsPerHonoredMap(@TempDir Path root)
            throws Exception {
        Path release = export(root, "harbor-cove-64-honored-guided", "guided-export");

        ConstraintFieldIndexV2 index = readIndex(release);
        assertEquals(List.of("coast-height-binding", "coast-mask-binding"),
                index.bindings().stream().map(ConstraintFieldIndexV2.AppliedBinding::bindingId).toList());
        assertEquals(6, index.fields().size());
        ConstraintFieldIndexV2.AppliedBinding height = index.bindings().stream()
                .filter(binding -> binding.role() == TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE)
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("constraint.coast.height.actual", "constraint.coast.height.desired",
                "constraint.coast.height.residual"), height.fieldIds());
        assertEquals("constraint.coast.height.desired", height.canonicalFieldId());
        assertTrue(height.canonicalArtifactId().startsWith("constraint:height-guide:sha256-"));
        // The sealed intent binding still references the declared INPUT digest, which is what the
        // Release verifier compares each declared source against.
        GenerationRequestV2 sealedRequest =
                codec.readGenerationRequest(release.resolve("source/generation-request.json"));
        TerrainIntentV2 sealedIntent = codec.readTerrainIntent(release.resolve("source/terrain-intent.json"));
        for (TerrainIntentV2.ConstraintMapBinding binding : sealedIntent.mapReferences()) {
            String declaredDigest = sealedRequest.constraintMaps().stream()
                    .filter(source -> source.sourceId().equals(binding.sourceId()))
                    .findFirst().orElseThrow().expectedSha256();
            assertTrue(binding.artifactId().endsWith(declaredDigest), binding.artifactId());
        }
    }

    @Test
    void aRequestWithoutAGuideStillPublishesTheSamePreV21906Bytes(@TempDir Path root) throws Exception {
        Path release = export(root, "harbor-cove-64-honored", "unguided-export");

        ConstraintFieldIndexV2 index = readIndex(release);
        assertEquals(1, index.bindings().size());
        assertEquals(3, index.fields().size());
        assertEquals(UNGUIDED_MANIFEST_CHECKSUM,
                new ReleaseSurfaceVerifierV2().verify(release).manifest().canonicalChecksum());
    }

    @Test
    void aHardGuideThatDisagreesWithASurfaceModifierIsRejected(@TempDir Path root) throws Exception {
        // The coastal modifiers own the height of the cells they claim (ADR 0038 D5-3), so a HARD
        // guide over those same cells is two declared sources for one height. Fail closed rather than
        // letting one of them silently win. The guide has to be no-data free, because a HARD binding
        // already refuses no-data at bind time (V2-18-06).
        Path workspace = relocateGuided(root);
        Path request = workspace.resolve("hard-request-v2.json");
        codec.writeGenerationRequest(request, withCompleteHardGuide(
                codec.readGenerationRequest(workspace.resolve("request-v2.json")), workspace));
        Path intent = workspace.resolve("intent.json");
        codec.writeTerrainIntent(intent, withGuideStrength(
                codec.readTerrainIntent(workspace.resolve("terrain-intent-v2.json")),
                TerrainIntentV2.Strength.HARD, 0, 0));

        IOException rejected = assertThrows(IOException.class,
                () -> exportWorkspace(root, request, intent, "hard-guide-export"));

        assertTrue(rejected.getMessage().contains(MacroFoundationV2.RULE_HEIGHT_GUIDE_MODIFIER_CONFLICT),
                rejected::getMessage);
        assertFalse(Files.exists(root.resolve("exports").resolve("hard-guide-export")),
                "a rejected export must not leave a published Release behind");
    }

    @Test
    void aGuideWhoseDeclaredRangeLeavesTheRequestExtentNeverReachesGeneration(@TempDir Path root)
            throws Exception {
        // Out of contract, not clamped. The request record itself refuses a height encoding whose
        // declared range decodes outside the vertical extent, so this is caught at declaration time
        // and the consumer's own per-cell guard (MacroFoundationV2.RULE_HEIGHT_GUIDE_OUT_OF_CONTRACT,
        // exercised in MacroFoundationStageV2Test) stays defence in depth. Raising the floor to 44
        // keeps the declared base levels valid and puts the guide's declared range (40..60 blocks)
        // partly below the request's own extent.
        Path workspace = relocateGuided(root);
        GenerationRequestV2 guided = codec.readGenerationRequest(workspace.resolve("request-v2.json"));

        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> withBounds(guided, new GenerationRequestV2.Bounds(64, 64, 44, 72, 50)));

        assertTrue(rejected.getMessage().contains("height encoding output is outside request bounds"),
                rejected::getMessage);
    }

    @Test
    void aDeclaredSourceNoBindingConsumesIsRejected(@TempDir Path root) throws Exception {
        // The guide is declared by the request but bound by nothing, so no field sidecar could carry
        // its provenance. Accepting it would ship a source the Release cannot verify.
        Path workspace = relocateGuided(root);
        Path intent = workspace.resolve("intent.json");
        TerrainIntentV2 guided = codec.readTerrainIntent(workspace.resolve("terrain-intent-v2.json"));
        codec.writeTerrainIntent(intent, withMapReferences(guided, guided.mapReferences().stream()
                .filter(binding -> binding.role() == TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK)
                .toList()));

        IOException rejected = assertThrows(IOException.class, () -> exportWorkspace(
                root, workspace.resolve("request-v2.json"), intent, "unbound-source-export"));

        assertTrue(rejected.getMessage().contains("every declared constraint map source"),
                rejected::getMessage);
    }

    private Path export(Path root, String fixture, String releaseId) throws Exception {
        return exportWorkspace(root, DIAGNOSTIC.resolve(fixture + ".request-v2.json"),
                DIAGNOSTIC.resolve(fixture + ".terrain-intent-v2.json"), releaseId);
    }

    private Path exportWorkspace(Path root, Path request, Path intent, String releaseId) throws Exception {
        return new Release2ExportApplicationServiceV2(executors).exportNow(new Release2ExportRequestV2(
                        request, intent, root.resolve("work").resolve(releaseId),
                        root.resolve("exports"), releaseId, BASELINE, false,
                        new ExportBudgetV2(ExportBudgetV2.MAXIMUM_RELEASE_TILES, 256L * 1024L * 1024L, 1L),
                        Optional.empty()))
                .releaseDirectory();
    }

    /** Copies the guided fixture and its two maps so a modified intent can be exported beside them. */
    private static Path relocateGuided(Path root) throws IOException {
        Path workspace = Files.createDirectories(root.resolve("guided"));
        Files.createDirectories(workspace.resolve("maps"));
        for (String map : List.of("harbor-cove-64-honored-land-water-u8.png",
                "harbor-cove-64-honored-height-guide-u8.png")) {
            Files.copy(DIAGNOSTIC.resolve("maps").resolve(map), workspace.resolve("maps").resolve(map));
        }
        Files.copy(DIAGNOSTIC.resolve("harbor-cove-64-honored-guided.request-v2.json"),
                workspace.resolve("request-v2.json"));
        Files.copy(DIAGNOSTIC.resolve("harbor-cove-64-honored-guided.terrain-intent-v2.json"),
                workspace.resolve("terrain-intent-v2.json"));
        return workspace;
    }

    /**
     * Re-declares the guide as a map with no no-data cells: the same terraces, with the example's
     * no-data patch filled in from its own medium, written beside the relocated request.
     */
    private static GenerationRequestV2 withCompleteHardGuide(
            GenerationRequestV2 request,
            Path workspace
    ) throws IOException {
        int[] mask = HeightGuideExampleFixtureV2.maskSamples();
        byte[] samples = new byte[HeightGuideExampleFixtureV2.WIDTH * HeightGuideExampleFixtureV2.LENGTH];
        for (int z = 0; z < HeightGuideExampleFixtureV2.LENGTH; z++) {
            for (int x = 0; x < HeightGuideExampleFixtureV2.WIDTH; x++) {
                int index = z * HeightGuideExampleFixtureV2.WIDTH + x;
                int terrace = Math.min(3, z / 16);
                samples[index] = (byte) (HeightGuideExampleFixtureV2.isNoDataCell(x, z)
                        ? (mask[index] == 1 ? HeightGuideExampleFixtureV2.LAND_CREST_SAMPLE - terrace
                                : HeightGuideExampleFixtureV2.WATER_SHALLOW_SAMPLE - terrace)
                        : HeightGuideExampleFixtureV2.sampleAt(x, z, mask[index]));
            }
        }
        Path file = workspace.resolve("maps").resolve("hard-height-guide-u8.png");
        new NumericPngEncoder().writeU8(file, HeightGuideExampleFixtureV2.WIDTH,
                HeightGuideExampleFixtureV2.LENGTH, samples);

        List<GenerationRequestV2.ConstraintMapSource> sources = new ArrayList<>();
        for (GenerationRequestV2.ConstraintMapSource source : request.constraintMaps()) {
            if (!(source.encoding() instanceof GenerationRequestV2.HeightEncoding encoding)) {
                sources.add(source);
                continue;
            }
            sources.add(new GenerationRequestV2.ConstraintMapSource(
                    source.sourceId(), "maps/hard-height-guide-u8.png", Sha256.file(file),
                    source.expectedWidth(), source.expectedLength(), source.decoderKind(),
                    source.coordinateMapping(),
                    new GenerationRequestV2.HeightEncoding(
                            encoding.encodingVersion(), encoding.sampleType(), encoding.channel(),
                            encoding.valueMeaning(), encoding.valueScaleMillionths(),
                            encoding.valueOffsetMillionths(), encoding.validSampleRange(),
                            new GenerationRequestV2.NoDataForbidden())));
        }
        return withConstraintMaps(request, sources);
    }

    private static GenerationRequestV2 withConstraintMaps(
            GenerationRequestV2 request,
            List<GenerationRequestV2.ConstraintMapSource> sources
    ) {
        return new GenerationRequestV2(
                request.requestVersion(), request.requestId(), request.bounds(), request.prompt(),
                request.referenceImages(), sources, request.generation(),
                request.constraintMapBudget(), request.foundationBaseLevels());
    }

    private static GenerationRequestV2 withBounds(
            GenerationRequestV2 request,
            GenerationRequestV2.Bounds bounds
    ) {
        return new GenerationRequestV2(
                request.requestVersion(), request.requestId(), bounds, request.prompt(),
                request.referenceImages(), request.constraintMaps(), request.generation(),
                request.constraintMapBudget(), request.foundationBaseLevels());
    }

    private static TerrainIntentV2 withGuideStrength(
            TerrainIntentV2 intent,
            TerrainIntentV2.Strength strength,
            int toleranceBlocks,
            int weightMillionths
    ) {
        List<TerrainIntentV2.ConstraintMapBinding> bindings = new ArrayList<>();
        for (TerrainIntentV2.ConstraintMapBinding binding : intent.mapReferences()) {
            bindings.add(binding.role() == TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE
                    ? new TerrainIntentV2.ConstraintMapBinding(binding.id(), binding.sourceId(),
                            binding.role(), binding.artifactId(), strength, binding.sampling(),
                            toleranceBlocks, weightMillionths)
                    : binding);
        }
        return withMapReferences(intent, bindings);
    }

    private static TerrainIntentV2 withMapReferences(
            TerrainIntentV2 intent,
            List<TerrainIntentV2.ConstraintMapBinding> bindings
    ) {
        return new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                intent.features(), intent.relations(), intent.constraints(), intent.environment(),
                bindings, intent.structures(), intent.provenance());
    }

    private static ConstraintFieldIndexV2 readIndex(Path release) throws IOException {
        Path constraintRoot = release.resolve("constraints");
        return new ConstraintFieldIndexCodecV2()
                .readAndVerify(constraintRoot.resolve("index.json"), constraintRoot);
    }
}
