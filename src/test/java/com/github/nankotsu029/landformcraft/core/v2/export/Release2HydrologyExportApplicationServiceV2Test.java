package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseHydrologyVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-15-06 offline evidence: coastal production intent → shared hydrology-plan export →
 * directory／ZIP strict verify → graph binding → placement eligibility. No Feature promotion.
 */
class Release2HydrologyExportApplicationServiceV2Test {
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
    void exportPublishesHydrologyWithSurfaceAndBindsGraphToPlan(@TempDir Path root) throws Exception {
        Release2ExportResultV2 result = service().exportNow(request(root.resolve("run"), "azure-coast-hydrology"));

        assertEquals("azure-coast-hydrology", result.releaseId());
        assertEquals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE, result.requiredCapabilities());
        assertTrue(result.zip().isPresent());
        assertTrue(result.eligibility().eligible());
        assertEquals(result.manifestChecksum(), result.eligibility().manifestChecksum());

        var directory = new ReleaseHydrologyVerifierV2().verify(result.releaseDirectory());
        var zip = new ReleaseHydrologyVerifierV2().verify(result.zip().orElseThrow());
        assertEquals(directory.manifest(), zip.manifest());
        assertEquals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE, directory.manifest().requiredCapabilities());
        assertTrue(directory.manifest().artifacts().stream().anyMatch(descriptor ->
                descriptor.path().equals("hydrology/plan.json")));
        assertTrue(directory.manifest().artifacts().stream().anyMatch(descriptor ->
                descriptor.path().equals("hydrology/routing/index.json")));

        LandformV2DataCodec codec = new LandformV2DataCodec();
        WorldBlueprintV2 blueprint = codec.readWorldBlueprint(
                result.releaseDirectory().resolve("blueprint/world-blueprint.json"));
        HydrologyPlanV2 plan = codec.readHydrologyPlan(
                result.releaseDirectory().resolve("hydrology/plan.json"));
        assertEquals(blueprint.hydrologyPlan(), plan);
        assertEquals(blueprint.canonicalChecksum(), result.blueprintChecksum());
        assertTrue(plan.basins().isEmpty());
        assertTrue(plan.nodes().isEmpty());

        HydrologyRoutingArtifactV2 routing = new com.github.nankotsu029.landformcraft.format.v2.hydrology
                .HydrologyRoutingArtifactCodecV2().readAndVerify(
                result.releaseDirectory().resolve("hydrology/routing/index.json"),
                result.releaseDirectory().resolve("hydrology/routing"),
                () -> false);
        assertEquals(plan.canonicalChecksum(), routing.sourceHydrologyPlanChecksum());
        assertFalse(routing.graphChecksum().equals("0".repeat(64)));

        new ReleasePlacementEligibilityVerifierV2().verifyEligible(result.releaseDirectory());
    }

    @Test
    void identicalInputsPublishIdenticalHydrologyChecksums(@TempDir Path root) throws Exception {
        Release2ExportResultV2 first = service().exportNow(request(root.resolve("first"), "determinism-hydro"));
        Release2ExportResultV2 second = service().exportNow(request(root.resolve("second"), "determinism-hydro"));
        assertEquals(first.blueprintChecksum(), second.blueprintChecksum());
        assertEquals(first.manifestChecksum(), second.manifestChecksum());
        assertArrayEquals(Files.readAllBytes(first.releaseDirectory().resolve("manifest.json")),
                Files.readAllBytes(second.releaseDirectory().resolve("manifest.json")));
    }

    @Test
    void cancelBeforePublishLeavesNoHydrologyRelease(@TempDir Path root) throws Exception {
        Path run = root.resolve("cancel");
        Release2ExportRequestV2 request = new Release2ExportRequestV2(
                REQUEST, INTENT, run.resolve("work"), run.resolve("exports"), "cancel-hydro",
                BASELINE, true, ExportBudgetV2.defaults(), Optional.of((CancellationToken) () -> true));
        assertThrows(CancellationException.class, () -> service().exportNow(request));
        assertFalse(Files.exists(run.resolve("exports").resolve("cancel-hydro")));
        if (Files.exists(run.resolve("exports"))) {
            try (var files = Files.list(run.resolve("exports"))) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".release-v2")));
            }
        }
    }

    @Test
    void surfaceOnlyPathStillPublishesWithoutHydrologyArtifacts(@TempDir Path root) throws Exception {
        Release2ExportResultV2 surface = new Release2ExportApplicationServiceV2(executors)
                .exportNow(request(root.resolve("surface"), "surface-only"));
        assertEquals(List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D), surface.requiredCapabilities());
        assertTrue(surface.eligibility().eligible());
        assertTrue(Files.notExists(surface.releaseDirectory().resolve("hydrology/plan.json")));
        assertTrue(Files.notExists(surface.releaseDirectory().resolve("hydrology")));
    }

    private static Release2HydrologyExportApplicationServiceV2 service() {
        return new Release2HydrologyExportApplicationServiceV2(executors);
    }

    private static Release2ExportRequestV2 request(Path run, String releaseId) {
        return new Release2ExportRequestV2(
                REQUEST, INTENT, run.resolve("work"), run.resolve("exports"), releaseId, BASELINE);
    }
}
