package com.github.nankotsu029.landformcraft.preview.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintMapPreviewRendererV2Test {
    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesTheFixedDiagnosticLayerSetAsOneBundle() throws Exception {
        ConstraintDiagnosticFieldsV2 fields = fixture(7, 5);
        Path target = temporaryDirectory.resolve("manual-island-preview");

        var paths = new ConstraintMapPreviewRendererV2().render(target, fields, () -> false);

        assertEquals(ConstraintMapPreviewRendererV2.FILE_NAMES,
                paths.stream().map(path -> path.getFileName().toString()).toList());
        for (Path path : paths) {
            assertTrue(Files.isRegularFile(path));
            var image = ImageIO.read(path.toFile());
            assertEquals(7, image.getWidth());
            assertEquals(5, image.getHeight());
            image.flush();
        }
    }

    @Test
    void cancellationNeverPublishesAPartialCanonicalBundle() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Path target = temporaryDirectory.resolve("cancelled-preview");

        assertThrows(CancellationException.class, () -> new ConstraintMapPreviewRendererV2().render(
                target, fixture(64, 64), () -> checks.incrementAndGet() > 3));

        assertFalse(Files.exists(target));
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".tmp-constraint-preview-")));
        }
    }

    private static ConstraintDiagnosticFieldsV2 fixture(int width, int length) {
        return new ConstraintDiagnosticFieldsV2(
                width,
                length,
                0,
                255_000_000,
                (x, z) -> x < width / 2 ? 0 : 1,
                (x, z) -> x < width / 2 ? 0 : 1,
                (x, z) -> 0,
                (x, z) -> (40 + x + z) * 1_000_000,
                (x, z) -> (40 + x + z / 2) * 1_000_000,
                (x, z) -> z * 500_000,
                (x, z) -> x < width / 2 ? 10 : 20,
                (x, z) -> 0
        );
    }
}
