package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.model.v2.CoastalPreviewIndexV2;
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

class CoastalDiagnosticPreviewRendererV2Test {
    @TempDir
    Path directory;

    @Test
    void atomicallyPublishesAndStrictlyReadsBackTheCompleteFixedLayerSet() throws Exception {
        Path target = directory.resolve("coastal-preview");
        CoastalPreviewIndexV2 index = new CoastalDiagnosticPreviewRendererV2().render(
                target, "a".repeat(64), fixture(7, 5), () -> false);

        assertEquals(11, index.layers().size());
        assertEquals(index, new CoastalPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
        for (CoastalPreviewIndexV2.Layer layer : index.layers()) {
            var image = ImageIO.read(target.resolve(layer.path()).toFile());
            assertEquals(7, image.getWidth());
            assertEquals(5, image.getHeight());
            image.flush();
        }
        CoastalPreviewIndexV2 repeat = new CoastalDiagnosticPreviewRendererV2().render(
                directory.resolve("coastal-preview-repeat"), "a".repeat(64), fixture(7, 5), () -> false);
        assertEquals(index.layers().stream().collect(java.util.stream.Collectors.toMap(
                        CoastalPreviewIndexV2.Layer::layerId, CoastalPreviewIndexV2.Layer::sha256)),
                repeat.layers().stream().collect(java.util.stream.Collectors.toMap(
                        CoastalPreviewIndexV2.Layer::layerId, CoastalPreviewIndexV2.Layer::sha256)));
    }

    @Test
    void rejectsExtraEntriesAndChecksumTamperingDuringStrictReadBack() throws Exception {
        Path target = directory.resolve("coastal-preview");
        new CoastalDiagnosticPreviewRendererV2().render(target, "a".repeat(64), fixture(5, 4), () -> false);
        Files.writeString(target.resolve("unexpected.png"), "not a preview");
        assertThrows(Exception.class, () -> new CoastalPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
        Files.delete(target.resolve("unexpected.png"));
        String index = Files.readString(target.resolve("index.json"));
        Files.writeString(target.resolve("index.json"), index.replaceFirst("a{64}", "b".repeat(64)));
        assertThrows(Exception.class, () -> new CoastalPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
    }

    @Test
    void cancelCleansUpBeforeTheAtomicCommitPoint() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Path target = directory.resolve("cancelled");
        assertThrows(CancellationException.class, () -> new CoastalDiagnosticPreviewRendererV2().render(
                target, "a".repeat(64), fixture(64, 64), () -> checks.incrementAndGet() > 2));
        assertFalse(Files.exists(target));
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".tmp-coastal-preview-")));
        }
    }

    @Test
    void rejectsSymlinkAndHardlinkAliasDuringPreviewReadBack() throws Exception {
        Path target = directory.resolve("coastal-preview");
        new CoastalDiagnosticPreviewRendererV2().render(target, "a".repeat(64), fixture(5, 4), () -> false);
        Path actual = target.resolve("actual-land-water.png");
        Files.delete(actual);
        Files.createSymbolicLink(actual, Path.of("desired-land-water.png"));
        assertThrows(Exception.class, () -> new CoastalPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));

        Files.delete(actual);
        Files.createLink(actual, target.resolve("desired-land-water.png"));
        assertThrows(Exception.class, () -> new CoastalPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
    }

    @Test
    void oneThousandSquareRenderHasOnlyOneImageInFlightAndCancelsWithoutPublication() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Path target = directory.resolve("large-cancelled");
        assertThrows(CancellationException.class, () -> new CoastalDiagnosticPreviewRendererV2().render(
                target, "a".repeat(64), fixture(1_000, 1_000), () -> checks.incrementAndGet() > 2));
        assertFalse(Files.exists(target));
    }

    @Test
    void rejectsNonPortableLayerPathsAndPngByteBudgetBeforePublication() {
        assertThrows(IllegalArgumentException.class, () -> new CoastalPreviewIndexV2.Layer(
                CoastalPreviewIndexV2.LayerId.BEACH_OVERLAY, 1, "../beach-overlay.png",
                "coastal.beach.overlay", "a".repeat(64), 1, 5, 4,
                CoastalDiagnosticPreviewRendererV2.PALETTE_ID));
        assertThrows(IllegalArgumentException.class, () -> new CoastalPreviewIndexV2.Layer(
                CoastalPreviewIndexV2.LayerId.BEACH_OVERLAY, 1, "beach-overlay.png",
                "coastal.beach.overlay", "a".repeat(64), 8L * 1024L * 1024L + 1L, 5, 4,
                CoastalDiagnosticPreviewRendererV2.PALETTE_ID));
    }

    private static CoastalDiagnosticFieldsV2 fixture(int width, int length) {
        return new CoastalDiagnosticFieldsV2(
                width, length, -64_000_000, 255_000_000,
                (x, z) -> x % 4, (x, z) -> z % 3, (x, z) -> (x + z) % 4, (x, z) -> x == z ? 1 : 0,
                (x, z) -> x < width / 2 ? 0 : 1, (x, z) -> x < width / 2 ? 0 : 1,
                (x, z) -> 0, (x, z) -> (x + z) * 1_000_000,
                (x, z) -> (x + z) * 1_000_000, (x, z) -> 0, (x, z) -> 0);
    }
}
