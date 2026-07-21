package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapSourceSpec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.DecodedNumericRaster;
import com.github.nankotsu029.landformcraft.format.v2.constraint.LoadedConstraintMapSource;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngDecoder;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoding;
import com.github.nankotsu029.landformcraft.format.v2.constraint.SecureConstraintMapSourceLoader;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuideDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuidePromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageHeightGuideExtractorV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageMaskExtractionLimitsV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedHeightGuidePromotionRecordV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

class ExtractedHeightGuidePromotionServiceV2Test {
    private static final String SOURCE = "ab".repeat(32);

    private final ExtractedHeightGuidePromotionServiceV2 service =
            new ExtractedHeightGuidePromotionServiceV2();
    private final ExtractedHeightGuidePromotionRecordCodecV2 codec =
            new ExtractedHeightGuidePromotionRecordCodecV2();
    private final SecureConstraintMapSourceLoader loader = new SecureConstraintMapSourceLoader();
    private final NumericPngDecoder decoder = new NumericPngDecoder();

    @ParameterizedTest
    @EnumSource(GenerationRequestV2.HeightValueMeaning.class)
    void promotesDraftThroughStrictDecoderWithResidualConsistency(
            GenerationRequestV2.HeightValueMeaning meaning,
            @TempDir Path root
    ) throws Exception {
        ExtractedHeightGuideDraftV2 draft = sampleDraft();
        GenerationRequestV2.Bounds bounds = new GenerationRequestV2.Bounds(2, 2, 0, 400, 50);
        Path target = root.resolve(meaning.name().toLowerCase(Locale.ROOT));
        ExtractedHeightGuidePromotionRecordV2 record = service.promote(
                target,
                draft,
                ExtractedHeightGuidePromotionOptionsV2.of(1, meaning, 1_000_000L, 0L),
                bounds,
                () -> false);

        assertEquals(draft.sourceChecksum(), record.sourceChecksum());
        assertEquals(draft.semanticChecksum(), record.draftSemanticChecksum());
        assertEquals(meaning.name(), record.valueMeaning());
        assertEquals(ExtractedHeightGuideDraftV2.SAMPLE_SPACE_DECLARATION, record.sampleSpaceDeclaration());

        ExtractedHeightGuidePromotionRecordV2 verified = codec.readAndVerify(
                target.resolve(ExtractedHeightGuidePromotionRecordCodecV2.INDEX_FILE_NAME),
                target,
                () -> false);
        assertEquals(record, verified);

        DecodedNumericRaster raster = decodePublished(target, record);
        assertEquals(record.mapSha256(), raster.sourceChecksum());
        assertEquals(draft.isNoData(0, 0) ? 255 : draft.sampleAt(0, 0), raster.sample(0, 0));
        assertEquals(draft.isNoData(1, 0) ? 255 : draft.sampleAt(1, 0), raster.sample(1, 0));
        assertEquals(255, raster.sample(0, 1));
        assertEquals(draft.sampleAt(1, 1), raster.sample(1, 1));
    }

    @Test
    void thresholdSuppressesLowConfidenceToNoData(@TempDir Path root) throws Exception {
        ExtractedHeightGuideDraftV2 draft = sampleDraft();
        ExtractedHeightGuidePromotionRecordV2 record = service.promote(
                root.resolve("threshold"),
                draft,
                ExtractedHeightGuidePromotionOptionsV2.of(
                        250,
                        GenerationRequestV2.HeightValueMeaning.BLOCKS_ABOVE_REQUEST_MIN_Y,
                        1_000_000L,
                        0L),
                new GenerationRequestV2.Bounds(2, 2, 0, 400, 50),
                () -> false);
        assertTrue(record.thresholdSuppressedCells() >= 1 || record.noDataCells() >= 1);
        DecodedNumericRaster raster = decodePublished(root.resolve("threshold"), record);
        boolean sawNoData = false;
        for (int z = 0; z < raster.length(); z++) {
            for (int x = 0; x < raster.width(); x++) {
                if (raster.sample(x, z) == 255) {
                    sawNoData = true;
                }
            }
        }
        assertTrue(sawNoData);
    }

