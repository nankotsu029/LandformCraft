package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import com.github.nankotsu029.landformcraft.format.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureImageExtractionEnvelopeV2Test {
    private static final ImageExtractionInputLimitsV2 DEFAULTS = ImageExtractionInputLimitsV2.defaults();
    private static final ImageMaskExtractionLimitsV2 EXTRACT_DEFAULTS = ImageMaskExtractionLimitsV2.defaults();
    private final SecureImageExtractionEnvelopeV2 envelope = new SecureImageExtractionEnvelopeV2();

    @Test
    void loadsPngAndJpegIntoSanitizedArgbAndExtractsLandWaterDraft(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path png = writeLandWaterMap(root.resolve("images/map.png"), 8, 4);
        Path jpeg = writeLandWaterMap(root.resolve("images/map.jpg"), 8, 4);

        SanitizedArgbImageV2 fromPng = envelope.loadOne(request, "images/map.png", DEFAULTS, () -> false);
        assertEquals("image/png", fromPng.mediaType());
        assertEquals(8, fromPng.width());
        assertEquals(4, fromPng.length());
        assertEquals(Sha256.file(png), fromPng.sourceChecksum());
        assertEquals(1, fromPng.exifOrientation());
        int rightEdge = fromPng.argbPixels()[7];
        assertEquals(255, rightEdge >>> 24);
        assertTrue((rightEdge & 0xFF) > ((rightEdge >>> 16) & 0xFF));
        assertTrue((rightEdge & 0xFF) > ((rightEdge >>> 8) & 0xFF));
        ExtractedMaskDraftV2 draft = envelope.loadAndExtractLandWater(
                request, "images/map.png", DEFAULTS, EXTRACT_DEFAULTS, () -> false);
        assertEquals(ImageLandWaterExtractorV2.ALGORITHM_VERSION, draft.algorithmVersion());
        assertEquals(fromPng.sourceChecksum(), draft.sourceChecksum());
        assertEquals(ExtractedMaskDraftV2.CLASS_LAND, draft.classAt(0, 0));
        assertEquals(ExtractedMaskDraftV2.CLASS_WATER, draft.classAt(7, 0));
        assertEquals(
                ImageLandWaterExtractorV2.extract(
                        fromPng.width(), fromPng.length(), fromPng.argbPixels(),
                        fromPng.sourceChecksum(), EXTRACT_DEFAULTS, () -> false).semanticChecksum(),
                draft.semanticChecksum());

        SanitizedArgbImageV2 fromJpeg = envelope.loadOne(request, "images/map.jpg", DEFAULTS, () -> false);
        assertEquals("image/jpeg", fromJpeg.mediaType());
        assertEquals(Sha256.file(jpeg), fromJpeg.sourceChecksum());
        ExtractedMaskDraftV2 jpegDraft = envelope.loadAndExtractLandWater(
                request, "images/map.jpg", DEFAULTS, EXTRACT_DEFAULTS, () -> false);
        assertEquals(fromJpeg.sourceChecksum(), jpegDraft.sourceChecksum());
        assertTrue(jpegDraft.waterCells() > 0);
        assertTrue(jpegDraft.landCells() > 0);
    }

    @Test
    void preservesAlphaAndAppliesExifOrientationWithoutForwardingMetadata(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path alphaPath = root.resolve("images/alpha.png");
        Files.createDirectories(alphaPath.getParent());
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x00112233);
        source.setRGB(1, 0, 0x80112233);
        source.setRGB(0, 1, 0xff112233);
        source.setRGB(1, 1, 0x40112233);
        assertTrue(ImageIO.write(source, "png", alphaPath.toFile()));

        SanitizedArgbImageV2 alpha = envelope.loadOne(request, "images/alpha.png", DEFAULTS, () -> false);
        assertEquals(0x00112233, alpha.argbPixels()[0]);
        assertEquals(0x80112233, alpha.argbPixels()[1]);
        assertEquals(0xff112233, alpha.argbPixels()[2]);
        assertEquals(0x40112233, alpha.argbPixels()[3]);

        Path jpeg = root.resolve("images/oriented.jpg");
        writeLandWaterMap(jpeg, 48, 32);
        Files.write(jpeg, withExifOrientation(Files.readAllBytes(jpeg), 6));
        SanitizedArgbImageV2 oriented = envelope.loadOne(request, "images/oriented.jpg", DEFAULTS, () -> false);
        assertTrue(oriented.metadataDetected());
        assertEquals(6, oriented.exifOrientation());
        assertEquals(32, oriented.width());
        assertEquals(48, oriented.length());
        ExtractedMaskDraftV2 draft = envelope.loadAndExtractLandWater(
                request, "images/oriented.jpg", DEFAULTS, EXTRACT_DEFAULTS, () -> false);
        assertEquals(oriented.sourceChecksum(), draft.sourceChecksum());
        assertEquals(32, draft.width());
        assertEquals(48, draft.length());
    }

    @Test
    void checksumChainAndDeterminismAreStableAcrossThreadsLocaleAndTimezone(@TempDir Path root)
            throws Exception {
        Path request = request(root);
        writeLandWaterMap(root.resolve("images/map.png"), 6, 4);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            ExtractedMaskDraftV2 expected = envelope.loadAndExtractLandWater(
                    request, "images/map.png", DEFAULTS, EXTRACT_DEFAULTS, () -> false);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(expected.semanticChecksum(), envelope.loadAndExtractLandWater(
                    request, "images/map.png", DEFAULTS, EXTRACT_DEFAULTS, () -> false).semanticChecksum());
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(expected.semanticChecksum(), one.submit(() -> envelope.loadAndExtractLandWater(
                        request, "images/map.png", DEFAULTS, EXTRACT_DEFAULTS, () -> false))
                        .get().semanticChecksum());
                assertEquals(expected.semanticChecksum(), four.submit(() -> envelope.loadAndExtractLandWater(
                        request, "images/map.png", DEFAULTS, EXTRACT_DEFAULTS, () -> false))
                        .get().semanticChecksum());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void rejectsUnsafePathsSymlinksHardLinkAliasesAndToctou(@TempDir Path root) throws Exception {
        Path request = request(root);
        for (String path : List.of(
                "/tmp/map.png", "../map.png", "images/../map.png", "C:/map.png",
                "images\\map.png", "images//map.png", "https://example.invalid/map.png")) {
            assertCode(ImageExtractionInputFailureCodeV2.INVALID_PATH_DESCRIPTOR,
                    () -> envelope.loadOne(request, path, DEFAULTS, () -> false));
        }

        Path original = writeLandWaterMap(root.resolve("images/original.png"), 4, 4);
        Path symlink = root.resolve("images/linked.png");
        Files.createSymbolicLink(symlink, original);
        assertCode(ImageExtractionInputFailureCodeV2.UNSAFE_PATH,
                () -> envelope.loadOne(request, "images/linked.png", DEFAULTS, () -> false));

        Path alias = root.resolve("images/alias.png");
        Files.createLink(alias, original);
        assertCode(ImageExtractionInputFailureCodeV2.HARD_LINK_ALIAS,
                () -> envelope.load(request, List.of("images/original.png", "images/alias.png"),
                        DEFAULTS, () -> false));

        Path copy = root.resolve("images/copy.png");
        Files.copy(original, copy);
        assertEquals(2, envelope.load(request, List.of("images/original.png", "images/copy.png"),
                DEFAULTS, () -> false).size());

        Path source = writeLandWaterMap(root.resolve("images/toctou.png"), 4, 4);
        Path replacement = writeLandWaterMap(root.resolve("replacement.png"), 4, 4);
        Path backup = root.resolve("images/toctou-backup.png");
        AtomicBoolean replaced = new AtomicBoolean();
        SecureImageExtractionEnvelopeV2 observed = new SecureImageExtractionEnvelopeV2((phase, opened) -> {
            if (phase == SecureImageExtractionEnvelopeV2.ReadPhase.AFTER_OPEN_BEFORE_READ
                    && replaced.compareAndSet(false, true)) {
                Files.move(opened, backup, StandardCopyOption.ATOMIC_MOVE);
                Files.copy(replacement, opened);
            }
        });
        assertCode(ImageExtractionInputFailureCodeV2.SOURCE_CHANGED,
                () -> observed.loadOne(request, "images/toctou.png", DEFAULTS, () -> false));
        assertTrue(Files.exists(source) || Files.exists(backup));
    }

    @Test
    void rejectsMagicSpoofCorruptAnimatedPngAndBudgets(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path images = Files.createDirectories(root.resolve("images"));

        Path invalid = images.resolve("invalid.png");
        Files.write(invalid, "not-png".getBytes(StandardCharsets.UTF_8));
        assertCode(ImageExtractionInputFailureCodeV2.INVALID_MAGIC,
                () -> envelope.loadOne(request, "images/invalid.png", DEFAULTS, () -> false));

        Path real = writeLandWaterMap(images.resolve("real.png"), 8, 8);
        Files.copy(real, images.resolve("spoof.jpg"));
        assertCode(ImageExtractionInputFailureCodeV2.UNSUPPORTED_FORMAT,
                () -> envelope.loadOne(request, "images/spoof.jpg", DEFAULTS, () -> false));

        Files.write(images.resolve("broken.png"), new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
        });
        assertCode(ImageExtractionInputFailureCodeV2.CORRUPT_IMAGE,
                () -> envelope.loadOne(request, "images/broken.png", DEFAULTS, () -> false));

        byte[] animatedBytes = insertAfterIhdr(Files.readAllBytes(real), pngChunk("acTL", new byte[]{
                0, 0, 0, 2, 0, 0, 0, 0
        }));
        Files.write(images.resolve("animated.png"), animatedBytes);
        assertCode(ImageExtractionInputFailureCodeV2.MULTI_FRAME,
                () -> envelope.loadOne(request, "images/animated.png", DEFAULTS, () -> false));

        byte[] oversized = new byte[Math.toIntExact(ImageExtractionInputLimitsV2.TRUSTED_MAXIMUM_SOURCE_BYTES + 1)];
        System.arraycopy(PNG_SIGNATURE, 0, oversized, 0, PNG_SIGNATURE.length);
        Files.write(images.resolve("large.png"), oversized);
        assertCode(ImageExtractionInputFailureCodeV2.FILE_TOO_LARGE,
                () -> envelope.loadOne(request, "images/large.png", DEFAULTS, () -> false));

        writeLandWaterMap(images.resolve("wide.png"), 100, 2);
        assertCode(ImageExtractionInputFailureCodeV2.ASPECT_RATIO_EXCEEDED,
                () -> envelope.loadOne(request, "images/wide.png", DEFAULTS, () -> false));

        writeLandWaterMap(images.resolve("pixels.png"), 2100, 2000);
        assertCode(ImageExtractionInputFailureCodeV2.PIXELS_EXCEEDED,
                () -> envelope.loadOne(request, "images/pixels.png", DEFAULTS, () -> false));

        Path small = writeLandWaterMap(images.resolve("decode-budget.png"), 64, 64);
        long fileBytes = Files.size(small);
        long loadingPeak = fileBytes + fileBytes + 64L * 1024L;
        long decodeWorking = 64L * 64L * ImageExtractionInputLimitsV2.ARGB_WORKING_BYTES_PER_PIXEL
                + ImageExtractionInputLimitsV2.DECODE_OVERHEAD_BYTES;
        assertTrue(decodeWorking > loadingPeak);
        ImageExtractionInputLimitsV2 tinyWorking = new ImageExtractionInputLimitsV2(
                fileBytes,
                fileBytes,
                4_096,
                32,
                4_000_000L,
                16_000_000L,
                decodeWorking - 1L);
        assertCode(ImageExtractionInputFailureCodeV2.WORKING_BUDGET_EXCEEDED,
                () -> envelope.loadOne(request, "images/decode-budget.png", tinyWorking, () -> false));
        Path first = writeLandWaterMap(images.resolve("first.png"), 4, 4);
        Path second = writeLandWaterMap(images.resolve("second.png"), 4, 4);
        long each = Math.max(Files.size(first), Files.size(second));
        ImageExtractionInputLimitsV2 totalLimit = new ImageExtractionInputLimitsV2(
                each, each * 2L - 1L, 4_096, 32, 4_000_000L, 16_000_000L,
                ImageExtractionInputLimitsV2.TRUSTED_MAXIMUM_DECODE_WORKING_BYTES);
        AtomicBoolean readStarted = new AtomicBoolean();
        SecureImageExtractionEnvelopeV2 observed = new SecureImageExtractionEnvelopeV2(
                (phase, source) -> readStarted.set(true));
        assertCode(ImageExtractionInputFailureCodeV2.TOTAL_BYTES_EXCEEDED,
                () -> observed.load(request, List.of("images/first.png", "images/second.png"),
                        totalLimit, () -> false));
        assertFalse(readStarted.get(), "aggregate admission must finish before any source is opened");
    }

    @Test
    void cancelStopsLoadingAndDoesNotLeakPartialDraft(@TempDir Path root) throws Exception {
        Path request = request(root);
        writeLandWaterMap(root.resolve("images/map.png"), 8, 8);
        assertThrows(CancellationException.class,
                () -> envelope.loadOne(request, "images/map.png", DEFAULTS, () -> true));
        assertThrows(CancellationException.class,
                () -> envelope.loadAndExtractLandWater(
                        request, "images/map.png", DEFAULTS, EXTRACT_DEFAULTS, () -> true));
    }

    @Test
    void trustedCeilingsCannotBeRaisedByCaller() {
        ImageExtractionInputLimitsV2 raised = new ImageExtractionInputLimitsV2(
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE);
        assertEquals(ImageExtractionInputLimitsV2.TRUSTED_MAXIMUM_SOURCE_BYTES, raised.maximumSourceBytes());
        assertEquals(ImageExtractionInputLimitsV2.TRUSTED_MAXIMUM_DIMENSION, raised.maximumDimension());
        assertEquals(ImageExtractionInputLimitsV2.TRUSTED_MAXIMUM_DECODE_WORKING_BYTES,
                raised.maximumDecodeWorkingBytes());
        assertThrows(IllegalArgumentException.class,
                () -> new ImageExtractionInputLimitsV2(0, 1, 1, 1, 1, 1, 1));
    }

    private static Path request(Path root) throws Exception {
        Path path = root.resolve("request.json");
        Files.writeString(path, "{}");
        return path;
    }

    private static Path writeLandWaterMap(Path path, int width, int height) throws Exception {
        Files.createDirectories(path.getParent());
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean jpeg = name.endsWith(".jpg") || name.endsWith(".jpeg");
        BufferedImage image = new BufferedImage(
                width, height, jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Color land = new Color(70, 140, 70);
        Color water = new Color(10, 40, 220);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x < width / 2 ? land : water).getRGB());
            }
        }
        String format = jpeg ? "JPEG" : "png";
        assertTrue(ImageIO.write(image, format, path.toFile()), "failed to write " + path);
        return path;
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

    private static byte[] insertAfterIhdr(byte[] png, byte[] chunk) {
        int position = 8;
        while (position + 12 <= png.length) {
            int length = ByteBuffer.wrap(png, position, 4).getInt();
            String type = new String(png, position + 4, 4, StandardCharsets.US_ASCII);
            int next = position + 12 + length;
            if ("IHDR".equals(type)) {
                byte[] result = new byte[png.length + chunk.length];
                System.arraycopy(png, 0, result, 0, next);
                System.arraycopy(chunk, 0, result, next, chunk.length);
                System.arraycopy(png, next, result, next + chunk.length, png.length - next);
                return result;
            }
            position = next;
        }
        throw new IllegalStateException("IHDR not found");
    }

    private static byte[] pngChunk(String type, byte[] payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(ByteBuffer.allocate(4).putInt(payload.length).array(), 0, 4);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        output.write(typeBytes, 0, 4);
        output.write(payload, 0, payload.length);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(payload);
        output.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array(), 0, 4);
        return output.toByteArray();
    }

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private static void assertCode(ImageExtractionInputFailureCodeV2 expected, Runnable runnable) {
        ImageExtractionInputExceptionV2 exception =
                assertThrows(ImageExtractionInputExceptionV2.class, runnable::run);
        assertEquals(expected, exception.failureCode());
    }
}
