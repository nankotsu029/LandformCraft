package com.github.nankotsu029.landformcraft.core.v2.binding;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.ConstraintCompilationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.ConstraintCompilationFailureCodeV2;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapFailureCode;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapInputException;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoder;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-18-06 reusable constraint-map binding: secure resolve → digest → decode → canonical XZ field.
 * Positive path uses the shipped {@code coastal-honored-400} fixture; the negatives drive the security
 * and integrity defenses (dimension mismatch, no-data, bit depth, legend, path traversal, digest
 * mismatch, decode-memory budget); determinism proves thread / locale / timezone independence.
 */
class ConstraintMapFieldBindingV2Test {
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/coastal-honored-400.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/coastal-honored-400.terrain-intent-v2.json");
    private static final ConstraintMapDecodeLimits DEFAULTS = ConstraintMapDecodeLimits.defaults();
    private static final CancellationToken TOKEN = () -> false;

    private final ConstraintMapFieldBindingV2 binding = new ConstraintMapFieldBindingV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final NumericPngEncoder encoder = new NumericPngEncoder();

    @Test
    void bindsShippedHonoredCoastMaskIntoCanonicalLandWaterField() throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(INTENT);
        TerrainIntentV2.ConstraintMapBinding mapBinding = intent.mapReferences().getFirst();

        BoundConstraintFieldV2 field = binding.bind(
                REQUEST, request, mapBinding, DEFAULTS, TOKEN);

        assertEquals(TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK, field.role());
        assertEquals("constraint-source:coast-mask", field.sourceId());
        assertEquals(400, field.width());
        assertEquals(400, field.length());
        assertEquals("constraint-pixel-center-fixed-v1", field.canonicalizationVersion());
        assertEquals(
                Sha256.file(REQUEST.resolveSibling("maps").resolve("coastal-honored-400-land-water-u8.png")),
                field.sourceChecksum());