    @Test
    void rejectsMissingValueMeaningAndTampering(@TempDir Path root) throws Exception {
        assertThrows(NullPointerException.class, () -> ExtractedHeightGuidePromotionOptionsV2.of(
                1, null, 1_000_000L, 0L));
        Path target = root.resolve("promoted");
        service.promote(
                target,
                sampleDraft(),
                ExtractedHeightGuidePromotionOptionsV2.of(
                        1,
                        GenerationRequestV2.HeightValueMeaning.ABSOLUTE_BLOCK_Y,
                        1_000_000L,
                        0L),
                new GenerationRequestV2.Bounds(2, 2, 0, 400, 50),
                () -> false);
        Files.writeString(target.resolve("extra.bin"), "nope");
        assertThrows(Exception.class, () -> codec.readAndVerify(
                target.resolve(ExtractedHeightGuidePromotionRecordCodecV2.INDEX_FILE_NAME),
                target,
                () -> false));
    }

    @Test
    void cancelCleansUpBeforeAtomicCommit(@TempDir Path root) {
        AtomicInteger checks = new AtomicInteger();
        Path target = root.resolve("cancelled");
        assertThrows(CancellationException.class, () -> service.promote(
                target,
                sampleDraft(),
                ExtractedHeightGuidePromotionOptionsV2.of(
                        1,
                        GenerationRequestV2.HeightValueMeaning.BLOCKS_RELATIVE_TO_WATER_LEVEL,
                        1_000_000L,
                        0L),
                new GenerationRequestV2.Bounds(2, 2, 0, 400, 50),
                () -> checks.incrementAndGet() > 2));
        assertFalse(Files.exists(target));
    }

    @Test
    void promotionIsDeterministicAcrossLocaleTimezoneAndThreads(@TempDir Path root) throws Exception {
        ExtractedHeightGuideDraftV2 draft = sampleDraft();
        ExtractedHeightGuidePromotionOptionsV2 options = ExtractedHeightGuidePromotionOptionsV2.of(
                1,
                GenerationRequestV2.HeightValueMeaning.BLOCKS_ABOVE_REQUEST_MIN_Y,
                1_000_000L,
                0L);
        GenerationRequestV2.Bounds bounds = new GenerationRequestV2.Bounds(2, 2, 0, 400, 50);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            ExtractedHeightGuidePromotionRecordV2 expected = service.promote(
                    root.resolve("a"), draft, options, bounds, () -> false);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            ExtractedHeightGuidePromotionRecordV2 second = service.promote(
                    root.resolve("b"), draft, options, bounds, () -> false);
            assertEquals(expected.canonicalChecksum(), second.canonicalChecksum());
            assertEquals(expected.mapSha256(), second.mapSha256());
            try (var pool = Executors.newFixedThreadPool(4)) {
                assertEquals(expected.canonicalChecksum(), pool.submit(() -> service.promote(
                        root.resolve("c"), draft, options, bounds, () -> false)).get().canonicalChecksum());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    private DecodedNumericRaster decodePublished(
            Path target,
            ExtractedHeightGuidePromotionRecordV2 record
    ) {
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
                        NumericPngEncoding.NumericKind.HEIGHT,
                        NumericPngEncoding.SampleType.U8),
                ConstraintMapDecodeLimits.defaults(),
                () -> false);
    }

    private static ExtractedHeightGuideDraftV2 sampleDraft() {
        int[] pixels = {
                argb(255, 0, 0, 0),
                argb(255, 255, 255, 255),
                argb(64, 200, 200, 200),
                argb(200, 10, 20, 30),
        };
        return ImageHeightGuideExtractorV2.extract(
                2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

}

