package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageLandWaterExtractorV2Test {
    private static final ImageMaskExtractionLimitsV2 DEFAULTS = ImageMaskExtractionLimitsV2.defaults();
    private static final String SOURCE_CHECKSUM = "ab".repeat(32);

    @Test
    void classifiesBlueDominantWaterLandBorderlineAndTransparentCells() {
        int[] pixels = {
                argb(255, 0, 0, 255),     // pure blue: dominance 510 -> WATER, confidence 255
                argb(255, 0, 128, 160),   // teal: dominance 192 -> WATER
                argb(255, 194, 178, 128), // sand: dominance -116 -> LAND
                argb(255, 0, 255, 0),     // grass green: dominance -255 -> LAND, confidence 255
                argb(255, 128, 128, 128), // neutral gray: dominance 0 -> LAND, confidence 1
                argb(64, 0, 0, 255),      // transparent blue -> UNKNOWN, confidence 0
                argb(255, 100, 100, 116), // dominance 32 -> WATER boundary
                argb(255, 100, 100, 115), // dominance 30 -> UNKNOWN band
        };
        ExtractedMaskDraftV2 draft = ImageLandWaterExtractorV2.extract(
                4, 2, pixels, SOURCE_CHECKSUM, DEFAULTS, () -> false);

        assertEquals(ImageLandWaterExtractorV2.ALGORITHM_VERSION, draft.algorithmVersion());
        assertEquals(ExtractedMaskDraftV2.CLASS_WATER, draft.classAt(0, 0));
        assertEquals(255, draft.confidenceAt(0, 0));
        assertEquals(ExtractedMaskDraftV2.CLASS_WATER, draft.classAt(1, 0));
        assertEquals(192, draft.confidenceAt(1, 0));
        assertEquals(ExtractedMaskDraftV2.CLASS_LAND, draft.classAt(2, 0));
        assertEquals(117, draft.confidenceAt(2, 0));
        assertEquals(ExtractedMaskDraftV2.CLASS_LAND, draft.classAt(3, 0));
        assertEquals(255, draft.confidenceAt(3, 0));
        assertEquals(ExtractedMaskDraftV2.CLASS_LAND, draft.classAt(0, 1));
        assertEquals(1, draft.confidenceAt(0, 1));
        assertEquals(ExtractedMaskDraftV2.CLASS_UNKNOWN, draft.classAt(1, 1));
        assertEquals(0, draft.confidenceAt(1, 1));
        assertEquals(ExtractedMaskDraftV2.CLASS_WATER, draft.classAt(2, 1));
        assertEquals(32, draft.confidenceAt(2, 1));
        assertEquals(ExtractedMaskDraftV2.CLASS_UNKNOWN, draft.classAt(3, 1));
        assertEquals(0, draft.confidenceAt(3, 1));

        assertEquals(3, draft.waterCells());
        assertEquals(3, draft.landCells());
        assertEquals(2, draft.unknownCells());
        assertEquals(SOURCE_CHECKSUM, draft.sourceChecksum());
    }

    @Test
    void semanticChecksumIsStableAcrossRepeatsThreadsLocaleAndTimezone() throws Exception {
        int[] pixels = {
                argb(255, 10, 20, 200), argb(255, 120, 90, 40),
                argb(255, 0, 160, 60), argb(200, 30, 60, 220),
        };
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            String expected = ImageLandWaterExtractorV2.extract(
                    2, 2, pixels, SOURCE_CHECKSUM, DEFAULTS, () -> false).semanticChecksum();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(expected, ImageLandWaterExtractorV2.extract(
                    2, 2, pixels, SOURCE_CHECKSUM, DEFAULTS, () -> false).semanticChecksum());
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(expected, one.submit(() -> ImageLandWaterExtractorV2.extract(
                        2, 2, pixels, SOURCE_CHECKSUM, DEFAULTS, () -> false)).get().semanticChecksum());
                assertEquals(expected, four.submit(() -> ImageLandWaterExtractorV2.extract(
                        2, 2, pixels, SOURCE_CHECKSUM, DEFAULTS, () -> false)).get().semanticChecksum());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void rejectsInvalidDimensionsBuffersChecksumsAndBudgets() {
        int[] one = {argb(255, 0, 0, 255)};

        assertCode(ImageMaskExtractionFailureCodeV2.INVALID_DIMENSIONS,
                () -> ImageLandWaterExtractorV2.extract(0, 1, one, SOURCE_CHECKSUM, DEFAULTS, () -> false));
        assertCode(ImageMaskExtractionFailureCodeV2.INVALID_DIMENSIONS,
                () -> ImageLandWaterExtractorV2.extract(4_097, 1, one, SOURCE_CHECKSUM, DEFAULTS, () -> false));
        assertCode(ImageMaskExtractionFailureCodeV2.ASPECT_RATIO_EXCEEDED,
                () -> ImageLandWaterExtractorV2.extract(10, 1, new int[10], SOURCE_CHECKSUM,
                        new ImageMaskExtractionLimitsV2(4_096, 2, 16_000_000L, Long.MAX_VALUE),
                        () -> false));
        assertCode(ImageMaskExtractionFailureCodeV2.PIXELS_EXCEEDED,
                () -> ImageLandWaterExtractorV2.extract(3, 2, new int[6], SOURCE_CHECKSUM,
                        new ImageMaskExtractionLimitsV2(4_096, 32, 4L, Long.MAX_VALUE),
                        () -> false));
        assertCode(ImageMaskExtractionFailureCodeV2.WORKING_BUDGET_EXCEEDED,
                () -> ImageLandWaterExtractorV2.extract(3, 2, new int[6], SOURCE_CHECKSUM,
                        new ImageMaskExtractionLimitsV2(4_096, 32, 16_000_000L, 8L),
                        () -> false));
        assertCode(ImageMaskExtractionFailureCodeV2.PIXEL_BUFFER_MISMATCH,
                () -> ImageLandWaterExtractorV2.extract(2, 2, one, SOURCE_CHECKSUM, DEFAULTS, () -> false));
        assertCode(ImageMaskExtractionFailureCodeV2.INVALID_SOURCE_CHECKSUM,
                () -> ImageLandWaterExtractorV2.extract(1, 1, one, "UPPERCASE", DEFAULTS, () -> false));

        assertThrows(CancellationException.class,
                () -> ImageLandWaterExtractorV2.extract(1, 1, one, SOURCE_CHECKSUM, DEFAULTS, () -> true));
        assertThrows(IndexOutOfBoundsException.class,
                () -> ImageLandWaterExtractorV2.extract(1, 1, one, SOURCE_CHECKSUM, DEFAULTS, () -> false)
                        .classAt(1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ImageMaskExtractionLimitsV2(0, 32, 16_000_000L, 8L));
    }

    private static void assertCode(ImageMaskExtractionFailureCodeV2 expected, Runnable runnable) {
        ImageMaskExtractionExceptionV2 exception =
                assertThrows(ImageMaskExtractionExceptionV2.class, runnable::run);
        assertEquals(expected, exception.failureCode());
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
