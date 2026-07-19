package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.model.v2.EnvironmentPreviewIndexV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentCellSnapshotV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentFieldSamplerV2;
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

class EnvironmentDiagnosticPreviewRendererV2Test {
    @TempDir
    Path directory;

    @Test
    void atomicallyPublishesAndStrictlyReadsBackTheCompleteFixedLayerSet() throws Exception {
        Path target = directory.resolve("environment-preview");
        EnvironmentPreviewIndexV2 index = new EnvironmentDiagnosticPreviewRendererV2().render(
                target, "a".repeat(64), fields(7, 5), () -> false);

        assertEquals(10, index.layers().size());
        assertEquals(index, new EnvironmentPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
        for (EnvironmentPreviewIndexV2.Layer layer : index.layers()) {
            var image = ImageIO.read(target.resolve(layer.path()).toFile());
            assertEquals(7, image.getWidth());
            assertEquals(5, image.getHeight());
            image.flush();
        }
        EnvironmentPreviewIndexV2 repeat = new EnvironmentDiagnosticPreviewRendererV2().render(
                directory.resolve("environment-preview-repeat"), "a".repeat(64), fields(7, 5), () -> false);
        assertEquals(index.layers().stream().collect(java.util.stream.Collectors.toMap(
                        EnvironmentPreviewIndexV2.Layer::layerId, EnvironmentPreviewIndexV2.Layer::sha256)),
                repeat.layers().stream().collect(java.util.stream.Collectors.toMap(
                        EnvironmentPreviewIndexV2.Layer::layerId, EnvironmentPreviewIndexV2.Layer::sha256)));
    }

    @Test
    void rejectsExtraEntriesAndChecksumTamperingDuringStrictReadBack() throws Exception {
        Path target = directory.resolve("environment-preview");
        new EnvironmentDiagnosticPreviewRendererV2().render(target, "a".repeat(64), fields(5, 4), () -> false);
        Files.writeString(target.resolve("unexpected.png"), "not a preview");
        assertThrows(Exception.class, () -> new EnvironmentPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
        Files.delete(target.resolve("unexpected.png"));
        String index = Files.readString(target.resolve("index.json"));
        Files.writeString(target.resolve("index.json"), index.replaceFirst("a{64}", "b".repeat(64)));
        assertThrows(Exception.class, () -> new EnvironmentPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
    }

    @Test
    void cancelCleansUpBeforeTheAtomicCommitPoint() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Path target = directory.resolve("cancelled");
        assertThrows(CancellationException.class, () -> new EnvironmentDiagnosticPreviewRendererV2().render(
                target, "a".repeat(64), fields(64, 64), () -> checks.incrementAndGet() > 2));
        assertFalse(Files.exists(target));
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".tmp-environment-preview-")));
        }
    }

    @Test
    void rejectsSymlinkAndHardlinkAliasDuringPreviewReadBack() throws Exception {
        Path target = directory.resolve("environment-preview");
        new EnvironmentDiagnosticPreviewRendererV2().render(target, "a".repeat(64), fields(5, 4), () -> false);
        Path actual = target.resolve("habitat.png");
        Files.delete(actual);
        Files.createSymbolicLink(actual, Path.of("temperature.png"));
        assertThrows(Exception.class, () -> new EnvironmentPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));

        Files.delete(actual);
        Files.createLink(actual, target.resolve("temperature.png"));
        assertThrows(Exception.class, () -> new EnvironmentPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
    }

    @Test
    void oneThousandSquareRenderCancelsWithoutPublication() {
        AtomicInteger checks = new AtomicInteger();
        Path target = directory.resolve("large-cancelled");
        assertThrows(CancellationException.class, () -> new EnvironmentDiagnosticPreviewRendererV2().render(
                target, "a".repeat(64), fields(1_000, 1_000), () -> checks.incrementAndGet() > 2));
        assertFalse(Files.exists(target));
    }

    @Test
    void rejectsNonPortableLayerPathsAndPngByteBudgetBeforePublication() {
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentPreviewIndexV2.Layer(
                EnvironmentPreviewIndexV2.LayerId.TEMPERATURE, 1, "../temperature.png",
                "environment.climate.temperature", "a".repeat(64), 1, 5, 4,
                EnvironmentDiagnosticPreviewRendererV2.PALETTE_ID));
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentPreviewIndexV2.Layer(
                EnvironmentPreviewIndexV2.LayerId.TEMPERATURE, 1, "temperature.png",
                "environment.climate.temperature", "a".repeat(64), 8L * 1024L * 1024L + 1L, 5, 4,
                EnvironmentDiagnosticPreviewRendererV2.PALETTE_ID));
    }

    private static EnvironmentDiagnosticFieldsV2 fields(int width, int length) {
        EnvironmentFieldSamplerV2 sampler = (x, z) -> new EnvironmentCellSnapshotV2(
                (x * 17 + z * 13) % 1_001,
                (x * 11 + z * 19) % 1_001,
                (x * 7 + z * 23) % 1_001,
                (x * 3 + z * 29) % 1_001,
                (x * 5 + z * 31) % 1_001,
                (x + z) % 200,
                (x + z) % 4,
                (x % 6) + 1,
                0,
                (x % 3) + 1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0);
        return EnvironmentDiagnosticFieldFactoryV2.create(width, length, sampler);
    }
}
