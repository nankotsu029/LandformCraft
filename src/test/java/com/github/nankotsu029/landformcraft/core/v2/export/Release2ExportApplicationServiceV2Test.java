package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.design.DesignDispatchRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.design.TerrainDesignApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.PlacementPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.VerifiedReleaseCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactsV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfaceVerifierV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
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
    private static final Path REQUEST = Path.of("examples/v2/diagnostic/azure-coast.request-v2.json");
    private static final Path INTENT = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");
    private static final SurfaceBaselineV2 BASELINE =
            new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 42);

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
    void designOutputFeedsTheExportPathDirectly(@TempDir Path root) throws Exception {
        Path workspace = Files.createDirectories(root.resolve("workspace"));
        Files.copy(REQUEST, workspace.resolve("request-v2.json"));
        Files.copy(INTENT, workspace.resolve("terrain-intent-v2.json"));

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
