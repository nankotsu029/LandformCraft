package com.github.nankotsu029.landformcraft.format.v2.constraint;

/** Stable machine-readable failures for untrusted constraint-map sources and numeric PNG decoding. */
public enum ConstraintMapFailureCode {
    INVALID_DESCRIPTOR,
    MISSING_FILE,
    UNSAFE_PATH,
    HARD_LINK_ALIAS,
    SOURCE_CHANGED,
    UNSUPPORTED_FORMAT,
    INVALID_MAGIC,
    FILE_TOO_LARGE,
    TOTAL_BYTES_EXCEEDED,
    CHECKSUM_MISMATCH,
    MALFORMED_IMAGE,
    MULTI_FRAME,
    DIMENSIONS_MISMATCH,
    DIMENSIONS_EXCEEDED,
    PIXELS_EXCEEDED,
    SAMPLE_TYPE_MISMATCH,
    DECODE_BUDGET_EXCEEDED,
    FUTURE_VERSION
}
