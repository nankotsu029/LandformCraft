package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseEnvironmentVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;
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
 * V2-15-07 offline evidence: coastal production intent → shared environment-fields export →
 * directory／ZIP strict verify with hydrology dependency → placement eligibility. No Feature promotion.
 */
class Release2EnvironmentExportApplicationServiceV2Test {
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
    void exportPublishesEnvironmentWithHydrologyAndSurfaceAndBindsPlans(@TempDir Path root) throws Exception {
        Release2ExportResultV2 result = service().exportNow(request(root.resolve("run"), "azure-coast-environment"));

        assertEquals("azure-coast-environment", result.releaseId());
        assertEquals(ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE, result.requiredCapabilities());
        assertTrue(result.zip().isPresent());
        assertTrue(result.eligibility().eligible());
        assertEquals(result.manifestChecksum(), result.eligibility().manifestChecksum());

        var directory = new ReleaseEnvironmentVerifierV2().verify(result.releaseDirectory());
        var zip = new ReleaseEnvironmentVerifierV2().verify(result.zip().orElseThrow());
        assertEquals(directory.manifest(), zip.manifest());
        assertEquals(ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE,
                directory.manifest().requiredCapabilities());
        assertTrue(directory.manifest().artifacts().stream().anyMatch(descriptor ->
                descriptor.path().equals("environment/geology-plan.json")));
        assertTrue(directory.manifest().artifacts().stream().anyMatch(descriptor ->
                descriptor.path().equals("environment/minecraft-palette-plan.json")));
        assertTrue(directory.manifest().artifacts().stream().anyMatch(descriptor ->
                descriptor.path().equals("hydrology/plan.json")));
        assertTrue(directory.manifest().artifacts().stream().anyMatch(descriptor ->
                descriptor.path().equals("source/generation-request.json")));

        LandformV2DataCodec codec = new LandformV2DataCodec();
        WorldBlueprintV2 blueprint = codec.readWorldBlueprint(
                result.releaseDirectory().resolve("blueprint/world-blueprint.json"));
        GeologyPlanV2 geology = codec.readGeologyPlan(
                result.releaseDirectory().resolve("environment/geology-plan.json"));
        MinecraftPalettePlanV2 palette = codec.readMinecraftPalettePlan(
                result.releaseDirectory().resolve("environment/minecraft-palette-plan.json"));
        assertEquals(blueprint.geologyPlan(), geology);
        assertEquals(blueprint.canonicalChecksum(), result.blueprintChecksum());
        assertFalse(palette.canonicalChecksum().equals("0".repeat(64)));

        assertTrue(Files.exists(result.releaseDirectory().resolve("hydrology/plan.json")));
        assertTrue(Files.exists(result.releaseDirectory().resolve("hydrology/routing/index.json")));
        assertTrue(Files.exists(result.releaseDirectory().resolve("environment/validation.json")));
        assertTrue(Files.exists(result.releaseDirectory().resolve("environment/previews/index.json")));

        new ReleasePlacementEligibilityVerifierV2().verifyEligible(result.releaseDirectory());
    }

    @Test
    void identicalInputsPublishIdenticalEnvironmentChecksums(@TempDir Path root) throws Exception {
        Release2ExportResultV2 first = service().exportNow(request(root.resolve("first"), "determinism-env"));
        Release2ExportResultV2 second = service().exportNow(request(root.resolve("second"), "determinism-env"));
        assertEquals(first.blueprintChecksum(), second.blueprintChecksum());
        assertEquals(first.manifestChecksum(), second.manifestChecksum());
        assertArrayEquals(Files.readAllBytes(first.releaseDirectory().resolve("manifest.json")),
                Files.readAllBytes(second.releaseDirectory().resolve("manifest.json")));
    }

    @Test
    void cancelBeforePublishLeavesNoEnvironmentRelease(@TempDir Path root) throws Exception {
        Path run = root.resolve("cancel");
        Release2ExportRequestV2 request = new Release2ExportRequestV2(
                REQUEST, INTENT, run.resolve("work"), run.resolve("exports"), "cancel-env",
                BASELINE, true, ExportBudgetV2.defaults(), Optional.of((CancellationToken) () -> true));
        assertThrows(CancellationException.class, () -> service().exportNow(request));
        assertFalse(Files.exists(run.resolve("exports").resolve("cancel-env")));
        if (Files.exists(run.resolve("exports"))) {
            try (var files = Files.list(run.resolve("exports"))) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".release-v2")));
            }
        }
    }

    @Test
    void surfaceAndHydrologyPathsStillPublishWithoutEnvironmentArtifacts(@TempDir Path root) throws Exception {
        Release2ExportResultV2 surface = new Release2ExportApplicationServiceV2(executors)
                .exportNow(request(root.resolve("surface"), "surface-only"));
        assertEquals(List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D), surface.requiredCapabilities());
        assertTrue(Files.notExists(surface.releaseDirectory().resolve("environment")));
        assertTrue(Files.notExists(surface.releaseDirectory().resolve("hydrology")));

        Release2ExportResultV2 hydrology = new Release2HydrologyExportApplicationServiceV2(executors)
                .exportNow(request(root.resolve("hydrology"), "hydrology-only"));
        assertEquals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE, hydrology.requiredCapabilities());
        assertTrue(Files.exists(hydrology.releaseDirectory().resolve("hydrology/plan.json")));
        assertTrue(Files.notExists(hydrology.releaseDirectory().resolve("environment")));
    }

    private static Release2EnvironmentExportApplicationServiceV2 service() {
        return new Release2EnvironmentExportApplicationServiceV2(executors);
    }

    private static Release2ExportRequestV2 request(Path run, String releaseId) {
        return new Release2ExportRequestV2(
                REQUEST, INTENT, run.resolve("work"), run.resolve("exports"), releaseId, BASELINE);
    }
}
