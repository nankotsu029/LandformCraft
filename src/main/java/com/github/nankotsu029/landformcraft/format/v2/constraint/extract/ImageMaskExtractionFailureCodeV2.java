package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

/** Stable machine-readable reasons for rejecting an extraction request before allocation. */
public enum ImageMaskExtractionFailureCodeV2 {
    INVALID_DIMENSIONS,
    ASPECT_RATIO_EXCEEDED,
    PIXELS_EXCEEDED,
    PIXEL_BUFFER_MISMATCH,
    INVALID_SOURCE_CHECKSUM,
    WORKING_BUDGET_EXCEEDED
}
