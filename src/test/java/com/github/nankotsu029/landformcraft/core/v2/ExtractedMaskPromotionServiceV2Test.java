package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapSourceSpec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.DecodedNumericRaster;
import com.github.nankotsu029.landformcraft.format.v2.constraint.LoadedConstraintMapSource;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngDecoder;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoding;
import com.github.nankotsu029.landformcraft.format.v2.constraint.SecureConstraintMapSourceLoader;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskPromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageLandWaterExtractorV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageMaskExtractionLimitsV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskPromotionRecordV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractedMaskPromotionServiceV2Test {
    private static final String SOURCE = "ab".repeat(32);

    private final ExtractedMaskPromotionServiceV2 service = new ExtractedMaskPromotionServiceV2();
    private final ExtractedMaskPromotionRecordCodecV2 codec = new ExtractedMaskPromotionRecordCodecV2();
    private final SecureConstraintMapSourceLoader loader = new SecureConstraintMapSourceLoader();
    private final NumericPngDecoder decoder = new NumericPngDecoder();

    @Test
    void promotesDraftToNumericPngAndRoundTripsThroughStrictDecoder(@TempDir Path root) throws Exception {
        ExtractedMaskDraftV2 draft = confidentLandWaterDraft();
        Path target = root.resolve("promoted");
        ExtractedMaskPromotionRecordV2 record = service.promote(
                target,
                draft,
                ExtractedMaskPromotionOptionsV2.rejectBelow(1),
                () -> false);

        assertEquals(draft.sourceChecksum(), record.sourceChecksum());
        assertEquals(draft.semanticChecksum(), record.draftSemanticChecksum());
        assertEquals(0, record.noDataCells());
        assertTrue(Files.isRegularFile(target.resolve(ExtractedMaskPromotionRecordV2.MAP_PATH)));

        ExtractedMaskPromotionRecordV2 verified = codec.readAndVerify(
                target.resolve(ExtractedMaskPromotionRecordCodecV2.INDEX_FILE_NAME), target, () -> false);
        assertEquals(record, verified);

        DecodedNumericRaster raster = decodePublished(target, record);
        assertEquals(0, raster.sample(0, 0));
        assertEquals(1, raster.sample(1, 0));
        assertEquals(0, raster.sample(0, 1));
        assertEquals(1, raster.sample(1, 1));
        assertEquals(record.mapSha256(), raster.sourceChecksum());
    }

    @Test
    void thresholdBoundarySuppressesLowConfidenceCells(@TempDir Path root) throws Exception {
        ExtractedMaskDraftV2 draft = mixedConfidenceDraft();
        ExtractedMaskPromotionExceptionV2 rejected = assertThrows(
                ExtractedMaskPromotionExceptionV2.class,
                () -> service.promote(
                        root.resolve("reject"),
                        draft,
                        ExtractedMaskPromotionOptionsV2.rejectBelow(200),
                        () -> false));
        assertEquals(ExtractedMaskPromotionFailureCodeV2.UNRESOLVED_UNKNOWN, rejected.code());

        ExtractedMaskPromotionRecordV2 mapped = service.promote(
                root.resolve("mapped"),
                draft,
                ExtractedMaskPromotionOptionsV2.mapUnknownToLand(200),
                () -> false);
        assertTrue(mapped.thresholdSuppressedCells() >= 1);
        DecodedNumericRaster raster = decodePublished(root.resolve("mapped"), mapped);
        assertEquals(1, raster.sample(1, 0));
    }

    @Test
    void unknownHandlingIsMandatoryAndNoDataSentinelRoundTrips(@TempDir Path root) throws Exception {
        assertThrows(NullPointerException.class, () -> new ExtractedMaskPromotionOptionsV2(
                1, null, null));
        assertThrows(IllegalArgumentException.class, () -> ExtractedMaskPromotionOptionsV2
                .mapUnknownToNoData(1, 0));
        assertThrows(IllegalArgumentException.class, () -> ExtractedMaskPromotionOptionsV2
                .mapUnknownToNoData(1, 1));

        ExtractedMaskDraftV2 draft = draftWithUnknown();
        ExtractedMaskPromotionRecordV2 record = service.promote(
                root.resolve("nodata"),
                draft,
                ExtractedMaskPromotionOptionsV2.mapUnknownToNoData(
                        1, ExtractedMaskPromotionOptionsV2.DEFAULT_NODATA_SAMPLE),
                () -> false);
        assertEquals(ExtractedMaskPromotionOptionsV2.DEFAULT_NODATA_SAMPLE, record.noDataSample());
        assertTrue(record.noDataCells() >= 1);
        DecodedNumericRaster raster = decodePublished(root.resolve("nodata"), record);
        boolean sawNoData = false;
        for (int z = 0; z < raster.length(); z++) {
            for (int x = 0; x < raster.width(); x++) {
                int sample = raster.sample(x, z);
                if (sample == ExtractedMaskPromotionOptionsV2.DEFAULT_NODATA_SAMPLE) {
                    sawNoData = true;
                } else {
                    assertTrue(sample == 0 || sample == 1);
                }
            }
        }
        assertTrue(sawNoData);
    }

    @Test
    void rejectsExtraMissingAndChecksumTampering(@TempDir Path root) throws Exception {
        Path target = root.resolve("promoted");
        service.promote(
                target,
                confidentLandWaterDraft(),
                ExtractedMaskPromotionOptionsV2.rejectBelow(1),
                () -> false);

        Files.writeString(target.resolve("extra.bin"), "nope");
        assertThrows(Exception.class, () -> codec.readAndVerify(
                target.resolve(ExtractedMaskPromotionRecordCodecV2.INDEX_FILE_NAME), target, () -> false));
        Files.delete(target.resolve("extra.bin"));

        byte[] png = Files.readAllBytes(target.resolve(ExtractedMaskPromotionRecordV2.MAP_PATH));
        png[png.length - 1] ^= 1;
        Files.write(target.resolve(ExtractedMaskPromotionRecordV2.MAP_PATH), png);
        assertThrows(Exception.class, () -> codec.readAndVerify(
                target.resolve(ExtractedMaskPromotionRecordCodecV2.INDEX_FILE_NAME), target, () -> false));
    }

    @Test
    void cancelCleansUpBeforeAtomicCommit(@TempDir Path root) throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Path target = root.resolve("cancelled");
        assertThrows(CancellationException.class, () -> service.promote(
                target,
                confidentLandWaterDraft(),
                ExtractedMaskPromotionOptionsV2.rejectBelow(1),
                () -> checks.incrementAndGet() > 2));
        assertFalse(Files.exists(target));
        try (var files = Files.list(root)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString()
                    .startsWith(".tmp-extracted-mask-promotion-")));
        }
    }

    @Test
    void promotionIsDeterministicAcrossLocaleTimezoneAndThreads(@TempDir Path root) throws Exception {
        ExtractedMaskDraftV2 draft = confidentLandWaterDraft();
        ExtractedMaskPromotionOptionsV2 options = ExtractedMaskPromotionOptionsV2.mapUnknownToWater(1);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            ExtractedMaskPromotionRecordV2 expected = service.promote(
                    root.resolve("a"), draft, options, () -> false);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            ExtractedMaskPromotionRecordV2 second = service.promote(
                    root.resolve("b"), draft, options, () -> false);
            assertEquals(expected.canonicalChecksum(), second.canonicalChecksum());
            assertEquals(expected.mapSha256(), second.mapSha256());
            try (var pool = Executors.newFixedThreadPool(4)) {
                assertEquals(expected.canonicalChecksum(), pool.submit(() -> service.promote(
                        root.resolve("c"), draft, options, () -> false)).get().canonicalChecksum());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    private DecodedNumericRaster decodePublished(Path target, ExtractedMaskPromotionRecordV2 record) {
        ConstraintMapSourceSpec specification = new ConstraintMapSourceSpec(
                record.sourceId(),
                record.mapPath(),
                record.mapSha256(),
                record.width(),
                record.length());
        List<LoadedConstraintMapSource> loaded = loader.load(
                target.resolve("request-v2.json"),
                List.of(specification),
                ConstraintMapDecodeLimits.defaults(),
                () -> false);
        return decoder.decode(
                loaded.getFirst(),
                specification,
                new NumericPngEncoding(
                        NumericPngEncoding.CURRENT_VERSION,
                        NumericPngEncoding.NumericKind.CATEGORICAL,
                        NumericPngEncoding.SampleType.U8),
                ConstraintMapDecodeLimits.defaults(),
                () -> false);
    }

    private static ExtractedMaskDraftV2 confidentLandWaterDraft() {
        int[] pixels = {
                argb(255, 10, 40, 220), argb(255, 70, 140, 70),
                argb(255, 10, 40, 220), argb(255, 120, 90, 40),
        };
        return ImageLandWaterExtractorV2.extract(
                2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false);
    }

    private static ExtractedMaskDraftV2 mixedConfidenceDraft() {
        int[] pixels = {
                argb(255, 10, 40, 220), argb(80, 70, 140, 70),
                argb(255, 10, 40, 220), argb(255, 120, 90, 40),
        };
        return ImageLandWaterExtractorV2.extract(
                2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false);
    }

    private static ExtractedMaskDraftV2 draftWithUnknown() {
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
