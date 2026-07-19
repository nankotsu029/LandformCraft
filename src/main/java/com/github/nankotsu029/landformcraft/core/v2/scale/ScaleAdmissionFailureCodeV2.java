package com.github.nankotsu029.landformcraft.core.v2.scale;

/** Stable machine-readable reasons for rejecting a generation area before allocation. */
public enum ScaleAdmissionFailureCodeV2 {
    DIMENSIONS_OUT_OF_RANGE,
    SCALE_CLASS_EXCEEDED,
    TILE_BUDGET_EXCEEDED,
    WORKING_BUDGET_EXCEEDED,
    RETAINED_BUDGET_EXCEEDED,
    ARTIFACT_BUDGET_EXCEEDED
}
