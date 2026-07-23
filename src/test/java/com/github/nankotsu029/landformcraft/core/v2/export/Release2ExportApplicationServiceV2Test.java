package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.design.DesignDispatchRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.design.TerrainDesignApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.PlacementPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.VerifiedReleaseCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactsV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfaceVerifierV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-12-02 offline end-to-end evidence: request fixture → production export API → strict Release 2
 * verify → placement eligibility → placement plan. No world is mutated.
 */
class Release2ExportApplicationServiceV2Test {
    // V2-18-03: the HARD preflight gate now rejects azure-coast (unresolved LAND_WATER_MASK, HARD
    // EDGE_CLASSIFICATION with no evaluator, HARD relation to contract-only BACKSHORE_PLAINS). The
    // gate-passing "honored" fixture keeps the same coastal geometry but declares those not-yet-
    // enforceable requirements as SOFT and ships a resolvable mask.
    private static final Path REQUEST = Path.of("examples/v2/diagnostic/coastal-honored-400.request-v2.json");
    private static final Path INTENT = Path.of("examples/v2/diagnostic/coastal-honored-400.terrain-intent-v2.json");
    private static final SurfaceBaselineV2 BASELINE =
            new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 42);
    /** 64×64 honored fixture: the same coastal geometry at a fraction of the generation cost. */
    private static final Path REQUEST_64 =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json");
    private static final Path INTENT_64 =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json");
    private static final SurfaceBaselineV2 BASELINE_64 =
            new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46);

    private static GenerationExecutors executors;

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
    void exportPublishesAPlacementEligibleReleaseAndPlansIt(@TempDir Path root) throws Exception {
        Release2ExportResultV2 result = service().exportNow(request(root.resolve("run"), "azure-coast-export"));

        assertEquals("azure-coast-export", result.releaseId());
        assertEquals(List.of("surface-2_5d"), result.requiredCapabilities());
        assertEquals(16, result.tileIds().size());
        assertTrue(result.zip().isPresent());
        assertTrue(result.eligibility().eligible());
        assertEquals(result.manifestChecksum(), result.eligibility().manifestChecksum());

        // The published container re-verifies independently of the publishing process.
        assertEquals(result.manifestChecksum(),
                new ReleaseSurfaceVerifierV2().verify(result.releaseDirectory()).manifest().canonicalChecksum());
        assertEquals(result.manifestChecksum(),
                new ReleaseSurfaceVerifierV2().verify(result.zip().orElseThrow()).manifest().canonicalChecksum());

        try (VerifiedReleaseCanonicalBlockSourceV2 source =
                     VerifiedReleaseCanonicalBlockSourceV2.open(result.releaseDirectory(), () -> false)) {
            var layout = source.layout();
            assertEquals(400, layout.width());
            assertEquals(400, layout.length());
            assertEquals(result.manifestChecksum(), source.binding().releaseManifestChecksum());

            TilePlanV2 tilePlan = TilePlanV2.of(layout.width(), layout.length(),
                    ScaleProfileV2.defaults(ScaleClassV2.forDimensions(layout.width(), layout.length())));
            PlacementPlanV2 plan = new PlacementPlanCompilerV2().compile(
                    new PlacementPlanCompilerV2.PlacementCompileRequestV2(
                            UUID.randomUUID(), UUID.randomUUID(), "release2-export-e2e",
                            actor(), target(layout.width(), layout.length(), layout.maxY() - layout.minY()),
                            new PlacementPlanV2.ReleaseBindingV2(
                                    PlacementPlanV2.ReleaseBindingV2.VERSION, 2,
                                    result.releaseDirectory().getFileName().toString(),
                                    source.binding().releaseManifestChecksum(),
                                    PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION),
                            source.binding().requiredCapabilities(), tilePlan)).plan();

            assertEquals(result.tileIds().size(), plan.tileOrder().tiles().size());
            new ReleasePlacementEligibilityVerifierV2().requirePlanMatches(result.eligibility(), plan);
        }
    }

    @Test
    void exportCarriesAReportOnlyIntentContributionCoverageThatNeverAffectsChecksums(
            @TempDir Path root) throws Exception {
        Release2ExportResultV2 result = service().exportNow(request(root.resolve("run"), "coverage-report"));

        assertTrue(result.intentContributionCoverage().isPresent());
        IntentContributionCoverageV2 coverage = result.intentContributionCoverage().orElseThrow();
        assertEquals(List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS), coverage.contractOnlyKinds());
        // The honored fixture declares the EDGE / beach-width constraints and the backshore adjacency
        // as SOFT, so they are no longer reported as unconsumed HARD requirements.
        assertEquals(List.of(), coverage.unconsumedHardConstraints());
        assertEquals(List.of(), coverage.unconsumedHardRelations());
        // V2-18-09: the macro foundation stage is the permanent LAND_WATER_MASK consumer, so the
        // HARD map reference is no longer reported as unconsumed (audit item 2a closed).
        assertEquals(List.of(), coverage.unconsumedHardMapReferences());
        assertEquals(160_000, coverage.totalCells());
        assertTrue(coverage.activeContributorCoverageMillionths() > 0
                && coverage.activeContributorCoverageMillionths() < 1_000_000);
        // The two coverage metrics diverge exactly as V2-18-02 designed: the mask-derived background
        // owner covers every cell while active coastal modifiers still claim only their footprints.
        assertEquals(1_000_000, coverage.surfaceFoundationOwnerCoverageMillionths());
        assertEquals(160_000, coverage.surfaceFoundationOwnerCells());
        assertTrue(coverage.activeContributorCoverageMillionths()
                < coverage.surfaceFoundationOwnerCoverageMillionths());
        // ADR 0038 D8-1: the ignored surface-baseline argument surfaces as a NON_GATING warning.
        assertEquals(List.of(ExportWarningV2.RULE_SURFACE_BASELINE_DEPRECATED),
                result.warnings().stream().map(ExportWarningV2::ruleId).toList());

        // Report-only: an export from the same inputs (same releaseId, separate work/exports roots)
        // is byte-identical whether or not the coverage report is inspected, because it is never
        // written into the blueprint or manifest.
        Release2ExportResultV2 second = service().exportNow(request(root.resolve("run2"), "coverage-report"));
        assertEquals(result.blueprintChecksum(), second.blueprintChecksum());
        assertEquals(result.manifestChecksum(), second.manifestChecksum());
    }

    @Test
    void exportBindsTheLandWaterDesiredReferenceToTheInputMaskDigest(@TempDir Path root) throws Exception {
        // V2-18-07: the sealed intent's LAND_WATER_MASK binding must carry the declared INPUT mask digest,
        // not the generated field's own checksum (the old self-reference). This is verifiable directly on
        // the published source intent and through the conformance target set's provenance classification.
        LandformV2DataCodec codec = new LandformV2DataCodec();
        String inputDigest = codec.readGenerationRequest(REQUEST).constraintMaps().getFirst().expectedSha256();

        Release2ExportResultV2 result = service().exportNow(request(root.resolve("run"), "mask-binding"));

        TerrainIntentV2 sealedIntent = codec.readTerrainIntent(
                result.releaseDirectory().resolve("source/terrain-intent.json"));
        TerrainIntentV2.ConstraintMapBinding binding = sealedIntent.mapReferences().getFirst();
        assertEquals("constraint:land-water:sha256-" + inputDigest, binding.artifactId());

        com.github.nankotsu029.landformcraft.validation.v2.conformance.ConformanceTargetSetV2 conformance =
                com.github.nankotsu029.landformcraft.validation.v2.conformance.ConformanceTargetSetV2.from(
                        List.of(), sealedIntent.mapReferences(),
                        java.util.Map.of(binding.sourceId(), inputDigest));
        assertEquals(1, conformance.desiredRasterTargets().size());
        assertEquals(
                com.github.nankotsu029.landformcraft.validation.v2.conformance.ConformanceProvenanceV2.Origin.INPUT_MASK,
                conformance.desiredRasterTargets().getFirst().provenance().origin());
    }

    @Test
    void designOutputFeedsTheExportPathDirectly(@TempDir Path root) throws Exception {
        Path workspace = Files.createDirectories(root.resolve("workspace"));
        Files.copy(REQUEST, workspace.resolve("request-v2.json"));
        Files.copy(INTENT, workspace.resolve("terrain-intent-v2.json"));
        // The relocated request resolves its LAND_WATER_MASK relative to its own directory, so the
        // preflight gate needs the mask copied alongside it.
        Path workspaceMaps = Files.createDirectories(workspace.resolve("maps"));
        Files.copy(REQUEST.resolveSibling("maps").resolve("coastal-honored-400-land-water-u8.png"),
                workspaceMaps.resolve("coastal-honored-400-land-water-u8.png"));

        DesignArtifactsV2 design = new TerrainDesignApplicationServiceV2(executors, null)
                .design(new DesignDispatchRequestV2(
                        2,
                        DesignPathKindV2.FIXTURE,
                        EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                        workspace.resolve("request-v2.json"),
                        root.resolve("designs"),
                        "terrain-intent-v2.json",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()))
                .get(60, TimeUnit.SECONDS);

        Release2ExportResultV2 result = service().exportNow(Release2ExportRequestV2.fromDesign(
                workspace.resolve("request-v2.json"), design,
                root.resolve("work"), root.resolve("exports"), "from-design", BASELINE));

        assertEquals(List.of("surface-2_5d"), result.requiredCapabilities());
        assertTrue(result.eligibility().eligible());
    }

    @Test
    void sameInputsProduceTheSameBlueprintAndReleaseChecksums(@TempDir Path root) throws Exception {
        Release2ExportResultV2 first = service().exportNow(request(root.resolve("first"), "determinism"));
        Release2ExportResultV2 second = service().exportNow(request(root.resolve("second"), "determinism"));

        assertEquals(first.blueprintChecksum(), second.blueprintChecksum());
        assertEquals(first.manifestChecksum(), second.manifestChecksum());
        assertEquals(first.tileIds(), second.tileIds());
    }

    @Test
    void cancellationLeavesNoPublishedReleaseOrStagingResidue(@TempDir Path root) throws Exception {
        Path run = root.resolve("cancelled");
        Path workRoot = run.resolve("work");
        Path exportsRoot = run.resolve("exports");
        // Flips once generation has produced its sealed constraint index, so cleanup runs mid-pipeline.
        CancellationToken token = () -> Files.exists(workRoot.resolve("constraints/index.json"));
        Release2ExportRequestV2 request = new Release2ExportRequestV2(
                REQUEST, INTENT, workRoot, exportsRoot, "cancelled", BASELINE, true,
                ExportBudgetV2.defaults(), Optional.of(token));

        assertThrows(CancellationException.class, () -> service().exportNow(request));
        assertFalse(Files.exists(exportsRoot.resolve("cancelled")));
        assertFalse(Files.exists(exportsRoot.resolve("cancelled.zip")));
        if (Files.exists(exportsRoot)) {
            try (var entries = Files.list(exportsRoot)) {
                assertTrue(entries.noneMatch(
                        path -> path.getFileName().toString().startsWith(".release-v2-surface-")));
            }
        }
    }

    @Test
    void budgetAdmissionRejectsBeforeAnyArtifactIsWritten(@TempDir Path root) throws Exception {
        Path run = root.resolve("budget");
        Path workRoot = run.resolve("work");
        Path exportsRoot = run.resolve("exports");
        Release2ExportRequestV2 request = new Release2ExportRequestV2(
                REQUEST, INTENT, workRoot, exportsRoot, "budget", BASELINE, false,
                new ExportBudgetV2(4, 256L * 1024L * 1024L, 1L), Optional.empty());

        IOException failure = assertThrows(IOException.class, () -> service().exportNow(request));
        assertTrue(failure.getMessage().contains("tile count exceeds its budget"), failure.getMessage());
        assertFalse(Files.exists(workRoot.resolve("tiles")));
        assertFalse(Files.exists(workRoot.resolve("constraints")));
        assertFalse(Files.exists(exportsRoot.resolve("budget")));
    }

    @Test
    void residentBudgetAdmissionRejectsAnOversizedWorkingSet(@TempDir Path root) {
        Release2ExportRequestV2 request = new Release2ExportRequestV2(
                REQUEST, INTENT, root.resolve("work"), root.resolve("exports"), "resident", BASELINE, false,
                new ExportBudgetV2(64, 1_024L, 1L), Optional.empty());

        IOException failure = assertThrows(IOException.class, () -> service().exportNow(request));
        assertTrue(failure.getMessage().contains("working set exceeds its budget"), failure.getMessage());
    }

    @Test
    void unsupportedFeatureIsRejectedByDispatchBeforeWorkArtifacts(@TempDir Path root) throws Exception {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        TerrainIntentV2 intent = codec.readTerrainIntent(INTENT);
        List<TerrainIntentV2.Feature> features = intent.features().stream().map(feature -> {
            if (feature.kind() != TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS) {
                return feature;
            }
            return new TerrainIntentV2.Feature(
                    feature.id(), TerrainIntentV2.FeatureKind.PLAIN, feature.geometry(),
                    new TerrainIntentV2.PlainParameters(
                            new TerrainIntentV2.IntRange(4, 12),
                            new TerrainIntentV2.IntRange(1, 2),
                            new TerrainIntentV2.IntRange(1, 4)),
                    feature.priority(), feature.provenance());
        }).toList();
        TerrainIntentV2 unsupported = new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                features, intent.relations(), intent.constraints(), intent.environment(), intent.mapReferences(),
                intent.structures(), intent.provenance());
        Path intentPath = root.resolve("unsupported-intent.json");
        codec.writeTerrainIntent(intentPath, unsupported);
        Path workRoot = root.resolve("work");
        Path exportsRoot = root.resolve("exports");

        IOException failure = assertThrows(IOException.class, () -> service().exportNow(
                new Release2ExportRequestV2(
                        REQUEST, intentPath, workRoot, exportsRoot, "unsupported", BASELINE)));

        assertTrue(failure.getMessage().contains("no production dispatch route: PLAIN"), failure.getMessage());
        assertTrue(Files.isDirectory(workRoot));
        try (var entries = Files.list(workRoot)) {
            assertTrue(entries.findAny().isEmpty(), "dispatch rejection must precede work artifacts");
        }
        assertFalse(Files.exists(exportsRoot));
    }

    @Test
    void aHardEdgeClassificationViolationRejectsTheExportAfterGeneration(@TempDir Path root) throws Exception {
        // V2-18-04/09: a resolvable-map, otherwise-honorable intent that declares a HARD edge the
        // macro foundation cannot satisfy passes the preflight gate and is rejected by the
        // target-driven EDGE evaluator after generation, before publication. The unsatisfiable edge
        // is EAST (the mask leaves that band mostly water); NORTH/SOUTH now carry the fixture's own
        // HARD requirements since V2-18-11, and a contradicting HARD pair would be a compile error
        // in TerrainIntentV2 rather than a validation rejection.
        Path request64 = Path.of("examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json");
        LandformV2DataCodec codec = new LandformV2DataCodec();
        TerrainIntentV2 base = codec.readTerrainIntent(
                Path.of("examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json"));
        List<TerrainIntentV2.Constraint> constraints = new ArrayList<>(base.constraints());
        constraints.add(new TerrainIntentV2.EdgeClassificationConstraint(
                "east-is-land-hard", TerrainIntentV2.Strength.HARD, "world",
                TerrainIntentV2.Edge.EAST, TerrainIntentV2.EdgeClassification.LAND, 950_000, 0));
        TerrainIntentV2 hardEdge = new TerrainIntentV2(
                base.intentVersion(), base.intentId(), base.theme(), base.coordinateSystem(),
                base.features(), base.relations(), constraints, base.environment(), base.mapReferences(),
                base.structures(), base.provenance());
        Path intentPath = root.resolve("hard-edge-intent.json");
        codec.writeTerrainIntent(intentPath, hardEdge);

        SurfaceBaselineV2 baseline = new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46);
        IOException failure = assertThrows(IOException.class, () -> service().exportNow(
                new Release2ExportRequestV2(request64, intentPath, root.resolve("work"),
                        root.resolve("exports"), "hard-edge-reject", baseline)));

        assertTrue(failure.getMessage().contains("target-driven HARD validation failed"), failure.getMessage());
        assertFalse(Files.exists(root.resolve("exports").resolve("hard-edge-reject")));
    }

    @Test
    void aRequestWithoutAnExplicitFoundationInputIsRejectedByTheOwnerCoverageGate(@TempDir Path root)
            throws Exception {
        // V2-18-10 (ADR 0038 D7-2/D8-2): on the legacy surface-baseline path no cell has a foundation
        // owner — a constant fill is not an owner — so the promoted gate rejects the export after
        // generation and before anything is published. The baseline argument cannot override it.
        Path legacyRequest = LegacyFoundationFreeRequestFixtureV2.write(
                REQUEST_64, root.resolve("legacy"));
        Path workRoot = root.resolve("work");
        Path exportsRoot = root.resolve("exports");

        SurfaceFoundationOwnerRejectedV2 rejected = assertThrows(SurfaceFoundationOwnerRejectedV2.class,
                () -> service().exportNow(new Release2ExportRequestV2(
                        legacyRequest, INTENT_64, workRoot, exportsRoot, "legacy-baseline", BASELINE_64)));

        assertEquals(SurfaceFoundationOwnerGateV2.RULE_FOUNDATION_OWNER_COVERAGE_INCOMPLETE,
                rejected.ruleId());
        assertEquals(0, rejected.ownedCells());
        assertEquals(64 * 64, rejected.totalCells());
        assertEquals(0, rejected.coverageMillionths());
        assertFalse(Files.exists(exportsRoot.resolve("legacy-baseline")));
        assertFalse(Files.exists(exportsRoot.resolve("legacy-baseline.zip")));
        // The gate runs before the diagnostic previews and the offline tiles, so a doomed run never
        // pays for them.
        assertFalse(Files.exists(workRoot.resolve("tiles")));
        assertFalse(Files.exists(workRoot.resolve("previews")));
    }

    @Test
    void theHonoredFoundationRequestPassesTheOwnerCoverageGate(@TempDir Path root) throws Exception {
        // The positive side of the same gate: the explicit foundation input covers every cell, so the
        // metric the gate reads is exactly the required 100%.
        Release2ExportResultV2 result = service().exportNow(request(root.resolve("run"), "owner-gate"));

        IntentContributionCoverageV2 coverage = result.intentContributionCoverage().orElseThrow();
        assertEquals(SurfaceFoundationOwnerGateV2.REQUIRED_COVERAGE_MILLIONTHS,
                coverage.surfaceFoundationOwnerCoverageMillionths());
        assertEquals(coverage.totalCells(), coverage.surfaceFoundationOwnerCells());
        assertTrue(result.eligibility().eligible());
    }

    private static Release2ExportApplicationServiceV2 service() {
        return new Release2ExportApplicationServiceV2(executors);
    }

    private static Release2ExportRequestV2 request(Path run, String releaseId) {
        return new Release2ExportRequestV2(
                REQUEST, INTENT, run.resolve("work"), run.resolve("exports"), releaseId, BASELINE, true,
                new ExportBudgetV2(ExportBudgetV2.MAXIMUM_RELEASE_TILES, 256L * 1024L * 1024L, 1L),
                Optional.empty());
    }

    private static PlacementPlanV2.PlacementActorV2 actor() {
        return PlacementPlanV2.PlacementActorV2.console();
    }

    private static PlacementPlanV2.PlacementTargetV2 target(int width, int length, int height) {
        return new PlacementPlanV2.PlacementTargetV2(
                UUID.randomUUID(), "world", PlacementPlanV2.AnchorKind.MINIMUM_CORNER,
                0, 64, 0, 0, 64, 0, width - 1, 64 + height, length - 1);
    }
}
