package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageLandWaterExtractorV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageMaskExtractionLimitsV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskDraftPreviewIndexV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractedMaskDraftPreviewRendererV2Test {
    private static final String SOURCE = "ef".repeat(32);

    @TempDir
    Path directory;

    @Test
    void atomicallyPublishesFixedPaletteLayersOneAtATime() throws Exception {
        ExtractedMaskDraftV2 draft = sampleDraft();
        Path target = directory.resolve("draft-preview");
        ExtractedMaskDraftPreviewIndexV2 index = new ExtractedMaskDraftPreviewRendererV2().render(
                target, draft, () -> false);

        assertEquals(3, index.layers().size());
        assertEquals(ExtractedMaskDraftPreviewIndexV2.Layer.PALETTE_ID,
                index.layers().getFirst().paletteId());
        assertEquals(draft.semanticChecksum(), index.sourceDraftSemanticChecksum());
        assertEquals(index, new ExtractedMaskDraftPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
        for (ExtractedMaskDraftPreviewIndexV2.Layer layer : index.layers()) {
            var image = ImageIO.read(target.resolve(layer.path()).toFile());
            assertEquals(draft.width(), image.getWidth());
            assertEquals(draft.length(), image.getHeight());
            image.flush();
        }
        ExtractedMaskDraftPreviewIndexV2 repeat = new ExtractedMaskDraftPreviewRendererV2().render(
                directory.resolve("draft-preview-repeat"), draft, () -> false);
        assertEquals(
                index.layers().stream().collect(Collectors.toMap(
                        ExtractedMaskDraftPreviewIndexV2.Layer::layerId,
                        ExtractedMaskDraftPreviewIndexV2.Layer::sha256)),
                repeat.layers().stream().collect(Collectors.toMap(
                        ExtractedMaskDraftPreviewIndexV2.Layer::layerId,
                        ExtractedMaskDraftPreviewIndexV2.Layer::sha256)));
    }

    @Test
    void rejectsExtraEntriesChecksumTamperingAndSymlinks() throws Exception {
        ExtractedMaskDraftV2 draft = sampleDraft();
        Path target = directory.resolve("draft-preview");
        new ExtractedMaskDraftPreviewRendererV2().render(target, draft, () -> false);

        Files.writeString(target.resolve("unexpected.png"), "not a preview");
        assertThrows(Exception.class, () -> new ExtractedMaskDraftPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
        Files.delete(target.resolve("unexpected.png"));

        String index = Files.readString(target.resolve("index.json"));
        Files.writeString(target.resolve("index.json"),
                index.replace(draft.semanticChecksum(), "aa".repeat(32)));
        assertThrows(Exception.class, () -> new ExtractedMaskDraftPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
        Files.writeString(target.resolve("index.json"), index);

        Path classPng = target.resolve("class.png");
        Files.delete(classPng);
        Files.createSymbolicLink(classPng, Path.of("confidence.png"));
        assertThrows(Exception.class, () -> new ExtractedMaskDraftPreviewIndexCodecV2().readAndVerify(
                target.resolve("index.json"), target, () -> false));
    }

    @Test
    void cancelCleansUpBeforeAtomicCommit() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Path target = directory.resolve("cancelled");
        assertThrows(CancellationException.class, () -> new ExtractedMaskDraftPreviewRendererV2().render(
                target, sampleDraft(), () -> checks.incrementAndGet() > 2));
        assertFalse(Files.exists(target));
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString()
                    .startsWith(".tmp-extracted-mask-draft-preview-")));
        }
    }

    private static ExtractedMaskDraftV2 sampleDraft() {
        int[] pixels = {
                argb(255, 10, 40, 220), argb(255, 70, 140, 70), argb(255, 10, 40, 220),
                argb(64, 10, 40, 220), argb(255, 120, 90, 40), argb(255, 70, 140, 70),
        };
        return ImageLandWaterExtractorV2.extract(
                3, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
