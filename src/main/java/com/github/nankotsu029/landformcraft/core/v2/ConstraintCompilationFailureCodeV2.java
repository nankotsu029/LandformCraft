package com.github.nankotsu029.landformcraft.core.v2;

/** Stable failures raised while numeric maps are bound to TerrainIntent v2 semantics. */
public enum ConstraintCompilationFailureCodeV2 {
    INVALID_BINDING,
    UNKNOWN_LABEL,
    INVALID_NO_DATA,
    SAMPLE_OUT_OF_RANGE,
    HARD_CONSTRAINT_CONFLICT,
    CHECKSUM_MISMATCH,
    BUDGET_EXCEEDED,
    ASPECT_MISMATCH,
    DIMENSIONS_MISMATCH
}
