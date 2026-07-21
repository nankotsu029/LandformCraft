package com.github.nankotsu029.landformcraft.core.v2;

/**
 * Explicit policy for draft UNKNOWN cells (and threshold-suppressed cells) during promotion.
 * Omission is forbidden; callers must choose one of these values.
 */
public enum ExtractedMaskPromotionUnknownHandlingV2 {
    /** Fail closed if any unresolved UNKNOWN remains after the confidence threshold. */
    REJECT,
    /** Map unresolved UNKNOWN to the water sample (0). */
    MAP_TO_WATER,
    /** Map unresolved UNKNOWN to the land sample (1). */
    MAP_TO_LAND,
    /** Map unresolved UNKNOWN to an explicit no-data sentinel sample. */
    MAP_TO_NODATA
}