        // Every cell is an exact land(1)/water(0) classification (the HARD mask forbids no-data), and the
        // mask carries both classes, so the field is a real registration and not a constant fill.
        boolean sawLand = false;
        boolean sawWater = false;
        for (int z = 0; z < field.length(); z += 40) {
            for (int x = 0; x < field.width(); x += 40) {
                int value = field.valueAt(x, z);
                assertTrue(value == 0 || value == 1, "canonical land-water value must be 0 or 1");
                sawLand |= value == 1;
                sawWater |= value == 0;
            }
        }
        assertTrue(sawLand && sawWater, "honored coast mask must register both land and water cells");
    }

    @Test
    void bindsIdenticalCanonicalValuesAcrossThreadLocaleAndTimezone() throws Exception {
        GenerationRequestV2 request = codec.readGenerationRequest(REQUEST);
        TerrainIntentV2.ConstraintMapBinding mapBinding =
                codec.readTerrainIntent(INTENT).mapReferences().getFirst();

        Locale locale = Locale.getDefault();
        TimeZone timeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            BoundConstraintFieldV2 first = binding.bind(
                    REQUEST, request, mapBinding, DEFAULTS, TOKEN);
            long firstDigest = fieldDigest(first);
            String firstChecksum = first.sourceChecksum();

            Locale.setDefault(Locale.forLanguageTag("en-US"));
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            AtomicReference<Long> otherThreadDigest = new AtomicReference<>();
            AtomicReference<String> otherThreadChecksum = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                BoundConstraintFieldV2 field = binding.bind(
                        REQUEST, request, mapBinding, DEFAULTS, TOKEN);
                otherThreadDigest.set(fieldDigest(field));
                otherThreadChecksum.set(field.sourceChecksum());
            });
            worker.start();
            worker.join();

            assertEquals(firstDigest, otherThreadDigest.get(),
                    "canonical field must be identical across thread / locale / timezone");
            assertEquals(firstChecksum, otherThreadChecksum.get());
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(timeZone);
        }
    }

    @Test
    void rejectsDimensionMismatchBetweenDeclaredAndDecodedSize(@TempDir Path root) throws IOException {
        // Declared 4x4 but the actual PNG is 3x2; the loader passes on bytes, the decoder catches dims.
        Path png = writeMask(root, "mask.png", 3, 2, new int[]{0, 1, 0, 1, 0, 1});
        GenerationRequestV2.ConstraintMapSource source = categoricalSource(png, 4, 4);
        assertBindCode(ConstraintMapFailureCode.DIMENSIONS_MISMATCH, root, source, hardBinding());
    }

    @Test
    void rejectsNoDataSampleUnderHardMask(@TempDir Path root) throws IOException {
        // sample 2 is declared as the no-data sentinel; a HARD mask may not contain no-data cells.
        Path png = writeMask(root, "mask.png", 2, 2, new int[]{0, 1, 2, 1});
        GenerationRequestV2.ConstraintMapSource source = categoricalSourceWithNoData(png, 2, 2, 2);
        assertBindCode(ConstraintCompilationFailureCodeV2.INVALID_NO_DATA, root, source, hardBinding());
    }

    @Test
    void rejectsBitDepthMismatchBetweenDeclaredAndDecodedSampleType(@TempDir Path root)
            throws IOException {
        // The PNG is 8-bit grayscale but the source declares a U16 categorical encoding.
        Path png = writeMask(root, "mask.png", 2, 2, new int[]{0, 1, 1, 0});
        GenerationRequestV2.ConstraintMapSource source = new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:mask", "mask.png", Sha256.file(png), 2, 2,
                GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER, identityMapping(2, 2),
                new GenerationRequestV2.CategoricalEncoding(
                        1, GenerationRequestV2.SampleType.U16, GenerationRequestV2.RasterChannel.GRAY,
                        List.of(new GenerationRequestV2.LabelMapping(0, "water"),
                                new GenerationRequestV2.LabelMapping(1, "land")),
                        new GenerationRequestV2.NoDataForbidden()));
        assertBindCode(ConstraintMapFailureCode.SAMPLE_TYPE_MISMATCH, root, source, hardBinding());
    }

    @Test
    void rejectsSampleOutsideTheDeclaredLegend(@TempDir Path root) throws IOException {
        // sample 7 has no label in the {water=0, land=1} legend.
        Path png = writeMask(root, "mask.png", 2, 2, new int[]{0, 1, 7, 0});
        GenerationRequestV2.ConstraintMapSource source = categoricalSource(png, 2, 2);
        assertBindCode(ConstraintCompilationFailureCodeV2.UNKNOWN_LABEL, root, source, hardBinding());
    }

    @Test
    void rejectsSymlinkPathTraversalOutsideTheRequestRoot(@TempDir Path root) throws IOException {
        Path outside = Files.createDirectories(root.resolve("outside"));
        Path real = writeMask(outside, "real.png", 2, 2, new int[]{0, 1, 1, 0});
        Path request = Files.createDirectories(root.resolve("request"));
        Path link = request.resolve("mask.png");
        Files.createSymbolicLink(link, real);
        GenerationRequestV2.ConstraintMapSource source = new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:mask", "mask.png", Sha256.file(real), 2, 2,
                GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER, identityMapping(2, 2),
                categoricalEncoding());
        assertBindCode(ConstraintMapFailureCode.UNSAFE_PATH, request, source, hardBinding());
    }

    @Test
    void rejectsDigestMismatchAgainstDeclaredChecksum(@TempDir Path root) throws IOException {
        Path png = writeMask(root, "mask.png", 2, 2, new int[]{0, 1, 1, 0});
        GenerationRequestV2.ConstraintMapSource source = new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:mask", "mask.png", "a".repeat(64), 2, 2,
                GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER, identityMapping(2, 2),
                categoricalEncoding());
        assertBindCode(ConstraintMapFailureCode.CHECKSUM_MISMATCH, root, source, hardBinding());
    }

    @Test
    void rejectsDecodeThatWouldExceedTheWorkingMemoryBudget(@TempDir Path root) throws IOException {
        Path png = writeMask(root, "mask.png", 2, 2, new int[]{0, 1, 1, 0});
        GenerationRequestV2.ConstraintMapSource source = categoricalSource(png, 2, 2);
        // Large enough to load the tiny source, far below the decoder's ~1 MiB fixed decode overhead.
        ConstraintMapDecodeLimits tight = new ConstraintMapDecodeLimits(
                8, 1024L * 1024L, 1024L * 1024L, 4_096, 32, 4_000_000L, 8L * 1024L * 1024L, 100_000L);
        assertThrows(ConstraintMapInputException.class,
                () -> binding.bind(root.resolve("generation-request.json"),
                        bounds(2, 2), source, hardBinding(), tight, TOKEN));
        assertCode(ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED,
                () -> binding.bind(root.resolve("generation-request.json"),
                        bounds(2, 2), source, hardBinding(), tight, TOKEN));
    }

    @Test
    void rejectsBindingWhoseSourceIdDoesNotMatchTheResolvedMap(@TempDir Path root) throws IOException {
        Path png = writeMask(root, "mask.png", 2, 2, new int[]{0, 1, 1, 0});
        GenerationRequestV2.ConstraintMapSource source = categoricalSource(png, 2, 2);
        TerrainIntentV2.ConstraintMapBinding mismatched = new TerrainIntentV2.ConstraintMapBinding(
                "coast-mask-binding", "constraint-source:other",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK, placeholderArtifact(),
                TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0);
        ConstraintCompilationExceptionV2 failure = assertThrows(ConstraintCompilationExceptionV2.class,
                () -> binding.bind(root.resolve("generation-request.json"),
                        bounds(2, 2), source, mismatched, DEFAULTS, TOKEN));
        assertEquals(ConstraintCompilationFailureCodeV2.INVALID_BINDING, failure.code());
    }

    private long fieldDigest(BoundConstraintFieldV2 field) {
        long digest = 1125899906842597L;
        for (int z = 0; z < field.length(); z++) {
            for (int x = 0; x < field.width(); x++) {
                digest = 31L * digest + field.valueAt(x, z);
            }
        }
        return digest;
    }

    private void assertBindCode(
            ConstraintMapFailureCode expected,
            Path root,
            GenerationRequestV2.ConstraintMapSource source,
            TerrainIntentV2.ConstraintMapBinding mapBinding
    ) {
        Path requestPath = root.resolve("generation-request.json");
        assertCode(expected, () -> binding.bind(requestPath, bounds(source.expectedWidth(),
                source.expectedLength()), source, mapBinding, DEFAULTS, TOKEN));
    }

    private void assertBindCode(
            ConstraintCompilationFailureCodeV2 expected,
            Path root,
            GenerationRequestV2.ConstraintMapSource source,
            TerrainIntentV2.ConstraintMapBinding mapBinding
    ) {
        Path requestPath = root.resolve("generation-request.json");
        ConstraintCompilationExceptionV2 failure = assertThrows(ConstraintCompilationExceptionV2.class,
                () -> binding.bind(requestPath, bounds(source.expectedWidth(), source.expectedLength()),
                        source, mapBinding, DEFAULTS, TOKEN));
        assertEquals(expected, failure.code());
    }

    private static void assertCode(ConstraintMapFailureCode expected, Executable executable) {
        ConstraintMapInputException failure = assertThrows(ConstraintMapInputException.class, executable);
        assertEquals(expected, failure.code());
    }

    private Path writeMask(Path root, String name, int width, int length, int[] samples)
            throws IOException {
        byte[] bytes = new byte[samples.length];
        for (int index = 0; index < samples.length; index++) {
            bytes[index] = (byte) samples[index];
        }
        Path png = root.resolve(name);
        encoder.writeU8(png, width, length, bytes);
        return png;
    }

    private static GenerationRequestV2.ConstraintMapSource categoricalSource(
            Path png, int declaredWidth, int declaredLength) throws IOException {
        return new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:mask", png.getFileName().toString(), Sha256.file(png),
                declaredWidth, declaredLength, GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER,
                identityMapping(declaredWidth, declaredLength), categoricalEncoding());
    }

    private static GenerationRequestV2.ConstraintMapSource categoricalSourceWithNoData(
            Path png, int declaredWidth, int declaredLength, int noDataSample) throws IOException {
        return new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:mask", png.getFileName().toString(), Sha256.file(png),
                declaredWidth, declaredLength, GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER,
                identityMapping(declaredWidth, declaredLength),
                new GenerationRequestV2.CategoricalEncoding(
                        1, GenerationRequestV2.SampleType.U8, GenerationRequestV2.RasterChannel.GRAY,
                        List.of(new GenerationRequestV2.LabelMapping(0, "water"),
                                new GenerationRequestV2.LabelMapping(1, "land")),
                        new GenerationRequestV2.NoDataSentinel(noDataSample)));
    }

    private static GenerationRequestV2.CategoricalEncoding categoricalEncoding() {
        return new GenerationRequestV2.CategoricalEncoding(
                1, GenerationRequestV2.SampleType.U8, GenerationRequestV2.RasterChannel.GRAY,
                List.of(new GenerationRequestV2.LabelMapping(0, "water"),
                        new GenerationRequestV2.LabelMapping(1, "land")),
                new GenerationRequestV2.NoDataForbidden());
    }

    private static GenerationRequestV2.CoordinateMapping identityMapping(int width, int length) {
        return new GenerationRequestV2.CoordinateMapping(
                GenerationRequestV2.CoordinateOrigin.NORTH_WEST, GenerationRequestV2.XAxis.EAST,
                GenerationRequestV2.ZAxis.SOUTH, GenerationRequestV2.PixelReference.PIXEL_CENTER,
                GenerationRequestV2.AspectMismatchPolicy.REJECT, GenerationRequestV2.QuarterTurn.DEGREES_0,
                false, false, new GenerationRequestV2.PixelCrop(0, 0, width, length));
    }

    private static TerrainIntentV2.ConstraintMapBinding hardBinding() {
        return new TerrainIntentV2.ConstraintMapBinding(
                "coast-mask-binding", "constraint-source:mask",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK, placeholderArtifact(),
                TerrainIntentV2.Strength.HARD, TerrainIntentV2.Sampling.NEAREST, 0, 0);
    }

    private static String placeholderArtifact() {
        return "constraint:land-water:sha256-" + "0".repeat(64);
    }

    private static GenerationRequestV2.Bounds bounds(int width, int length) {
        return new GenerationRequestV2.Bounds(width, length, 32, 72, 50);
    }
}
