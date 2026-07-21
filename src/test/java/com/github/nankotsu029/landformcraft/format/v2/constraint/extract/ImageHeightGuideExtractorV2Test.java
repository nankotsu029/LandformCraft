package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageHeightGuideExtractorV2Test {
    private static final String SOURCE = "ab".repeat(32);

    @Test
    void goldenLuminanceConversionNoDataAndClamp() {
        int[] pixels = {
                argb(255, 0, 0, 0),           // luma 0
                argb(255, 255, 255, 255),     // luma 255 → clamp 254
                argb(64, 200, 200, 200),      // alpha no-data
                argb(200, 10, 20, 30),        // mid gray-ish
        };
        ExtractedHeightGuideDraftV2 draft = ImageHeightGuideExtractorV2.extract(
                2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false);

        assertEquals(ImageHeightGuideExtractorV2.ALGORITHM_VERSION, draft.algorithmVersion());
        assertEquals(ExtractedHeightGuideDraftV2.SAMPLE_SPACE_DECLARATION, draft.sampleSpaceDeclaration());
        assertEquals(0, draft.sampleAt(0, 0));
        assertEquals(255, draft.confidenceAt(0, 0));
        assertEquals(254, draft.sampleAt(1, 0));
        assertTrue(draft.isNoData(0, 1));
        assertEquals(0, draft.confidenceAt(0, 1));
        int expectedLuma = ImageHeightGuideExtractorV2.luminance(10, 20, 30);
        assertEquals(Math.min(254, expectedLuma), draft.sampleAt(1, 1));
        assertEquals(200, draft.confidenceAt(1, 1));
        assertEquals(3, draft.validCells());
        assertEquals(1, draft.noDataCells());
    }

    @Test
    void extractionIsDeterministicAcrossLocaleTimezoneAndThreads() throws Exception {
        int[] pixels = {
                argb(255, 40, 80, 120), argb(255, 200, 100, 50),
                argb(180, 0, 255, 0), argb(50, 255, 255, 255),
        };
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            String expected = ImageHeightGuideExtractorV2.extract(
                    2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false)
                    .semanticChecksum();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(expected, ImageHeightGuideExtractorV2.extract(
                    2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false)
                    .semanticChecksum());
            try (var one = Executors.newSingleThreadExecutor();
                 var four = Executors.newFixedThreadPool(4)) {
                assertEquals(expected, one.submit(() -> ImageHeightGuideExtractorV2.extract(
                        2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false)
                        .semanticChecksum()).get());
                assertEquals(expected, four.submit(() -> ImageHeightGuideExtractorV2.extract(
                        2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false)
                        .semanticChecksum()).get());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void rejectsBudgetAndDescriptorFailures() {
        int[] one = {argb(255, 1, 2, 3)};
        assertThrows(ImageMaskExtractionExceptionV2.class, () -> ImageHeightGuideExtractorV2.extract(
                0, 1, one, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertThrows(ImageMaskExtractionExceptionV2.class, () -> ImageHeightGuideExtractorV2.extract(
                4_097, 1, one, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertThrows(ImageMaskExtractionExceptionV2.class, () -> ImageHeightGuideExtractorV2.extract(
                2, 2, one, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertThrows(ImageMaskExtractionExceptionV2.class, () -> ImageHeightGuideExtractorV2.extract(
                1, 1, one, "NOT-HEX", ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertThrows(CancellationException.class, () -> ImageHeightGuideExtractorV2.extract(
                1, 1, one, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> true));
        assertThrows(ImageMaskExtractionExceptionV2.class, () -> ImageHeightGuideExtractorV2.extract(
                33, 1, new int[33], SOURCE,
                new ImageMaskExtractionLimitsV2(64, 32, 1_000, 64L * 1024 * 1024), () -> false));
    }

    @Test
    void doesNotInventHeightValueMeaning() {
        ExtractedHeightGuideDraftV2 draft = ImageHeightGuideExtractorV2.extract(
                1, 1, new int[]{argb(255, 10, 10, 10)}, SOURCE,
                ImageMaskExtractionLimitsV2.defaults(), () -> false);
        assertTrue(draft.sampleSpaceDeclaration().contains("explicit-height-value-meaning"));
        assertFalse(draft.sampleSpaceDeclaration().contains("ABSOLUTE_BLOCK_Y"));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
