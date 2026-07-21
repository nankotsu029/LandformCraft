package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskDraftArtifactV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractedMaskDraftArtifactPublisherV2Test {
    private static final String SOURCE = "ab".repeat(32);
    private final ExtractedMaskDraftArtifactPublisherV2 publisher = new ExtractedMaskDraftArtifactPublisherV2();
    private final ExtractedMaskDraftArtifactCodecV2 codec = new ExtractedMaskDraftArtifactCodecV2();

    @Test
    void publishesStrictRoundTripAndRestoresIdenticalDraft(@TempDir Path root) throws Exception {
        ExtractedMaskDraftV2 draft = sampleDraft();
        Path target = root.resolve("draft-bundle");
        ExtractedMaskDraftArtifactV2 artifact = publisher.publish(
                target, draft, "images/sketch.png", () -> false);

        assertEquals(draft.semanticChecksum(), artifact.semanticChecksum());
        assertEquals(draft.sourceChecksum(), artifact.sourceChecksum());
        assertEquals(ExtractedMaskDraftArtifactV2.CLASSES_PATH, artifact.classesPath());
        assertTrue(Files.isRegularFile(target.resolve("classes.u8")));
        assertTrue(Files.isRegularFile(target.resolve("confidence.u8")));

        ExtractedMaskDraftArtifactV2 verified = codec.readAndVerify(
                target.resolve(ExtractedMaskDraftArtifactCodecV2.INDEX_FILE_NAME), target, () -> false);
        assertEquals(artifact, verified);
        ExtractedMaskDraftV2 restored = codec.loadDraft(
                target.resolve(ExtractedMaskDraftArtifactCodecV2.INDEX_FILE_NAME), target, () -> false);
        assertEquals(draft.semanticChecksum(), restored.semanticChecksum());
        assertEquals(draft.classAt(0, 0), restored.classAt(0, 0));
        assertEquals(draft.confidenceAt(1, 0), restored.confidenceAt(1, 0));
    }

    @Test
    void rejectsExtraMissingAndChecksumTampering(@TempDir Path root) throws Exception {
        Path target = root.resolve("draft-bundle");
        publisher.publish(target, sampleDraft(), null, () -> false);

        Files.writeString(target.resolve("extra.bin"), "nope");
        assertThrows(Exception.class, () -> codec.readAndVerify(
                target.resolve(ExtractedMaskDraftArtifactCodecV2.INDEX_FILE_NAME), target, () -> false));
        Files.delete(target.resolve("extra.bin"));

        byte[] classes = Files.readAllBytes(target.resolve("classes.u8"));
        classes[0] ^= 1;
        Files.write(target.resolve("classes.u8"), classes);
        assertThrows(Exception.class, () -> codec.readAndVerify(
                target.resolve(ExtractedMaskDraftArtifactCodecV2.INDEX_FILE_NAME), target, () -> false));
        classes[0] ^= 1;
        Files.write(target.resolve("classes.u8"), classes);

        String index = Files.readString(target.resolve(ExtractedMaskDraftArtifactCodecV2.INDEX_FILE_NAME));
        Files.writeString(
                target.resolve(ExtractedMaskDraftArtifactCodecV2.INDEX_FILE_NAME),
                index.replace(SOURCE, "cd".repeat(32)));
        assertThrows(Exception.class, () -> codec.readAndVerify(
                target.resolve(ExtractedMaskDraftArtifactCodecV2.INDEX_FILE_NAME), target, () -> false));
    }

    @Test
    void cancelCleansUpBeforeAtomicCommit(@TempDir Path root) throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Path target = root.resolve("cancelled");
        assertThrows(CancellationException.class, () -> publisher.publish(
                target, sampleDraft(), null, () -> checks.incrementAndGet() > 1));
        assertFalse(Files.exists(target));
        try (var files = Files.list(root)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString()
                    .startsWith(".tmp-extracted-mask-draft-")));
        }
    }

    @Test
    void publicationIsDeterministicAcrossLocaleTimezoneAndThreads(@TempDir Path root) throws Exception {
        ExtractedMaskDraftV2 draft = sampleDraft();
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            ExtractedMaskDraftArtifactV2 expected = publisher.publish(
                    root.resolve("draft-a"), draft, "maps/a.png", () -> false);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            ExtractedMaskDraftArtifactV2 second = publisher.publish(
                    root.resolve("draft-b"), draft, "maps/a.png", () -> false);
            assertEquals(expected.canonicalChecksum(), second.canonicalChecksum());
            assertEquals(expected.classesSha256(), second.classesSha256());
            try (var pool = Executors.newFixedThreadPool(4)) {
                assertEquals(expected.canonicalChecksum(), pool.submit(() -> publisher.publish(
                        root.resolve("draft-c"), draft, "maps/a.png", () -> false)).get().canonicalChecksum());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    private static ExtractedMaskDraftV2 sampleDraft() {
        int[] pixels = {
                argb(255, 10, 40, 220), argb(255, 70, 140, 70),
                argb(64, 10, 40, 220), argb(255, 120, 90, 40),
        };
        return ImageLandWaterExtractorV2.extract(
                2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
