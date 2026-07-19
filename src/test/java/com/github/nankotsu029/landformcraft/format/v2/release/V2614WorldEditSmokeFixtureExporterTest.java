package com.github.nankotsu029.landformcraft.format.v2.release;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline exporter for the V2-6-14 WorldEdit smoke fixture. Skips unless
 * {@code LANDFORMCRAFT_V2614_EXPORT_DIR} or {@code landformcraft.v2614.exportDir} is set.
 */
class V2614WorldEditSmokeFixtureExporterTest {
    @Test
    void exportSurfaceReleaseFixture() throws Exception {
        String configured = firstNonBlank(
                System.getProperty("landformcraft.v2614.exportDir"),
                System.getenv("LANDFORMCRAFT_V2614_EXPORT_DIR"));
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "V2-6-14 export directory is not configured");

        Path exportRoot = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(exportRoot);
        Path work = exportRoot.resolve("work");
        deleteRecursive(work);
        Files.createDirectories(work);

        EnvironmentReleaseFixtureV2.Fixture fixture = EnvironmentReleaseFixtureV2.build(work.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                work.resolve("releases"), "v2-6-14-we-smoke", fixture.source().hydrology().surface(),
                true, () -> false);

        Path staged = exportRoot.resolve("release");
        deleteRecursive(staged);
        copyRecursive(release.releaseDirectory(), staged);
        Path zip = exportRoot.resolve("release.zip");
        Files.copy(release.zip().orElseThrow(), zip, StandardCopyOption.REPLACE_EXISTING);

        Files.writeString(exportRoot.resolve("release-id.txt"),
                release.releaseId() + System.lineSeparator());
        Files.writeString(exportRoot.resolve("release-dir-name.txt"),
                release.releaseDirectory().getFileName().toString() + System.lineSeparator());
        assertTrue(Files.isRegularFile(staged.resolve("manifest.json")));
        assertTrue(Files.isRegularFile(zip));
        assertTrue(Files.size(staged.resolve("manifest.json")) > 0L);
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
