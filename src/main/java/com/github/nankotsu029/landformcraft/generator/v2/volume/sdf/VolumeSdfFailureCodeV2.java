package com.github.nankotsu029.landformcraft.generator.v2.volume.sdf;

/** Stable failure codes for V2-5-01 fixed-point SDF evaluation. */
public enum VolumeSdfFailureCodeV2 {
    UNKNOWN_KERNEL,
    ZERO_RADIUS,
    ARITHMETIC_OVERFLOW,
    DEGENERATE_GEOMETRY,
    BUDGET_EXCEEDED,
    UNSUPPORTED_PRIMITIVE
}
