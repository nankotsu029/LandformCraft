package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapSourceSpec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.DecodedNumericRaster;
import com.github.nankotsu029.landformcraft.format.v2.constraint.LoadedConstraintMapSource;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngDecoder;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoding;
import com.github.nankotsu029.landformcraft.format.v2.constraint.SecureConstraintMapSourceLoader;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelPromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageMaskExtractionLimitsV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageZoneLabelExtractorV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedZoneLabelPromotionRecordV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
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

class ExtractedZoneLabelPromotionServiceV2Test {
    private static final String SOURCE = "ab".repeat(32);

    private final ExtractedZoneLabelPromotionServiceV2 service =
            new ExtractedZoneLabelPromotionServiceV2();
    private final ExtractedZoneLabelPromotionRecordCodecV2 codec =
            new ExtractedZoneLabelPromotionRecordCodecV2();
    private final SecureConstraintMapSourceLoader loader = new SecureConstraintMapSourceLoader();
    private final NumericPngDecoder decoder = new NumericPngDecoder();

    @Test
    void promotesDraftThroughStrictDecoderAndZoneCanonicalPath(@TempDir Path root) throws Exception {
        ExtractedZoneLabelDraftV2 draft = sampleDraft();
        GenerationRequestV2.Bounds bounds = new GenerationRequestV2.Bounds(2, 2, 0, 400, 50);
        Path target = root.resolve("promoted");
        ExtractedZoneLabelPromotionRecordV2 record = service.promote(
                target,
                draft,
                ExtractedZoneLabelPromotionOptionsV2.rejectBelow(1),
                bounds,
                () -> false);

        assertEquals(draft.sourceChecksum(), record.sourceChecksum());
        assertEquals(draft.semanticChecksum(), record.draftSemanticChecksum());
        assertEquals(ExtractedZoneLabelPromotionRecordV2.ROLE, record.role());
        assertEquals(ExtractedZoneLabelDraftV2.SAMPLE_SPACE_DECLARATION, record.sampleSpaceDeclaration());
        assertEquals(2, record.labeledCells());
        assertEquals(2, record.noDataCells());

        ExtractedZoneLabelPromotionRecordV2 verified = codec.readAndVerify(
                target.resolve(ExtractedZoneLabelPromotionRecordCodecV2.INDEX_FILE_NAME),
                target,
                () -> false);
        assertEquals(record, verified);

        DecodedNumericRaster raster = decodePublished(target, record);
        assertEquals(record.mapSha256(), raster.sourceChecksum());
        assertEquals(10, raster.sample(0, 0));
        assertEquals(20, raster.sample(1, 0));
        assertEquals(0, raster.sample(0, 1));
        assertEquals(0, raster.sample(1, 1));
    }

    @Test
    void thresholdSuppressesLowConfidenceToNoData(@TempDir Path root) throws Exception {
        ExtractedZoneLabelDraftV2 draft = ImageZoneLabelExtractorV2.extract(
                1, 1,
                new int[]{argb(255, 200, 170, 110)}, // near shore but not exact
                SOURCE,
                ImageMaskExtractionLimitsV2.defaults(),
                () -> false);
        assertFalse(draft.isUnknown(0, 0));
        int confidence = draft.confidenceAt(0, 0);
        assertTrue(confidence < 255);

        ExtractedZoneLabelPromotionRecordV2 record = service.promote(
                root.resolve("threshold"),
                draft,
                ExtractedZoneLabelPromotionOptionsV2.rejectBelow(confidence + 1),
                new GenerationRequestV2.Bounds(1, 1, 0, 400, 50),
                () -> false);
        assertEquals(1, record.thresholdSuppressedCells());
        assertEquals(1, record.noDataCells());
        assertEquals(0, record.labeledCells());
        DecodedNumericRaster raster = decodePublished(root.resolve("threshold"), record);
        assertEquals(0, raster.sample(0, 0));
    }

    @Test
    void rejectsNoDataCollisionAndTampering(@TempDir Path root) throws Exception {
        assertThrows(ExtractedZoneLabelPromotionExceptionV2.class, () -> service.promote(
                root.resolve("collision"),
                sampleDraft(),
                new ExtractedZoneLabelPromotionOptionsV2(1, 10),
                new GenerationRequestV2.Bounds(2, 2, 0, 400, 50),
                () -> false));

        Path target = root.resolve("promoted");
        service.promote(
                target,
                sampleDraft(),
                ExtractedZoneLabelPromotionOptionsV2.rejectBelow(1),
                new GenerationRequestV2.Bounds(2, 2, 0, 400, 50),
                () -> false);
        Files.writeString(target.resolve("extra.bin"), "nope");
        assertThrows(Exception.class, () -> codec.readAndVerify(
                target.resolve(ExtractedZoneLabelPromotionRecordCodecV2.INDEX_FILE_NAME),
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
                ExtractedZoneLabelPromotionOptionsV2.rejectBelow(1),
                new GenerationRequestV2.Bounds(2, 2, 0, 400, 50),
                () -> checks.incrementAndGet() > 2));
        assertFalse(Files.exists(target));
    }

    @Test
    void promotionIsDeterministicAcrossLocaleTimezoneAndThreads(@TempDir Path root) throws Exception {
        ExtractedZoneLabelDraftV2 draft = sampleDraft();
        ExtractedZoneLabelPromotionOptionsV2 options =
                ExtractedZoneLabelPromotionOptionsV2.rejectBelow(1);
        GenerationRequestV2.Bounds bounds = new GenerationRequestV2.Bounds(2, 2, 0, 400, 50);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            ExtractedZoneLabelPromotionRecordV2 expected = service.promote(
                    root.resolve("a"), draft, options, bounds, () -> false);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            ExtractedZoneLabelPromotionRecordV2 second = service.promote(
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
            ExtractedZoneLabelPromotionRecordV2 record
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
                        NumericPngEncoding.NumericKind.CATEGORICAL,
                        NumericPngEncoding.SampleType.U8),
                ConstraintMapDecodeLimits.defaults(),
                () -> false);
    }

    private static ExtractedZoneLabelDraftV2 sampleDraft() {
        int[] pixels = {
                argb(255, 210, 180, 120),
                argb(255, 70, 140, 60),
                argb(64, 210, 180, 120),
                argb(255, 165, 150, 122),
        };
        return ImageZoneLabelExtractorV2.extract(
                2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
