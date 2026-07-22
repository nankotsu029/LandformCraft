package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfaceVerifierV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-15-09 evidence: plain/hill foundation merge → existing {@code surface-2_5d} directory／ZIP
 * strict read-back. No Feature promotion and no new capability.
 */
class Release2FoundationSurfaceExportApplicationServiceV2Test {
    private static final Path REQUEST =
            Path.of("examples/v2/foundation/plain-hill-64.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/foundation/plain-hill-64.terrain-intent-v2.json");
    private static final SurfaceBaselineV2 BASELINE =
            new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.LAND, 54, 42);

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
    void exportPublishesFoundationSurfaceIntoExistingSurfaceCapability(@TempDir Path root) throws Exception {
        Release2ExportResultV2 result = service().exportNow(
                request(root.resolve("run"), "plain-hill-foundation-surface"));

        assertEquals(java.util.List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                result.requiredCapabilities());
        assertTrue(result.zip().isPresent());
        assertTrue(result.eligibility().eligible());
        assertEquals(result.manifestChecksum(), result.eligibility().manifestChecksum());

        var directory = new ReleaseSurfaceVerifierV2().verify(result.releaseDirectory());
        var zip = new ReleaseSurfaceVerifierV2().verify(result.zip().orElseThrow());
        assertEquals(directory.manifest(), zip.manifest());
        assertEquals(java.util.List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                directory.manifest().requiredCapabilities());
        for (String type : java.util.List.of(
                "generation-request-v2",
                "terrain-intent-v2",
                "world-blueprint-v2",
                "constraint-field-index-v2",
                "constraint-field-grid-v1",
                "coastal-validation-artifact-v2",
                "coastal-preview-index-v2",
                "coastal-preview-png-v1",
                "offline-tile-artifact-v2",
                "sponge-schematic-v3")) {
            assertTrue(directory.manifest().artifacts().stream()
                    .anyMatch(descriptor -> descriptor.artifactType().equals(type)), type);
        }
        assertFalse(result.tileIds().isEmpty());
        new ReleasePlacementEligibilityVerifierV2().verifyEligible(result.releaseDirectory());
    }

    @Test
    void checksumsAreStableAcrossWorkerLocaleTimezoneAndRepeatedPublish(@TempDir Path root) throws Exception {
        GenerationExecutors oneWorker = GenerationExecutors.createDefault(1);
        GenerationExecutors fourWorkers = GenerationExecutors.createDefault(4);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Release2ExportResultV2 first = new Release2FoundationSurfaceExportApplicationServiceV2(oneWorker)
                    .export(request(root.resolve("first"), "foundation-surface-determinism"))
                    .get(60, TimeUnit.SECONDS);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Release2ExportResultV2 second = new Release2FoundationSurfaceExportApplicationServiceV2(fourWorkers)
                    .export(request(root.resolve("second"), "foundation-surface-determinism"))
                    .get(60, TimeUnit.SECONDS);

            assertEquals(first.blueprintChecksum(), second.blueprintChecksum());
            assertEquals(first.manifestChecksum(), second.manifestChecksum());
            assertEquals(first.tileIds(), second.tileIds());
            assertArrayEquals(Files.readAllBytes(first.releaseDirectory().resolve("manifest.json")),
                    Files.readAllBytes(second.releaseDirectory().resolve("manifest.json")));
            assertArrayEquals(Files.readAllBytes(first.zip().orElseThrow()),
                    Files.readAllBytes(second.zip().orElseThrow()));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
            oneWorker.shutdown(Duration.ofSeconds(30));
            oneWorker.close();
            fourWorkers.shutdown(Duration.ofSeconds(30));
            fourWorkers.close();
        }
    }

    @Test
    void cancellationBeforeTilesLeavesNoPublishedReleaseOrPublisherStaging(@TempDir Path root)
            throws Exception {
        Path run = root.resolve("cancel");
        Path workRoot = run.resolve("work");
        Path exportsRoot = run.resolve("exports");
        CancellationToken token = () -> Files.isDirectory(workRoot.resolve("tiles"));
        Release2ExportRequestV2 cancelled = new Release2ExportRequestV2(
                REQUEST,
                INTENT,
                workRoot,
                exportsRoot,
                "cancel-foundation-surface",
                BASELINE,
                true,
                ExportBudgetV2.defaults(),
                Optional.of(token));

        assertThrows(CancellationException.class, () -> service().exportNow(cancelled));
        assertFalse(Files.exists(exportsRoot.resolve("cancel-foundation-surface")));
        assertFalse(Files.exists(exportsRoot.resolve("cancel-foundation-surface.zip")));
        if (Files.exists(exportsRoot)) {
            try (var entries = Files.list(exportsRoot)) {
                assertTrue(entries.noneMatch(path -> path.getFileName().toString()
                        .startsWith(".release-v2-surface-")));
            }
        }
    }

    private static Release2FoundationSurfaceExportApplicationServiceV2 service() {
        return new Release2FoundationSurfaceExportApplicationServiceV2(executors);
    }

    private static Release2ExportRequestV2 request(Path run, String releaseId) {
        return new Release2ExportRequestV2(
                REQUEST,
                INTENT,
                run.resolve("work"),
                run.resolve("exports"),
                releaseId,
                BASELINE,
                true,
                new ExportBudgetV2(
                        ExportBudgetV2.MAXIMUM_RELEASE_TILES,
                        256L * 1024L * 1024L,
                        1L),
                Optional.empty());
    }
}
