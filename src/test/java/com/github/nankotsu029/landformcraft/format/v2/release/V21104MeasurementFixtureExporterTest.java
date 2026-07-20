package com.github.nankotsu029.landformcraft.format.v2.release;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline exporter for the V2-11-04 500×500 measurement Release. Skips unless
 * {@code LANDFORMCRAFT_V21104_EXPORT_DIR} or {@code landformcraft.v21104.exportDir} is set.
 */
class V21104MeasurementFixtureExporterTest {
    @Test
    void exportFiveHundredSolidSurfaceRelease() throws Exception {
        String configured = firstNonBlank(
                System.getProperty("landformcraft.v21104.exportDir"),
                System.getenv("LANDFORMCRAFT_V21104_EXPORT_DIR"));
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "V2-11-04 export directory is not configured");

        Path exportRoot = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(exportRoot);
        Path work = exportRoot.resolve("work");
        deleteRecursive(work);
        Files.createDirectories(work);

        MeasurementSurfaceFixtureV2.Fixture fixture = MeasurementSurfaceFixtureV2.build500(work.resolve("source"));
        assertEquals(16, fixture.tilePlan().tileCount());
        assertEquals(500, fixture.blueprint().space().bounds().width());
        assertEquals(500, fixture.blueprint().space().bounds().length());

        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                work.resolve("releases"), "v2-11-04-measure-500", fixture.source(), true, () -> false);
        ReleaseCoreVerificationV2 verified = new ReleaseSurfaceVerifierV2().verify(release.releaseDirectory());
        assertEquals(List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                verified.manifest().requiredCapabilities());

        Path staged = exportRoot.resolve("release");
        deleteRecursive(staged);
        copyRecursive(release.releaseDirectory(), staged);
        Path zip = exportRoot.resolve("release.zip");
        Files.copy(release.zip().orElseThrow(), zip, StandardCopyOption.REPLACE_EXISTING);

        Files.writeString(exportRoot.resolve("release-id.txt"),
                release.releaseId() + System.lineSeparator());
        Files.writeString(exportRoot.resolve("release-dir-name.txt"),
                release.releaseDirectory().getFileName().toString() + System.lineSeparator());
        Files.writeString(exportRoot.resolve("manifest-sha256.txt"),
                verified.manifest().canonicalChecksum() + System.lineSeparator());
        Files.writeString(exportRoot.resolve("tile-count.txt"),
                fixture.tilePlan().tileCount() + System.lineSeparator());
        Files.writeString(exportRoot.resolve("dimensions.txt"),
                "500x500 y=" + fixture.blueprint().space().bounds().minY()
                        + ".." + fixture.blueprint().space().bounds().maxY()
                        + " solid-only" + System.lineSeparator());
        assertTrue(Files.isRegularFile(staged.resolve("manifest.json")));
        assertTrue(Files.isRegularFile(zip));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private static void copyRecursive(Path source, Path target) throws Exception {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
