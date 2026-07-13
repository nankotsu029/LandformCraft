package com.github.nankotsu029.landformcraft.validation;

import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignRequest;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.GenerationOptions;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.ImageConsistencyStatus;
import com.github.nankotsu029.landformcraft.model.ImageTransformation;
import com.github.nankotsu029.landformcraft.model.OutputOptions;
import com.github.nankotsu029.landformcraft.model.ReferenceImage;
import com.github.nankotsu029.landformcraft.model.ReferenceImageRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceImageProcessorTest {
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private final ReferenceImageProcessor processor = new ReferenceImageProcessor(
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void normalizesTopDownImageAndRecordsEvidence(@TempDir Path root) throws Exception {
        Path requestPath = createRequestFile(root);
        writeMap(root.resolve("images/map.png"), false, 100, 80);
        GenerationRequest request = request(
                "西側を陸地、東側を海にしてください。",
                List.of(new ReferenceImage("images/map.png", ReferenceImageRole.TOP_DOWN_SKETCH))
        );

        PreparedImageInputs result = processor.process(
                request, processor.load(requestPath, request), () -> false
        );

        assertEquals(1, result.images().size());
        assertEquals("image/png", result.images().getFirst().mediaType());
        assertEquals(1, result.evidence().images().size());
        var evidence = result.evidence().images().getFirst();
        assertEquals(1, evidence.coordinateMappings().size());
        assertEquals(500, evidence.coordinateMappings().getFirst().targetWidth());
        assertTrue(evidence.edgeWaterRatios().get(com.github.nankotsu029.landformcraft.model.CardinalDirection.EAST)
                > 0.95);
        assertTrue(evidence.edgeWaterRatios().get(com.github.nankotsu029.landformcraft.model.CardinalDirection.WEST)
                < 0.05);
        assertTrue(evidence.transformations().contains(ImageTransformation.PNG_REENCODED));
        assertTrue(evidence.transformations().contains(ImageTransformation.TOP_DOWN_COORDINATES_NORMALIZED));
        assertEquals(2, result.evidence().consistencyChecks().size());
        assertTrue(result.evidence().consistencyChecks().stream()
                .allMatch(check -> check.status() == ImageConsistencyStatus.CONSISTENT));
        assertEquals(NOW, result.evidence().createdAt());
        assertEquals(request, new TerrainDesignRequest(request, result.images()).generationRequest());
    }

    @Test
    void noImageRequestUsesTheSamePreparationContract(@TempDir Path root) throws Exception {
        Path requestPath = createRequestFile(root);
        GenerationRequest request = request("山と川を作る", List.of());

        PreparedImageInputs result = processor.process(
                request, processor.load(requestPath, request), () -> false
        );

        assertTrue(result.images().isEmpty());
        assertTrue(result.evidence().images().isEmpty());
        assertTrue(new TerrainDesignRequest(request, result.images()).images().isEmpty());
    }

    @Test
    void rejectsOversizedCorruptAndExcessivePixelImages(@TempDir Path root) throws Exception {
        Path requestPath = createRequestFile(root);
        Path images = Files.createDirectories(root.resolve("images"));

        byte[] oversized = new byte[Math.toIntExact(ReferenceImageProcessor.MAX_SOURCE_BYTES + 1)];
        oversized[0] = (byte) 0x89;
        Files.write(images.resolve("large.png"), oversized);
        assertCode(
                ImageInputFailureCode.FILE_TOO_LARGE,
                () -> processor.load(requestPath, requestWith("images/large.png"))
        );

        Files.write(images.resolve("broken.png"), new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
        });
        GenerationRequest broken = requestWith("images/broken.png");
        LoadedImageInputs loaded = processor.load(requestPath, broken);
        assertCode(
                ImageInputFailureCode.CORRUPT_IMAGE,
                () -> processor.process(broken, loaded, () -> false)
        );

        writeMap(images.resolve("pixels.png"), false, 2100, 2000);
        GenerationRequest pixels = requestWith("images/pixels.png");
        LoadedImageInputs pixelInput = processor.load(requestPath, pixels);
        assertCode(
                ImageInputFailureCode.PIXELS_EXCEEDED,
                () -> processor.process(pixels, pixelInput, () -> false)
        );
    }

    @Test
    void rejectsSymlinkAndPromptImageConflict(@TempDir Path root) throws Exception {
        Path requestPath = createRequestFile(root);
        Path images = Files.createDirectories(root.resolve("images"));
        Path actual = root.resolve("outside.png");
        writeMap(actual, false, 32, 32);
        Files.createSymbolicLink(images.resolve("linked.png"), actual);
        assertCode(
                ImageInputFailureCode.UNSAFE_PATH,
                () -> processor.load(requestPath, requestWith("images/linked.png"))
        );

        writeMap(images.resolve("opposite.png"), true, 100, 80);
        GenerationRequest conflict = request(
                "西側を陸地、東側を海にしてください。",
                List.of(new ReferenceImage("images/opposite.png", ReferenceImageRole.TOP_DOWN_SKETCH))
        );
        LoadedImageInputs loaded = processor.load(requestPath, conflict);
        assertCode(
                ImageInputFailureCode.PROMPT_IMAGE_CONFLICT,
                () -> processor.process(conflict, loaded, () -> false)
        );
    }

    @Test
    void moodReferenceNeverAcquiresMapCoordinates(@TempDir Path root) throws Exception {
        Path requestPath = createRequestFile(root);
        writeMap(root.resolve("images/mood.jpg"), false, 48, 32);
        GenerationRequest request = request(
                "東側を海にする",
                List.of(new ReferenceImage("images/mood.jpg", ReferenceImageRole.MOOD_REFERENCE))
        );

        PreparedImageInputs result = processor.process(
                request, processor.load(requestPath, request), () -> false
        );

        assertTrue(result.evidence().images().getFirst().coordinateMappings().isEmpty());
        assertTrue(result.evidence().images().getFirst().edgeWaterRatios().isEmpty());
        assertTrue(result.evidence().consistencyChecks().isEmpty());
        assertFalse(result.images().getFirst().content().length == 0);
    }

    @Test
    void appliesExifOrientationAndDoesNotForwardMetadata(@TempDir Path root) throws Exception {
        Path requestPath = createRequestFile(root);
        Path jpeg = root.resolve("images/oriented.jpg");
        writeMap(jpeg, false, 48, 32);
        Files.write(jpeg, withExifOrientation(Files.readAllBytes(jpeg), 6));
        GenerationRequest request = request(
                "岩の雰囲気を使う",
                List.of(new ReferenceImage("images/oriented.jpg", ReferenceImageRole.MOOD_REFERENCE))
        );

        PreparedImageInputs result = processor.process(
                request, processor.load(requestPath, request), () -> false
        );

        var evidence = result.evidence().images().getFirst();
        assertTrue(evidence.metadataDetected());
        assertEquals(6, evidence.exifOrientation());
        assertEquals(32, evidence.normalizedWidth());
        assertEquals(48, evidence.normalizedHeight());
        assertTrue(evidence.transformations().contains(ImageTransformation.ORIENTATION_NORMALIZED));
        assertFalse(new String(result.images().getFirst().content(), java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("Exif"));
    }

    @Test
    void normalizationIsDeterministicAndUsesCanonicalArgbPixels(@TempDir Path root) throws Exception {
        Path requestPath = createRequestFile(root);
        writeMap(root.resolve("images/map.png"), false, 64, 48);
        GenerationRequest request = requestWith("images/map.png");
        LoadedImageInputs loaded = processor.load(requestPath, request);

        PreparedImageInputs first = processor.process(request, loaded, () -> false);
        PreparedImageInputs second = processor.process(request, loaded, () -> false);

        assertEquals(first.images().getFirst().checksum(), second.images().getFirst().checksum());
        assertTrue(java.util.Arrays.equals(
                first.images().getFirst().content(), second.images().getFirst().content()
        ));
        assertTrue(first.evidence().images().getFirst().transformations()
                .contains(ImageTransformation.COLOR_SPACE_NORMALIZED));
    }

    @Test
    void rejectsExtensionSpoofExtremeAspectRatioAndCancellation(@TempDir Path root) throws Exception {
        Path requestPath = createRequestFile(root);
        Path images = Files.createDirectories(root.resolve("images"));
        writeMap(images.resolve("real.png"), false, 32, 32);
        Files.copy(images.resolve("real.png"), images.resolve("spoof.jpg"));
        assertCode(
                ImageInputFailureCode.UNSUPPORTED_FORMAT,
                () -> processor.load(requestPath, requestWith("images/spoof.jpg"))
        );

        writeMap(images.resolve("wide.png"), false, 100, 2);
        GenerationRequest wide = requestWith("images/wide.png");
        assertCode(
                ImageInputFailureCode.DIMENSIONS_EXCEEDED,
                () -> processor.process(wide, processor.load(requestPath, wide), () -> false)
        );

        GenerationRequest cancelled = requestWith("images/real.png");
        LoadedImageInputs loaded = processor.load(requestPath, cancelled);
        assertThrows(CancellationException.class, () -> processor.process(cancelled, loaded, () -> true));
    }

    @Test
    void rejectsHardLinkAliasesButAllowsDifferentFilesWithIdenticalContent(@TempDir Path root) throws Exception {
        Path requestPath = createRequestFile(root);
        Path images = Files.createDirectories(root.resolve("images"));
        writeMap(images.resolve("one.png"), false, 32, 32);
        Files.createLink(images.resolve("alias.png"), images.resolve("one.png"));
        GenerationRequest aliases = request(
                "海岸を作る",
                List.of(
                        new ReferenceImage("images/one.png", ReferenceImageRole.MOOD_REFERENCE),
                        new ReferenceImage("images/alias.png", ReferenceImageRole.MATERIAL_REFERENCE)
                )
        );
        assertCode(ImageInputFailureCode.UNSAFE_PATH, () -> processor.load(requestPath, aliases));

        Files.copy(images.resolve("one.png"), images.resolve("copy.png"));
        GenerationRequest copies = request(
                "海岸を作る",
                List.of(
                        new ReferenceImage("images/one.png", ReferenceImageRole.MOOD_REFERENCE),
                        new ReferenceImage("images/copy.png", ReferenceImageRole.MATERIAL_REFERENCE)
                )
        );
        PreparedImageInputs result = processor.process(copies, processor.load(requestPath, copies), () -> false);
        assertEquals(2, result.images().size());
        assertEquals(result.images().get(0).checksum(), result.images().get(1).checksum());
    }

    @Test
    void pngNormalizationPreservesAlpha(@TempDir Path root) throws Exception {
        Path requestPath = createRequestFile(root);
        Path path = root.resolve("images/alpha.png");
        Files.createDirectories(path.getParent());
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x00112233);
        source.setRGB(1, 0, 0x80112233);
        source.setRGB(0, 1, 0xff112233);
        source.setRGB(1, 1, 0x40112233);
        assertTrue(ImageIO.write(source, "png", path.toFile()));
        GenerationRequest request = requestWith("images/alpha.png");

        PreparedImageInputs result = processor.process(
                request, processor.load(requestPath, request), () -> false
        );
        BufferedImage normalized = ImageIO.read(new java.io.ByteArrayInputStream(
                result.images().getFirst().content()
        ));

        assertEquals(0, normalized.getRGB(0, 0) >>> 24);
        assertEquals(128, normalized.getRGB(1, 0) >>> 24);
        assertEquals(255, normalized.getRGB(0, 1) >>> 24);
        assertEquals(64, normalized.getRGB(1, 1) >>> 24);
    }

    private static Path createRequestFile(Path root) throws Exception {
        Path path = root.resolve("request.yml");
        Files.writeString(path, "fixture");
        return path;
    }

    private static GenerationRequest requestWith(String file) {
        return request(
                "海岸を作る",
                List.of(new ReferenceImage(file, ReferenceImageRole.MOOD_REFERENCE))
        );
    }

    private static GenerationRequest request(String prompt, List<ReferenceImage> images) {
        return new GenerationRequest(
                1, "image-test", new GenerationBounds(500, 400, -32, 160, 62), prompt, images,
                new GenerationOptions(1, 827413L), new OutputOptions(128, true, true)
        );
    }

    private static void writeMap(Path path, boolean opposite, int width, int height) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Color left = opposite ? new Color(50, 95, 210) : new Color(70, 140, 70);
        Color right = opposite ? new Color(70, 140, 70) : new Color(50, 95, 210);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x < width / 2 ? left : right).getRGB());
            }
        }
        String format = path.getFileName().toString().endsWith(".jpg") ? "jpg" : "png";
        assertTrue(ImageIO.write(image, format, path.toFile()));
    }

    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] segment = {
                (byte) 0xff, (byte) 0xe1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,
                0x00, (byte) orientation, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
        byte[] result = new byte[jpeg.length + segment.length];
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(segment, 0, result, 2, segment.length);
        System.arraycopy(jpeg, 2, result, 2 + segment.length, jpeg.length - 2);
        return result;
    }

    private static void assertCode(ImageInputFailureCode expected, org.junit.jupiter.api.function.Executable action) {
        ImageInputException thrown = assertThrows(ImageInputException.class, action);
        assertEquals(expected, thrown.code());
    }
}
