package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageZoneLabelExtractorV2Test {
    private static final String SOURCE = "ab".repeat(32);

    @Test
    void goldenPaletteMatchesAmbiguousUnknownAndAlpha() {
        int[] pixels = {
                argb(255, 210, 180, 120), // shore exact
                argb(255, 70, 140, 60),   // upland exact
                argb(64, 210, 180, 120),  // transparent → UNKNOWN
                argb(255, 165, 150, 122), // shore/rock midpoint → ambiguous UNKNOWN
        };
        ExtractedZoneLabelDraftV2 draft = ImageZoneLabelExtractorV2.extract(
                2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false);

        assertEquals(ImageZoneLabelExtractorV2.ALGORITHM_VERSION, draft.algorithmVersion());
        assertEquals(ExtractedZoneLabelDraftV2.SAMPLE_SPACE_DECLARATION, draft.sampleSpaceDeclaration());
        assertEquals(4, draft.proposedLabels().size());
        assertEquals(0, draft.labelIndexAt(0, 0));
        assertEquals(10, draft.sampleAt(0, 0));
        assertEquals(255, draft.confidenceAt(0, 0));
        assertEquals(1, draft.labelIndexAt(1, 0));
        assertEquals(20, draft.sampleAt(1, 0));
        assertTrue(draft.isUnknown(0, 1));
        assertEquals(0, draft.confidenceAt(0, 1));
        assertTrue(draft.isUnknown(1, 1));
        assertEquals(0, draft.confidenceAt(1, 1));
        assertEquals(2, draft.labeledCells());
        assertEquals(2, draft.unknownCells());
    }

    @Test
    void rejectsFarColorsAsUnknownWithoutInventingLabels() {
        ExtractedZoneLabelDraftV2 draft = ImageZoneLabelExtractorV2.extract(
                1, 1, new int[]{argb(255, 255, 0, 255)}, SOURCE,
                ImageMaskExtractionLimitsV2.defaults(), () -> false);
        assertTrue(draft.isUnknown(0, 0));
        assertTrue(draft.sampleSpaceDeclaration().contains("explicit-categorical-encoding"));
        assertFalse(draft.sampleSpaceDeclaration().contains("k-means"));
    }

    @Test
    void extractionIsDeterministicAcrossLocaleTimezoneAndThreads() throws Exception {
        int[] pixels = {
                argb(255, 210, 180, 120), argb(255, 40, 90, 70),
                argb(200, 120, 120, 125), argb(50, 70, 140, 60),
        };
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            String expected = ImageZoneLabelExtractorV2.extract(
                    2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false)
                    .semanticChecksum();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(expected, ImageZoneLabelExtractorV2.extract(
                    2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false)
                    .semanticChecksum());
            try (var one = Executors.newSingleThreadExecutor();
                 var four = Executors.newFixedThreadPool(4)) {
                assertEquals(expected, one.submit(() -> ImageZoneLabelExtractorV2.extract(
                        2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false)
                        .semanticChecksum()).get());
                assertEquals(expected, four.submit(() -> ImageZoneLabelExtractorV2.extract(
                        2, 2, pixels, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false)
                        .semanticChecksum()).get());
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void rejectsBudgetPaletteAndDescriptorFailures() {
        int[] one = {argb(255, 1, 2, 3)};
        assertThrows(ImageMaskExtractionExceptionV2.class, () -> ImageZoneLabelExtractorV2.extract(
                0, 1, one, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertThrows(ImageMaskExtractionExceptionV2.class, () -> ImageZoneLabelExtractorV2.extract(
                4_097, 1, one, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertThrows(ImageMaskExtractionExceptionV2.class, () -> ImageZoneLabelExtractorV2.extract(
                2, 2, one, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertThrows(ImageMaskExtractionExceptionV2.class, () -> ImageZoneLabelExtractorV2.extract(
                1, 1, one, "NOT-HEX", ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertThrows(CancellationException.class, () -> ImageZoneLabelExtractorV2.extract(
                1, 1, one, SOURCE, ImageMaskExtractionLimitsV2.defaults(), () -> true));
        assertThrows(ImageMaskExtractionExceptionV2.class, () -> ImageZoneLabelExtractorV2.extract(
                33, 1, new int[33], SOURCE,
                new ImageMaskExtractionLimitsV2(64, 32, 1_000, 64L * 1024 * 1024), () -> false));

        ImageMaskExtractionExceptionV2 emptyPalette = assertThrows(
                ImageMaskExtractionExceptionV2.class,
                () -> ImageZoneLabelExtractorV2.extract(
                        1, 1, one, SOURCE, List.of(),
                        ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertEquals(ImageMaskExtractionFailureCodeV2.LABEL_BUDGET_EXCEEDED, emptyPalette.failureCode());

        List<ZonePaletteEntryV2> oversized = new ArrayList<>();
        for (int i = 1; i <= ImageZoneLabelExtractorV2.MAXIMUM_PALETTE_LABELS + 1; i++) {
            oversized.add(new ZonePaletteEntryV2(i, "label-" + i, i % 256, 0, 0));
        }
        ImageMaskExtractionExceptionV2 tooMany = assertThrows(
                ImageMaskExtractionExceptionV2.class,
                () -> ImageZoneLabelExtractorV2.extract(
                        1, 1, one, SOURCE, oversized,
                        ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertEquals(ImageMaskExtractionFailureCodeV2.LABEL_BUDGET_EXCEEDED, tooMany.failureCode());

        ImageMaskExtractionExceptionV2 duplicate = assertThrows(
                ImageMaskExtractionExceptionV2.class,
                () -> ImageZoneLabelExtractorV2.extract(
                        1, 1, one, SOURCE,
                        List.of(
                                new ZonePaletteEntryV2(10, "shore", 1, 2, 3),
                                new ZonePaletteEntryV2(10, "rock", 4, 5, 6)),
                        ImageMaskExtractionLimitsV2.defaults(), () -> false));
        assertEquals(ImageMaskExtractionFailureCodeV2.INVALID_PALETTE, duplicate.failureCode());
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
