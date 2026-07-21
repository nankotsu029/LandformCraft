package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

/** Stable machine-readable reasons for rejecting an untrusted extraction image before draft use. */
public enum ImageExtractionInputFailureCodeV2 {
    MISSING_FILE,
    UNSAFE_PATH,
    HARD_LINK_ALIAS,
    SOURCE_CHANGED,
    UNSUPPORTED_FORMAT,
    INVALID_MAGIC,
    FILE_TOO_LARGE,
    TOTAL_BYTES_EXCEEDED,
    CORRUPT_IMAGE,
    DIMENSIONS_EXCEEDED,
    ASPECT_RATIO_EXCEEDED,
    PIXELS_EXCEEDED,
    MULTI_FRAME,
    DECODE_BUDGET_EXCEEDED,
    WORKING_BUDGET_EXCEEDED,
    INVALID_PATH_DESCRIPTOR
}
