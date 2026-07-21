package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuideDraftV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;

import java.util.Objects;

/**
 * Explicit height-guide promotion controls. {@link GenerationRequestV2.HeightValueMeaning} is
 * mandatory — luminance drafts never invent absolute/relative/water-relative semantics.
 */
public record ExtractedHeightGuidePromotionOptionsV2(
        int confidenceThreshold,
        GenerationRequestV2.HeightValueMeaning valueMeaning,
        long valueScaleMillionths,
        long valueOffsetMillionths,
        int noDataSample
) {
    public ExtractedHeightGuidePromotionOptionsV2 {
        if (confidenceThreshold < 0 || confidenceThreshold > 255) {
            throw new IllegalArgumentException("confidenceThreshold must be within 0..255");
        }
        Objects.requireNonNull(valueMeaning, "valueMeaning");
        if (valueScaleMillionths == 0L) {
            throw new IllegalArgumentException("valueScaleMillionths must not be zero");
        }
        if (noDataSample != ExtractedHeightGuideDraftV2.NO_DATA_SENTINEL_SAMPLE) {
            throw new IllegalArgumentException(
                    "noDataSample must be " + ExtractedHeightGuideDraftV2.NO_DATA_SENTINEL_SAMPLE
                            + " for U8 luminance height promotion");
        }
    }

    public static ExtractedHeightGuidePromotionOptionsV2 of(
            int confidenceThreshold,
            GenerationRequestV2.HeightValueMeaning valueMeaning,
            long valueScaleMillionths,
            long valueOffsetMillionths
    ) {
        return new ExtractedHeightGuidePromotionOptionsV2(
                confidenceThreshold,
                valueMeaning,
                valueScaleMillionths,
                valueOffsetMillionths,
                ExtractedHeightGuideDraftV2.NO_DATA_SENTINEL_SAMPLE);
    }
}
