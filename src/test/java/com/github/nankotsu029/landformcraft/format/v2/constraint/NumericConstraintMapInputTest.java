package com.github.nankotsu029.landformcraft.format.v2.constraint;

import com.github.nankotsu029.landformcraft.format.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericConstraintMapInputTest {
    private static final ConstraintMapDecodeLimits DEFAULTS = ConstraintMapDecodeLimits.defaults();
    private final SecureConstraintMapSourceLoader loader = new SecureConstraintMapSourceLoader();
    private final NumericPngDecoder decoder = new NumericPngDecoder();

    @Test
    void decodesExactUnsignedU8CategoricalSamples(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path png = writeGray(root.resolve("inputs/labels.png"), false, new int[][]{
                {0, 17, 128},
                {254, 255, 1}
        });
        ConstraintMapSourceSpec spec = spec("labels", "inputs/labels.png", png, 3, 2);

        DecodedNumericRaster raster = decoder.decode(
                loadOne(request, spec), spec,
                new NumericPngEncoding(1, NumericPngEncoding.NumericKind.CATEGORICAL,
                        NumericPngEncoding.SampleType.U8),
                DEFAULTS, () -> false);

        assertEquals(3, raster.width());
        assertEquals(2, raster.length());
        assertEquals(NumericPngEncoding.NumericKind.CATEGORICAL, raster.kind());
        assertEquals(NumericPngEncoding.SampleType.U8, raster.sampleType());
        assertEquals(0, raster.sample(0, 0));
        assertEquals(128, raster.sample(2, 0));
        assertEquals(255, raster.sample(1, 1));
        assertEquals(Sha256.file(png), raster.sourceChecksum());
    }

    @Test
    void decodesExactUnsignedU16HeightSamplesWithoutArgbConversion(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path png = writeGray(root.resolve("inputs/height.png"), true, new int[][]{
                {0, 1, 0x1234},
                {0x8000, 0xfffe, 0xffff}
        });
        ConstraintMapSourceSpec spec = spec("height", "inputs/height.png", png, 3, 2);

        DecodedNumericRaster raster = decoder.decode(
                loadOne(request, spec), spec,
                new NumericPngEncoding(1, NumericPngEncoding.NumericKind.HEIGHT,
                        NumericPngEncoding.SampleType.U16),
                DEFAULTS, () -> false);

        assertEquals(0x1234, raster.sample(2, 0));
        assertEquals(0x8000, raster.sample(0, 1));
        assertEquals(0xfffe, raster.sample(1, 1));
        assertEquals(0xffff, raster.sample(2, 1));
    }

    @Test
    void rejectsAbsoluteTraversalAndBackslashPaths() {
        for (String path : List.of("/tmp/map.png", "../map.png", "inputs/../map.png",
                "C:/map.png", "inputs\\map.png", "inputs//map.png",
                "https://example.invalid/map.png", "data:image/png;base64,AAAA")) {
            assertCode(ConstraintMapFailureCode.UNSAFE_PATH,
                    () -> new ConstraintMapSourceSpec(
                            "constraint-source:path", path, "0".repeat(64), 1, 1));
        }
    }

    @Test
    void rejectsSymlinksAndHardLinkAliasesWithinOneRequest(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path original = writeGray(root.resolve("inputs/original.png"), false, new int[][]{{1}});
        Path symlink = root.resolve("inputs/symlink.png");
        Files.createSymbolicLink(symlink, original);
        ConstraintMapSourceSpec linked = spec("linked", "inputs/symlink.png", original, 1, 1);
        assertCode(ConstraintMapFailureCode.UNSAFE_PATH,
                () -> loader.load(request, List.of(linked), DEFAULTS, () -> false));

        Path alias = root.resolve("inputs/alias.png");
        Files.createLink(alias, original);
        ConstraintMapSourceSpec first = spec("first", "inputs/original.png", original, 1, 1);
        ConstraintMapSourceSpec second = spec("second", "inputs/alias.png", alias, 1, 1);
        assertCode(ConstraintMapFailureCode.HARD_LINK_ALIAS,
                () -> loader.load(request, List.of(first, second), DEFAULTS, () -> false));
    }

    @Test
    void rejectsMagicFormatChecksumAndByteBudgetFailures(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path invalid = root.resolve("inputs/invalid.png");
        Files.createDirectories(invalid.getParent());
        Files.write(invalid, "not-png".getBytes(StandardCharsets.UTF_8));
        ConstraintMapSourceSpec invalidSpec = spec("invalid", "inputs/invalid.png", invalid, 1, 1);
        assertCode(ConstraintMapFailureCode.INVALID_MAGIC,
                () -> loader.load(request, List.of(invalidSpec), DEFAULTS, () -> false));

        Path png = writeGray(root.resolve("inputs/valid.png"), false, new int[][]{{1}});
        Path wrongExtension = root.resolve("inputs/valid.jpg");
        Files.copy(png, wrongExtension);
        ConstraintMapSourceSpec formatSpec = spec("format", "inputs/valid.jpg", wrongExtension, 1, 1);
        assertCode(ConstraintMapFailureCode.UNSUPPORTED_FORMAT,
                () -> loader.load(request, List.of(formatSpec), DEFAULTS, () -> false));

        ConstraintMapSourceSpec badChecksum = new ConstraintMapSourceSpec(
                "constraint-source:checksum", "inputs/valid.png", "0".repeat(64), 1, 1);
        assertCode(ConstraintMapFailureCode.CHECKSUM_MISMATCH,
                () -> loader.load(request, List.of(badChecksum), DEFAULTS, () -> false));

        ConstraintMapDecodeLimits tinySource = limits(4, 4, 4_096, 4_000_000,
                16 * 1024 * 1024L, 32 * 1024 * 1024L);
        assertCode(ConstraintMapFailureCode.FILE_TOO_LARGE,
                () -> loader.load(request, List.of(spec("large", "inputs/valid.png", png, 1, 1)),
                        tinySource, () -> false));

        Path secondPng = root.resolve("inputs/second.png");
        Files.copy(png, secondPng);
        long each = Files.size(png);
        ConstraintMapDecodeLimits totalLimit = limits(each, each * 2L - 1L, 4_096, 4_000_000,
                16 * 1024 * 1024L, 32 * 1024 * 1024L);
        AtomicBoolean readStarted = new AtomicBoolean();
        SecureConstraintMapSourceLoader preflightingLoader = new SecureConstraintMapSourceLoader(
                (phase, source) -> readStarted.set(true));
        assertCode(ConstraintMapFailureCode.TOTAL_BYTES_EXCEEDED,
                () -> preflightingLoader.load(request, List.of(
                        spec("total-a", "inputs/valid.png", png, 1, 1),
                        spec("total-b", "inputs/second.png", secondPng, 1, 1)
                ), totalLimit, () -> false));
        assertFalse(readStarted.get(), "aggregate admission must finish before any source is opened");
    }

    @Test
    void rejectsSourceLoadingPeakBeforeAnySourceIsOpened(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path first = writeGray(root.resolve("inputs/first.png"), false, new int[][]{{1}});
        Path second = writeGray(root.resolve("inputs/second.png"), false, new int[][]{{2}});
        long firstBytes = Files.size(first);
        long secondBytes = Files.size(second);
        long totalBytes = firstBytes + secondBytes;
        long maximumBytes = Math.max(firstBytes, secondBytes);
        ConstraintMapDecodeLimits limits = limits(
                maximumBytes, totalBytes, 4_096, 4_000_000,
                8L * 1024L * 1024L, totalBytes + maximumBytes + 64L * 1024L - 1L);
        AtomicBoolean readStarted = new AtomicBoolean();
        SecureConstraintMapSourceLoader observed = new SecureConstraintMapSourceLoader(
                (phase, source) -> readStarted.set(true));

        assertCode(ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED, () -> observed.load(
                request,
                List.of(
                        spec("first", "inputs/first.png", first, 1, 1),
                        spec("second", "inputs/second.png", second, 1, 1)),
                limits,
                () -> false));
        assertFalse(readStarted.get(), "working-set admission must finish before any source is opened");
    }

    @Test
    void detectsDeterministicToctouReplacement(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path source = writeGray(root.resolve("inputs/map.png"), false, new int[][]{{1, 2}, {3, 4}});
        Path replacement = writeGray(root.resolve("replacement.png"), false, new int[][]{{9, 8}, {7, 6}});
        Path backup = root.resolve("inputs/map-backup.png");
        AtomicBoolean replaced = new AtomicBoolean();
        SecureConstraintMapSourceLoader observed = new SecureConstraintMapSourceLoader((phase, opened) -> {
            if (phase == SecureConstraintMapSourceLoader.ReadPhase.AFTER_OPEN_BEFORE_READ
                    && replaced.compareAndSet(false, true)) {
                Files.move(opened, backup, StandardCopyOption.ATOMIC_MOVE);
                Files.copy(replacement, opened);
            }
        });
        ConstraintMapSourceSpec spec = spec("toctou", "inputs/map.png", source, 2, 2);

        assertCode(ConstraintMapFailureCode.SOURCE_CHANGED,
                () -> observed.load(request, List.of(spec), DEFAULTS, () -> false));
    }

    @Test
    void cancellationInterruptsSourceLoadingAndNumericDecode(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path png = writeGray(root.resolve("inputs/map.png"), false, new int[][]{{1, 2}, {3, 4}});
        ConstraintMapSourceSpec spec = spec("cancel", "inputs/map.png", png, 2, 2);

        assertThrows(CancellationException.class,
                () -> loader.load(request, List.of(spec), DEFAULTS, () -> true));
        LoadedConstraintMapSource loaded = loadOne(request, spec);
        assertThrows(CancellationException.class, () -> decoder.decode(
                loaded, spec,
                new NumericPngEncoding(1, NumericPngEncoding.NumericKind.CATEGORICAL,
                        NumericPngEncoding.SampleType.U8),
                DEFAULTS, () -> true));
    }

    @Test
    void rejectsMalformedCrcAnimatedPngRgbAndWrongDeclaredSampleType(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path png = writeGray(root.resolve("inputs/gray.png"), false, new int[][]{{1, 2}, {3, 4}});
        byte[] corrupt = Files.readAllBytes(png);
        corrupt[corrupt.length - 1] ^= 1;
        Path corruptPath = root.resolve("inputs/corrupt.png");
        Files.write(corruptPath, corrupt);
        ConstraintMapSourceSpec corruptSpec = spec("corrupt", "inputs/corrupt.png", corruptPath, 2, 2);
        assertDecodeCode(request, corruptSpec,
                new NumericPngEncoding(1, NumericPngEncoding.NumericKind.CATEGORICAL,
                        NumericPngEncoding.SampleType.U8),
                DEFAULTS, ConstraintMapFailureCode.MALFORMED_IMAGE);

        Path animated = root.resolve("inputs/animated.png");
        Files.write(animated, insertAfterIhdr(Files.readAllBytes(png), pngChunk("acTL", new byte[]{
                0, 0, 0, 2, 0, 0, 0, 0
        })));
        ConstraintMapSourceSpec animatedSpec = spec("animated", "inputs/animated.png", animated, 2, 2);
        assertDecodeCode(request, animatedSpec,
                new NumericPngEncoding(1, NumericPngEncoding.NumericKind.CATEGORICAL,
                        NumericPngEncoding.SampleType.U8),
                DEFAULTS, ConstraintMapFailureCode.MULTI_FRAME);

        Path rgb = writeRgb(root.resolve("inputs/rgb.png"), 2, 2);
        ConstraintMapSourceSpec rgbSpec = spec("rgb", "inputs/rgb.png", rgb, 2, 2);
        assertDecodeCode(request, rgbSpec,
                new NumericPngEncoding(1, NumericPngEncoding.NumericKind.CATEGORICAL,
                        NumericPngEncoding.SampleType.U8),
                DEFAULTS, ConstraintMapFailureCode.SAMPLE_TYPE_MISMATCH);

        ConstraintMapSourceSpec graySpec = spec("gray", "inputs/gray.png", png, 2, 2);
        assertDecodeCode(request, graySpec,
                new NumericPngEncoding(1, NumericPngEncoding.NumericKind.HEIGHT,
                        NumericPngEncoding.SampleType.U16),
                DEFAULTS, ConstraintMapFailureCode.SAMPLE_TYPE_MISMATCH);
    }

    @Test
    void rejectsPaletteChunksInGrayscaleAndDuplicatePalettes(@TempDir Path root) throws Exception {
        Path request = request(root);
        Path original = writeGray(root.resolve("inputs/original.png"), false, new int[][]{{1, 2}});
        byte[] palette = pngChunk("PLTE", new byte[]{0, 0, 0});

        Path grayscalePalette = root.resolve("inputs/grayscale-palette.png");
        Files.write(grayscalePalette, insertAfterIhdr(Files.readAllBytes(original), palette));
        ConstraintMapSourceSpec grayscaleSpec = spec(
                "grayscale-palette", "inputs/grayscale-palette.png", grayscalePalette, 2, 1);
        assertDecodeCode(request, grayscaleSpec, u8Height(), DEFAULTS,
                ConstraintMapFailureCode.UNSUPPORTED_FORMAT);

        Path duplicatePalette = root.resolve("inputs/duplicate-palette.png");
        byte[] once = insertAfterIhdr(Files.readAllBytes(original), palette);
        Files.write(duplicatePalette, insertAfterIhdr(once, palette));
        ConstraintMapSourceSpec duplicateSpec = spec(
                "duplicate-palette", "inputs/duplicate-palette.png", duplicatePalette, 2, 1);
        assertDecodeCode(request, duplicateSpec, u8Height(), DEFAULTS,
                ConstraintMapFailureCode.MALFORMED_IMAGE);
    }

    @Test
    void rejectsDimensionPixelDecodedAndWorkingSetBudgetsBeforeSampleCopy(@TempDir Path root) throws Exception {
        Path request = request(root);
        int[][] values = new int[10][10];
        Path png = writeGray(root.resolve("inputs/map.png"), false, values);

        ConstraintMapSourceSpec wrongDimensions = spec("wrong", "inputs/map.png", png, 9, 10);
        assertDecodeCode(request, wrongDimensions, u8Height(), DEFAULTS,
                ConstraintMapFailureCode.DIMENSIONS_MISMATCH);

        ConstraintMapSourceSpec spec = spec("budget", "inputs/map.png", png, 10, 10);
        ConstraintMapDecodeLimits pixels = limits(Files.size(png), 1_024 * 1_024L,
                4_096, 99, 1_024, 4L * 1024 * 1024);
        assertDecodeCode(request, spec, u8Height(), pixels, ConstraintMapFailureCode.PIXELS_EXCEEDED);

        ConstraintMapDecodeLimits decoded = limits(Files.size(png), 1_024 * 1_024L,
                4_096, 100, 99, 4L * 1024 * 1024);
        assertDecodeCode(request, spec, u8Height(), decoded,
                ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED);

        ConstraintMapDecodeLimits working = limits(Files.size(png), 1_024 * 1_024L,
                4_096, 100, 1_024, 1_024);
        assertDecodeCode(request, spec, u8Height(), working,
                ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED);
    }

    @Test
    void rejectsFutureDecoderVersion() {
        assertCode(ConstraintMapFailureCode.FUTURE_VERSION,
                () -> new NumericPngEncoding(2, NumericPngEncoding.NumericKind.HEIGHT,
                        NumericPngEncoding.SampleType.U16));
    }

    @Test
    void estimatesAThousandSquareU16DecodeWithinTheDefaultWorkingBudget() {
        long peak = NumericPngDecoder.estimatedPeakBytes(
                8L * 1024L * 1024L, 1_000, 1_000, NumericPngEncoding.SampleType.U16);

        assertEquals(16L * 1024L * 1024L + 4_000_000L + 1024L * 1024L, peak);
        assertTrue(peak < DEFAULTS.maximumWorkingBytes());
    }

    @Test
    void clampsCallerDecodeLimitsToTrustedPerSourcePolicy(@TempDir Path root) throws Exception {
        ConstraintMapDecodeLimits limits = new ConstraintMapDecodeLimits(
                Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);

        assertEquals(ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_SOURCES, limits.maximumSources());
        assertEquals(ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_SOURCE_BYTES, limits.maximumSourceBytes());
        assertEquals(ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_TOTAL_SOURCE_BYTES,
                limits.maximumTotalSourceBytes());
        assertEquals(ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_DIMENSION, limits.maximumDimension());
        assertEquals(ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_ASPECT_RATIO, limits.maximumAspectRatio());
        assertEquals(ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_PIXELS, limits.maximumPixels());
        assertEquals(ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_DECODED_SAMPLE_BYTES,
                limits.maximumDecodedSampleBytes());
        assertEquals(ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_WORKING_BYTES, limits.maximumWorkingBytes());

        Path request = request(root);
        Path oversized = root.resolve("inputs/oversized.png");
        Files.createDirectories(oversized.getParent());
        Files.write(oversized,
                new byte[Math.toIntExact(ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_SOURCE_BYTES + 1L)]);
        ConstraintMapSourceSpec spec = new ConstraintMapSourceSpec(
                "constraint-source:oversized", "inputs/oversized.png", "0".repeat(64), 1, 1);
        assertCode(ConstraintMapFailureCode.FILE_TOO_LARGE,
                () -> loader.load(request, List.of(spec), limits, () -> false));
    }

    private LoadedConstraintMapSource loadOne(Path request, ConstraintMapSourceSpec spec) {
        return loader.load(request, List.of(spec), DEFAULTS, () -> false).getFirst();
    }

    private void assertDecodeCode(
            Path request,
            ConstraintMapSourceSpec spec,
            NumericPngEncoding encoding,
            ConstraintMapDecodeLimits limits,
            ConstraintMapFailureCode expected
    ) {
        LoadedConstraintMapSource loaded = loader.load(request, List.of(spec),
                withSourceAllowance(limits, loadedFileSize(request, spec)), () -> false).getFirst();
        assertCode(expected, () -> decoder.decode(loaded, spec, encoding, limits, () -> false));
    }

    private static long loadedFileSize(Path request, ConstraintMapSourceSpec spec) {
        try {
            return Files.size(request.toAbsolutePath().normalize().getParent().resolve(spec.relativePath()));
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static ConstraintMapDecodeLimits withSourceAllowance(
            ConstraintMapDecodeLimits limits,
            long sourceBytes
    ) {
        long maximumSource = Math.max(sourceBytes, limits.maximumSourceBytes());
        long maximumTotal = Math.max(maximumSource, limits.maximumTotalSourceBytes());
        long loaderWorking = Math.max(
                limits.maximumWorkingBytes(), Math.addExact(Math.multiplyExact(sourceBytes, 2L), 64L * 1024L));
        return new ConstraintMapDecodeLimits(
                limits.maximumSources(), maximumSource, maximumTotal,
                limits.maximumDimension(), limits.maximumAspectRatio(), limits.maximumPixels(),
                limits.maximumDecodedSampleBytes(), loaderWorking);
    }

    private static NumericPngEncoding u8Height() {
        return new NumericPngEncoding(1, NumericPngEncoding.NumericKind.HEIGHT,
                NumericPngEncoding.SampleType.U8);
    }

    private static ConstraintMapDecodeLimits limits(
            long sourceBytes,
            long totalSourceBytes,
            int maximumDimension,
            long maximumPixels,
            long maximumDecodedBytes,
            long maximumWorkingBytes
    ) {
        return new ConstraintMapDecodeLimits(
                32, sourceBytes, totalSourceBytes, maximumDimension, 32,
                maximumPixels, maximumDecodedBytes, maximumWorkingBytes);
    }

    private static Path request(Path root) throws Exception {
        Path request = root.resolve("request-v2.json");
        Files.writeString(request, "{}");
        return request;
    }

    private static ConstraintMapSourceSpec spec(
            String id,
            String relative,
            Path source,
            int width,
            int length
    ) throws Exception {
        return new ConstraintMapSourceSpec(
                "constraint-source:" + id, relative, Sha256.file(source), width, length);
    }

    private static Path writeGray(Path path, boolean u16, int[][] values) throws Exception {
        Files.createDirectories(path.getParent());
        int length = values.length;
        int width = values[0].length;
        BufferedImage image = new BufferedImage(
                width, length, u16 ? BufferedImage.TYPE_USHORT_GRAY : BufferedImage.TYPE_BYTE_GRAY);
        for (int z = 0; z < length; z++) {
            if (values[z].length != width) {
                throw new IllegalArgumentException("test raster rows must have equal width");
            }
            for (int x = 0; x < width; x++) {
                image.getRaster().setSample(x, z, 0, values[z][x]);
            }
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()));
        return path;
    }

    private static Path writeRgb(Path path, int width, int length) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, z, 0xff336699);
            }
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()));
        return path;
    }

    private static byte[] insertAfterIhdr(byte[] png, byte[] chunk) throws Exception {
        int insertion = 8 + 4 + 4 + 13 + 4;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(png.length + chunk.length)) {
            output.write(png, 0, insertion);
            output.write(chunk);
            output.write(png, insertion, png.length - insertion);
            return output.toByteArray();
        }
    }

    private static byte[] pngChunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        ByteBuffer result = ByteBuffer.allocate(12 + data.length);
        result.putInt(data.length);
        result.put(typeBytes);
        result.put(data);
        result.putInt((int) crc.getValue());
        return result.array();
    }

    private static void assertCode(
            ConstraintMapFailureCode expected,
            org.junit.jupiter.api.function.Executable operation
    ) {
        ConstraintMapInputException exception = assertThrows(ConstraintMapInputException.class, operation);
        assertEquals(expected, exception.code());
    }
}
