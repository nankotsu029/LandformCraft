package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.model.v2.VolumePreviewIndexV2;
import com.github.nankotsu029.landformcraft.validation.v2.volume.VolumeCellSnapshotV2;
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

class VolumeDiagnosticPreviewRendererV2Test {
    @TempDir
    Path directory;

    @Test
    void atomicallyPublishesAndStrictlyReadsBackTheCompleteFixedLayerSet() throws Exception {
        Path target = directory.resolve("volume-preview");
        VolumePreviewIndexV2 index = new VolumeDiagnosticPreviewRendererV2().render(
                target, "a".repeat(64), fields(7, 5), () -> false);

        assertEquals(5, index.layers().size());
        assertEquals(index, new VolumePreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
        for (VolumePreviewIndexV2.Layer layer : index.layers()) {
            var image = ImageIO.read(target.resolve(layer.path()).toFile());
            assertEquals(7, image.getWidth());
            assertEquals(5, image.getHeight());
            image.flush();
        }
        VolumePreviewIndexV2 repeat = new VolumeDiagnosticPreviewRendererV2().render(
                directory.resolve("volume-preview-repeat"), "a".repeat(64), fields(7, 5), () -> false);
        assertEquals(index.layers().stream().collect(java.util.stream.Collectors.toMap(
                        VolumePreviewIndexV2.Layer::layerId, VolumePreviewIndexV2.Layer::sha256)),
                repeat.layers().stream().collect(java.util.stream.Collectors.toMap(
                        VolumePreviewIndexV2.Layer::layerId, VolumePreviewIndexV2.Layer::sha256)));
    }

    @Test
    void rejectsExtraEntriesAndChecksumTamperingDuringStrictReadBack() throws Exception {
        Path target = directory.resolve("volume-preview");
        new VolumeDiagnosticPreviewRendererV2().render(target, "a".repeat(64), fields(5, 4), () -> false);
        Files.writeString(target.resolve("unexpected.png"), "not a preview");
        assertThrows(Exception.class, () -> new VolumePreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
        Files.delete(target.resolve("unexpected.png"));
        String index = Files.readString(target.resolve("index.json"));
        Files.writeString(target.resolve("index.json"), index.replaceFirst("a{64}", "b".repeat(64)));
        assertThrows(Exception.class, () -> new VolumePreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
    }

    @Test
    void cancelCleansUpBeforeTheAtomicCommitPoint() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Path target = directory.resolve("cancelled");
        assertThrows(CancellationException.class, () -> new VolumeDiagnosticPreviewRendererV2().render(
                target, "a".repeat(64), fields(64, 64), () -> checks.incrementAndGet() > 2));
        assertFalse(Files.exists(target));
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".tmp-volume-preview-")));
        }
    }

    @Test
    void rejectsSymlinkAndHardlinkAliasDuringPreviewReadBack() throws Exception {
        Path target = directory.resolve("volume-preview");
        new VolumeDiagnosticPreviewRendererV2().render(target, "a".repeat(64), fields(5, 4), () -> false);
        Path actual = target.resolve("aabb-footprint.png");
        Files.delete(actual);
        Files.createSymbolicLink(actual, Path.of("y-slice.png"));
        assertThrows(Exception.class, () -> new VolumePreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));

        Files.delete(actual);
        Files.createLink(actual, target.resolve("y-slice.png"));
        assertThrows(Exception.class, () -> new VolumePreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
    }

    @Test
    void rejectsNonPortableLayerPathsAndPngByteBudgetBeforePublication() {
        assertThrows(IllegalArgumentException.class, () -> new VolumePreviewIndexV2.Layer(
                VolumePreviewIndexV2.LayerId.AABB_FOOTPRINT, 1, "../aabb-footprint.png",
                "volume.aabb.footprint", "a".repeat(64), 1, 5, 4,
                VolumeDiagnosticPreviewRendererV2.PALETTE_ID));
        assertThrows(IllegalArgumentException.class, () -> new VolumePreviewIndexV2.Layer(
                VolumePreviewIndexV2.LayerId.AABB_FOOTPRINT, 1, "aabb-footprint.png",
                "volume.aabb.footprint", "a".repeat(64), 8L * 1024L * 1024L + 1L, 5, 4,
                VolumeDiagnosticPreviewRendererV2.PALETTE_ID));
    }

    private static VolumeDiagnosticFieldsV2 fields(int width, int length) {
        return VolumeDiagnosticFieldFactoryV2.create(width, length, (x, z) -> new VolumeCellSnapshotV2(
                (x + z) % 2,
                (x * 3 + z) % 8,
                (x * z) % 2,
                (x + 2 * z) % 4,
                (x % 5) + 1));
    }
}
