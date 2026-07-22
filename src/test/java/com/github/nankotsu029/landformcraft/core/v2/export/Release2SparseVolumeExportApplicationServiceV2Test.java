package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSparseVolumeVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.VolumeValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.VolumeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeAabbIndexPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
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
 * V2-15-08 evidence: coastal production intent → shared environment chain → ordered CSG →
 * bounded volume tile → sparse-volume directory／ZIP strict read-back. No Feature promotion.
 */
class Release2SparseVolumeExportApplicationServiceV2Test {
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/harbor-cove-64.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/harbor-cove-64.terrain-intent-v2.json");
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
    void exportPublishesOrderedCsgAndStrictlyReadBackVolumeTiles(@TempDir Path root) throws Exception {
        Release2ExportResultV2 result = service().exportNow(
                request(root.resolve("run"), "harbor-cove-sparse-volume"));

        assertEquals(ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT,
                result.requiredCapabilities());
        assertTrue(result.zip().isPresent());
        assertTrue(result.eligibility().eligible());
        assertEquals(result.manifestChecksum(), result.eligibility().manifestChecksum());

        var directory = new ReleaseSparseVolumeVerifierV2().verify(result.releaseDirectory());
        var zip = new ReleaseSparseVolumeVerifierV2().verify(result.zip().orElseThrow());
        assertEquals(directory.manifest(), zip.manifest());
        assertEquals(ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT,
                directory.manifest().requiredCapabilities());
        for (String type : java.util.List.of(
                "volume-sdf-primitive-plan-v2",
                "volume-csg-plan-v2",
                "volume-aabb-index-plan-v2",
                "volume-validation-artifact-v2",
                "volume-offline-tile-artifact-v2",
                "volume-sponge-schematic-v3")) {
            assertTrue(directory.manifest().artifacts().stream()
                    .anyMatch(descriptor -> descriptor.artifactType().equals(type)), type);
        }

        LandformV2DataCodec data = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 sdf = data.readVolumeSdfPrimitivePlan(
                result.releaseDirectory().resolve("volume/sdf-primitive-plan.json"));
        VolumeCsgPlanV2 csg = data.readVolumeCsgPlan(
                result.releaseDirectory().resolve("volume/csg-plan.json"));
        VolumeAabbIndexPlanV2 aabb = data.readVolumeAabbIndexPlan(
                result.releaseDirectory().resolve("volume/aabb-index-plan.json"));
        csg.requirePrimitivePlan(sdf);
        aabb.requireCsgPlan(csg);
        assertEquals(1, csg.operators().size());
        assertEquals(0, csg.operators().getFirst().ordinal());
        assertEquals(VolumeCsgPlanV2.OperationKind.ADD_FLUID, csg.operators().getFirst().kind());

        VolumeValidationArtifactV2 validation = new VolumeValidationArtifactCodecV2().read(
                result.releaseDirectory().resolve("volume/validation.json"));
        assertEquals(csg.canonicalChecksum(), validation.sourcePlanChecksum());
        assertTrue(validation.report().passesHardValidation());

        OfflineTileArtifactCodecV2 tileCodec = new OfflineTileArtifactCodecV2();
        for (String tileId : result.tileIds()) {
            OfflineTileArtifactV2 surface = tileCodec.read(
                    result.releaseDirectory().resolve("tiles/" + tileId + ".json"));
            OfflineTileArtifactV2 volume = tileCodec.read(
                    result.releaseDirectory().resolve("volume/tiles/" + tileId + ".json"));
            assertEquals(surface.tilePlan(), volume.tilePlan());
            assertEquals(surface.semanticChecksum(), volume.semanticChecksum(),
                    "identity CSG must not invent unrequested volume geometry");
            assertEquals(result.blueprintChecksum(), volume.sourceBlueprintChecksum());
        }
        new ReleasePlacementEligibilityVerifierV2().verifyEligible(result.releaseDirectory());
    }

    @Test
    void checksumsAreStableAcrossWorkerLocaleTimezoneAndRepeatedPublish(@TempDir Path root) throws Exception {
        GenerationExecutors oneWorker = GenerationExecutors.createDefault(1);
        GenerationExecutors fourWorkers = GenerationExecutors.createDefault(4);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Release2ExportResultV2 first = new Release2SparseVolumeExportApplicationServiceV2(oneWorker)
                    .export(request(root.resolve("first"), "sparse-volume-determinism"))
                    .get(60, TimeUnit.SECONDS);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            Release2ExportResultV2 second = new Release2SparseVolumeExportApplicationServiceV2(fourWorkers)
                    .export(request(root.resolve("second"), "sparse-volume-determinism"))
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
    void cancellationBeforeVolumeTilesLeavesNoPublishedReleaseOrPublisherStaging(@TempDir Path root)
            throws Exception {
        Path run = root.resolve("cancel");
        Path workRoot = run.resolve("work");
        Path exportsRoot = run.resolve("exports");
        CancellationToken token = () -> Files.isDirectory(workRoot.resolve("volume-work/tiles"));
        Release2ExportRequestV2 cancelled = new Release2ExportRequestV2(
                REQUEST,
                INTENT,
                workRoot,
                exportsRoot,
                "cancel-sparse-volume",
                BASELINE,
                true,
                ExportBudgetV2.defaults(),
                Optional.of(token));

        assertThrows(CancellationException.class, () -> service().exportNow(cancelled));
        assertFalse(Files.exists(exportsRoot.resolve("cancel-sparse-volume")));
        assertFalse(Files.exists(exportsRoot.resolve("cancel-sparse-volume.zip")));
        if (Files.exists(exportsRoot)) {
            try (var entries = Files.list(exportsRoot)) {
                assertTrue(entries.noneMatch(path -> path.getFileName().toString()
                        .startsWith(".release-v2-sparse-volume-")));
            }
        }
    }

    private static Release2SparseVolumeExportApplicationServiceV2 service() {
        return new Release2SparseVolumeExportApplicationServiceV2(executors);
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
