package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-14 production evidence for ADR 0043: the mask ⇔ feature reconcile pre-pass.
 *
 * <p>The 2026-07-23 cross-cutting audit (§4.2-4) measured the mask ⇔ seed binding — the HARD
 * land-water mask has to agree with the generated coastal raster cell for cell, so the only working
 * procedure was to regenerate the mask whenever geometry or seed changed. These tests pin the
 * relaxation of that binding <em>and</em> the invariants it must not disturb: the mask is never
 * moved, no gate is relaxed, and geometry that already agrees is never touched.</p>
 */
class MaskFeatureReconcileV2Test {
    private static final Path DIAGNOSTIC = Path.of("examples/v2/diagnostic");
    private static final SurfaceBaselineV2 BASELINE =
            new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46);
    /**
     * The four-contributor fixture's canonical block stream identity (ADR 0040 D6 absolute pin). The
     * drift case must reproduce it exactly: the pre-pass restores the authored geometry, so the
     * published blocks are the authored fixture's blocks, not merely "close enough".
     */
    private static final String HONORED_TILE_SEMANTIC_CHECKSUM =
            "20318e6cef8e224a1c369292e354b9116b3bb706f5fc524b7a1b4dfb343b48b2";
    /** The declared drift of the fixture: two blocks south of the mask it must honor. */
    private static final int DRIFT_BLOCKS_Z = 2;

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
    void theCommittedDriftFixtureIsTheHonoredGeometryMovedTwoBlocksSouth() throws Exception {
        TerrainIntentV2 honored = codec.readTerrainIntent(intentOf("harbor-cove-64-honored"));
        TerrainIntentV2 drift = codec.readTerrainIntent(intentOf("harbor-cove-64-honored-drift"));
        long delta = MaskFeatureReconcileStageV2.normalizedDelta(DRIFT_BLOCKS_Z, 64);

        // The fixture is not hand-drawn: it is exactly the authored geometry plus the pre-pass's own
        // normalized delta, which is what makes "the pre-pass restored it" a bit-exact claim below.
        assertEquals(honored.features().size(), drift.features().size());
        for (int index = 0; index < honored.features().size(); index++) {
            TerrainIntentV2.Feature source = honored.features().get(index);
            TerrainIntentV2.Feature moved = drift.features().get(index);
            assertEquals(source.id(), moved.id());
            List<TerrainIntentV2.Point2> sourcePoints = controlPoints(source.geometry());
            List<TerrainIntentV2.Point2> movedPoints = controlPoints(moved.geometry());
            assertEquals(sourcePoints.size(), movedPoints.size(), source.id());
            for (int point = 0; point < sourcePoints.size(); point++) {
                assertEquals(sourcePoints.get(point).xMillionths(),
                        movedPoints.get(point).xMillionths(), source.id());
                assertEquals(sourcePoints.get(point).zMillionths() + delta,
                        movedPoints.get(point).zMillionths(), source.id());
            }
        }
    }

    @Test
    void theNormalizedDeltaIsExactlySymmetricAboutZero() {
        // The reason a round trip restores the author's coordinates bit for bit rather than "almost".
        for (int extent : new int[] {2, 33, 64, 65, 400, 1024}) {
            assertEquals(0L, MaskFeatureReconcileStageV2.normalizedDelta(0, extent));
            for (int blocks = 1; blocks <= 8; blocks++) {
                long forward = MaskFeatureReconcileStageV2.normalizedDelta(blocks, extent);
                long back = MaskFeatureReconcileStageV2.normalizedDelta(-blocks, extent);
                assertEquals(0L, forward + back, extent + "/" + blocks);
                assertTrue(forward > 0L, extent + "/" + blocks);
            }
        }
    }

    @Test
    void theDriftedGeometryIsRejectedWithoutThePrePass(@TempDir Path root) throws Exception {
        Path request = workspace(root, "unreconciled", "harbor-cove-64-honored-drift",
                declared -> withReconcile(declared, Optional.empty()));

        IOException failure = assertThrows(IOException.class,
                () -> export(root, request, intentOf("harbor-cove-64-honored-drift"), "unreconciled"));

        // The pre-pass is what makes the drifted fixture exportable; without it the mask and the
        // features contradict each other and the existing HARD gate says so.
        assertTrue(messageChain(failure).contains("v2.coastal-transition-hard-conflict"),
                () -> "unexpected failure: " + messageChain(failure));
    }

    @Test
    void thePrePassRestoresTheAuthoredGeometryExactly(@TempDir Path root) throws Exception {
        Release2ExportResultV2 result = export(root, requestOf("harbor-cove-64-honored-drift"),
                intentOf("harbor-cove-64-honored-drift"), "reconciled");

        MaskFeatureReconcileV2 reconcile = result.maskFeatureReconcile().orElseThrow();
        assertTrue(reconcile.applied());
        assertEquals(0, reconcile.offsetXBlocks());
        assertEquals(-DRIFT_BLOCKS_Z, reconcile.offsetZBlocks());
        assertEquals(4, reconcile.toleranceBlocks());
        assertEquals(81L, reconcile.candidateOffsets());
        // The declared geometry really does contradict the mask, and the applied offset removes every
        // one of those contradictions — a reconcile that "improved" nothing would show equal counters.
        assertTrue(reconcile.disagreementBefore() > 0, reconcile::toString);
        assertEquals(0, reconcile.disagreementAfter(), reconcile::toString);
        assertTrue(reconcile.evaluatedCells() > 0, reconcile::toString);
        // Pinned, so a change in the objective or the candidate order is something to look at: 958
        // coastal-active cells, 151 of which contradict the mask as declared and none after the snap.
        assertEquals(958, reconcile.evaluatedCells(), reconcile::toString);
        assertEquals(151, reconcile.disagreementBefore(), reconcile::toString);
        // 65 of the 81 candidates move an active cell out of the domain — the coastal composition
        // touches the world edge — and are rejected rather than evaluated on fabricated cells.
        assertEquals(65L, reconcile.rejectedOffsets(), reconcile::toString);
        assertEquals(5, reconcile.translatedFeatures());

        // The sealed intent carries the reconciled geometry (ADR 0043 D5) and it is the authored
        // geometry, control point for control point.
        TerrainIntentV2 published = codec.readTerrainIntent(
                result.releaseDirectory().resolve("source/terrain-intent.json"));
        TerrainIntentV2 honored = codec.readTerrainIntent(intentOf("harbor-cove-64-honored"));
        assertEquals(geometries(honored), geometries(published));

        // …and therefore so is the published canonical block stream, measured against the absolute pin
        // of the fixture the drift was derived from.
        assertEquals(List.of(HONORED_TILE_SEMANTIC_CHECKSUM),
                tileSemanticChecksums(result.releaseDirectory()));
    }

    @Test
    void aToleranceTooSmallLeavesTheRunRejectedByTheExistingGate(@TempDir Path root) throws Exception {
        Path request = workspace(root, "narrow", "harbor-cove-64-honored-drift",
                declared -> withReconcile(declared,
                        Optional.of(new GenerationRequestV2.MaskFeatureReconcile(1))));

        IOException failure = assertThrows(IOException.class,
                () -> export(root, request, intentOf("harbor-cove-64-honored-drift"), "narrow"));

        // ADR 0043 D4: an out-of-tolerance drift is rejected by the pre-existing rule, not by a new
        // reconcile-specific one. The pre-pass adds no rejection of its own.
        String message = messageChain(failure);
        assertTrue(message.contains("v2.coastal-transition-hard-conflict"),
                () -> "unexpected failure: " + message);
        assertFalse(message.contains("mask feature reconcile failed"), message);
    }

    @Test
    void geometryThatAlreadyAgreesWithTheMaskIsNeverMoved(@TempDir Path root) throws Exception {
        Path request = workspace(root, "agreeing", "harbor-cove-64-honored",
                declared -> withReconcile(declared,
                        Optional.of(new GenerationRequestV2.MaskFeatureReconcile(4))));

        Release2ExportResultV2 result =
                export(root, request, intentOf("harbor-cove-64-honored"), "agreeing");

        MaskFeatureReconcileV2 reconcile = result.maskFeatureReconcile().orElseThrow();
        assertFalse(reconcile.applied(), reconcile::toString);
        assertEquals(0, reconcile.offsetXBlocks());
        assertEquals(0, reconcile.offsetZBlocks());
        assertEquals(0, reconcile.disagreementBefore(), reconcile::toString);
        assertEquals(0, reconcile.disagreementAfter(), reconcile::toString);
        assertEquals(0, reconcile.translatedFeatures());
        // The tie-break makes this a property of the ordering, not a coincidence: declaring the
        // pre-pass on an already-agreeing fixture produces the same canonical block stream.
        assertEquals(List.of(HONORED_TILE_SEMANTIC_CHECKSUM),
                tileSemanticChecksums(result.releaseDirectory()));
    }

    @Test
    void thePrePassNeverMovesTheDeclaredMask(@TempDir Path root) throws Exception {
        Release2ExportResultV2 result = export(root, requestOf("harbor-cove-64-honored-drift"),
                intentOf("harbor-cove-64-honored-drift"), "one-way");

        // ADR 0043 凍結1: the correction is one-way. The published request still declares the same
        // map digest, and the published intent still carries the untouched binding.
        GenerationRequestV2 declared = codec.readGenerationRequest(
                requestOf("harbor-cove-64-honored-drift"));
        GenerationRequestV2 sealed = codec.readGenerationRequest(
                result.releaseDirectory().resolve("source/generation-request.json"));
        assertEquals(declared.constraintMaps(), sealed.constraintMaps());
        assertEquals(codec.readGenerationRequest(requestOf("harbor-cove-64-honored"))
                        .constraintMaps().getFirst().expectedSha256(),
                sealed.constraintMaps().getFirst().expectedSha256());
        TerrainIntentV2 published = codec.readTerrainIntent(
                result.releaseDirectory().resolve("source/terrain-intent.json"));
        TerrainIntentV2 drift = codec.readTerrainIntent(intentOf("harbor-cove-64-honored-drift"));
        assertEquals(drift.mapReferences().size(), published.mapReferences().size());
        assertEquals(drift.mapReferences().getFirst().id(),
                published.mapReferences().getFirst().id());
        // V2-18-07 binds the published artifactId to the declared INPUT digest; the pre-pass leaves
        // that digest — and therefore the honored fixture's own mask — exactly as declared.
        assertEquals("constraint:land-water:sha256-"
                        + sealed.constraintMaps().getFirst().expectedSha256(),
                published.mapReferences().getFirst().artifactId());
        // …while the geometry it publishes is emphatically not what the author declared.
        assertNotEquals(geometries(drift), geometries(published));
    }

    @Test
    void theReconciledReleaseIsDeterministicAcrossLocaleAndTimezone(@TempDir Path root)
            throws Exception {
        Release2ExportResultV2 first = export(root, requestOf("harbor-cove-64-honored-drift"),
                intentOf("harbor-cove-64-honored-drift"), "determinism-a");
        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        Release2ExportResultV2 second;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            second = export(root, requestOf("harbor-cove-64-honored-drift"),
                    intentOf("harbor-cove-64-honored-drift"), "determinism-b");
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(zone);
        }

        assertEquals(first.maskFeatureReconcile(), second.maskFeatureReconcile());
        assertEquals(first.blueprintChecksum(), second.blueprintChecksum());
        assertEquals(tileSemanticChecksums(first.releaseDirectory()),
                tileSemanticChecksums(second.releaseDirectory()));
    }

    @Test
    void theDeclarationIsRejectedWithoutAnExplicitFoundationInput() throws Exception {
        GenerationRequestV2 declared = codec.readGenerationRequest(requestOf("harbor-cove-64-honored"));

        // ADR 0043 D3: the pre-pass aligns against the HARD mask of the explicit foundation input, so
        // it is meaningless — and rejected at declaration time — without the declared base levels.
        assertThrows(IllegalArgumentException.class, () -> new GenerationRequestV2(
                declared.requestVersion(), declared.requestId(), declared.bounds(), declared.prompt(),
                declared.referenceImages(), declared.constraintMaps(), declared.generation(),
                declared.constraintMapBudget(), Optional.empty(), Optional.empty(),
                Optional.of(new GenerationRequestV2.MaskFeatureReconcile(4))));
    }

    @Test
    void theEvaluationBudgetIsCheckedWhenTheRequestIsDeclared() throws Exception {
        GenerationRequestV2 declared = codec.readGenerationRequest(requestOf("harbor-cove-64-honored"));
        GenerationRequestV2.Bounds medium = new GenerationRequestV2.Bounds(1024, 1024, 32, 72, 50);

        // ADR 0043 D6: domain × candidate count is bounded up front and rejected, never clamped.
        assertThrows(IllegalArgumentException.class, () -> new GenerationRequestV2(
                declared.requestVersion(), declared.requestId(), medium, declared.prompt(),
                List.of(), List.of(), declared.generation(), declared.constraintMapBudget(),
                declared.foundationBaseLevels(), Optional.empty(),
                Optional.of(new GenerationRequestV2.MaskFeatureReconcile(6))));
        // …and the largest affordable tolerance for the same domain is accepted.
        assertEquals(5, new GenerationRequestV2(
                declared.requestVersion(), declared.requestId(), medium, declared.prompt(),
                List.of(), List.of(), declared.generation(), declared.constraintMapBudget(),
                declared.foundationBaseLevels(), Optional.empty(),
                Optional.of(new GenerationRequestV2.MaskFeatureReconcile(5)))
                .maskFeatureReconcile().orElseThrow().toleranceBlocks());
    }

    @Test
    void theDeclaredToleranceStaysInsideItsContract() {
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.MaskFeatureReconcile(0));
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.MaskFeatureReconcile(9));
        assertEquals(81L, new GenerationRequestV2.MaskFeatureReconcile(4).candidateCount());
    }

    private static GenerationRequestV2 withReconcile(
            GenerationRequestV2 declared,
            Optional<GenerationRequestV2.MaskFeatureReconcile> reconcile
    ) {
        return new GenerationRequestV2(
                declared.requestVersion(), declared.requestId(), declared.bounds(), declared.prompt(),
                declared.referenceImages(), declared.constraintMaps(), declared.generation(),
                declared.constraintMapBudget(), declared.foundationBaseLevels(),
                declared.foundationDetail(), reconcile);
    }

    /**
     * Copies one diagnostic fixture's request and its constraint maps into a scratch directory so a
     * variant request can be written next to the same relative map path the fixture declares.
     */
    private Path workspace(
            Path root,
            String name,
            String fixture,
            UnaryOperator<GenerationRequestV2> mutator
    ) throws IOException {
        Path directory = Files.createDirectories(root.resolve("workspace").resolve(name));
        Path maps = Files.createDirectories(directory.resolve("maps"));
        try (Stream<Path> sources = Files.list(DIAGNOSTIC.resolve("maps"))) {
            for (Path map : sources.sorted().toList()) {
                Files.copy(map, maps.resolve(map.getFileName().toString()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path request = directory.resolve(fixture + ".request-v2.json");
        codec.writeGenerationRequest(request, mutator.apply(codec.readGenerationRequest(requestOf(fixture))));
        return request;
    }

    private Release2ExportResultV2 export(Path root, Path request, Path intent, String releaseId)
            throws Exception {
        return new Release2ExportApplicationServiceV2(executors).exportNow(new Release2ExportRequestV2(
                request, intent, root.resolve("work").resolve(releaseId),
                root.resolve("exports").resolve(releaseId), releaseId, BASELINE));
    }

    private static Path requestOf(String fixture) {
        return DIAGNOSTIC.resolve(fixture + ".request-v2.json");
    }

    private static Path intentOf(String fixture) {
        return DIAGNOSTIC.resolve(fixture + ".terrain-intent-v2.json");
    }

    private static List<TerrainIntentV2.Geometry> geometries(TerrainIntentV2 intent) {
        return intent.features().stream().map(TerrainIntentV2.Feature::geometry).toList();
    }

    private static List<TerrainIntentV2.Point2> controlPoints(TerrainIntentV2.Geometry geometry) {
        return switch (geometry) {
            case TerrainIntentV2.PointGeometry point -> List.of(point.point());
            case TerrainIntentV2.MultiPointGeometry multi ->
                    multi.points().stream().map(TerrainIntentV2.NamedPoint::point).toList();
            case TerrainIntentV2.SplineGeometry spline -> spline.points();
            case TerrainIntentV2.MultiSplineGeometry multi ->
                    multi.paths().stream().flatMap(path -> path.points().stream()).toList();
            case TerrainIntentV2.PolygonGeometry polygon ->
                    polygon.rings().stream().flatMap(List::stream).toList();
            case TerrainIntentV2.VolumeGuideGeometry volume ->
                    volume.footprint().rings().stream().flatMap(List::stream).toList();
        };
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

    private static String messageChain(Throwable failure) {
        StringBuilder message = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            message.append(cause.getMessage()).append('\n');
        }
        return message.toString();
    }
}
