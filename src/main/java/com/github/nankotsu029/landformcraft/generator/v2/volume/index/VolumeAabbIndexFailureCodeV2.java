package com.github.nankotsu029.landformcraft.generator.v2.volume.index;

/** Stable failure codes for V2-5-03 AABB index build/query. */
public enum VolumeAabbIndexFailureCodeV2 {
    UNKNOWN_KERNEL,
    BINDING_MISMATCH,
    INVALID_AABB,
    ARITHMETIC_OVERFLOW,
    BUDGET_EXCEEDED,
    CHECKSUM_MISMATCH
}
