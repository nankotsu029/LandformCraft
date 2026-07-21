package com.github.nankotsu029.landformcraft.core.v2;

/**
 * Explicit zone-label promotion controls. UNKNOWN cells and cells below the confidence threshold
 * become no-data; remaining labeled cells keep their proposed categorical samples.
 */
public record ExtractedZoneLabelPromotionOptionsV2(
        int confidenceThreshold,
        int noDataSample
) {
    public static final int DEFAULT_NODATA_SAMPLE = 0;

    public ExtractedZoneLabelPromotionOptionsV2 {
        if (confidenceThreshold < 0 || confidenceThreshold > 255) {
            throw new IllegalArgumentException("confidenceThreshold must be within 0..255");
        }
        if (noDataSample < 0 || noDataSample > 255) {
            throw new IllegalArgumentException("noDataSample must be within 0..255");
        }
    }

    public static ExtractedZoneLabelPromotionOptionsV2 rejectBelow(int confidenceThreshold) {
        return new ExtractedZoneLabelPromotionOptionsV2(
                confidenceThreshold, DEFAULT_NODATA_SAMPLE);
    }
}
