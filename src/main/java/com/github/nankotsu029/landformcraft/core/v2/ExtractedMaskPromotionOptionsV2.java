package com.github.nankotsu029.landformcraft.core.v2;

import java.util.Objects;

/**
 * Mandatory explicit promotion controls. Confidence below {@code confidenceThreshold} is treated as
 * UNKNOWN before {@link ExtractedMaskPromotionUnknownHandlingV2} is applied.
 */
public record ExtractedMaskPromotionOptionsV2(
        int confidenceThreshold,
        ExtractedMaskPromotionUnknownHandlingV2 unknownHandling,
        Integer noDataSample
) {
    public static final int WATER_SAMPLE = 0;
    public static final int LAND_SAMPLE = 1;
    public static final int DEFAULT_NODATA_SAMPLE = 255;

    public ExtractedMaskPromotionOptionsV2 {
        if (confidenceThreshold < 0 || confidenceThreshold > 255) {
            throw new IllegalArgumentException("confidenceThreshold must be within 0..255");
        }
        Objects.requireNonNull(unknownHandling, "unknownHandling");
        if (unknownHandling == ExtractedMaskPromotionUnknownHandlingV2.MAP_TO_NODATA) {
            if (noDataSample == null) {
                throw new IllegalArgumentException("noDataSample is required for MAP_TO_NODATA");
            }
            if (noDataSample < 0 || noDataSample > 255
                    || noDataSample == WATER_SAMPLE
                    || noDataSample == LAND_SAMPLE) {
                throw new IllegalArgumentException(
                        "noDataSample must be a U8 value distinct from water(0) and land(1)");
            }
        } else if (noDataSample != null) {
            throw new IllegalArgumentException("noDataSample is only valid for MAP_TO_NODATA");
        }
    }

    public static ExtractedMaskPromotionOptionsV2 rejectBelow(int confidenceThreshold) {
        return new ExtractedMaskPromotionOptionsV2(
                confidenceThreshold, ExtractedMaskPromotionUnknownHandlingV2.REJECT, null);
    }

    public static ExtractedMaskPromotionOptionsV2 mapUnknownToWater(int confidenceThreshold) {
        return new ExtractedMaskPromotionOptionsV2(
                confidenceThreshold, ExtractedMaskPromotionUnknownHandlingV2.MAP_TO_WATER, null);
    }

    public static ExtractedMaskPromotionOptionsV2 mapUnknownToLand(int confidenceThreshold) {
        return new ExtractedMaskPromotionOptionsV2(
                confidenceThreshold, ExtractedMaskPromotionUnknownHandlingV2.MAP_TO_LAND, null);
    }

    public static ExtractedMaskPromotionOptionsV2 mapUnknownToNoData(
            int confidenceThreshold,
            int noDataSample
    ) {
        return new ExtractedMaskPromotionOptionsV2(
                confidenceThreshold,
                ExtractedMaskPromotionUnknownHandlingV2.MAP_TO_NODATA,
                noDataSample);
    }
}
