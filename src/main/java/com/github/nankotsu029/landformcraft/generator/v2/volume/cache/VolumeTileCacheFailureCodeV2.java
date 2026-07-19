package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

/**
 * Failure codes for V2-5-04 volume tile cache admission, fill, and lifecycle.
 */
public enum VolumeTileCacheFailureCodeV2 {
    UNKNOWN_KERNEL,
    BINDING_MISMATCH,
    BUDGET_EXCEEDED,
    OVERSIZED_CHUNK,
    DENSE_ALLOCATION_REJECTED,
    CANCELLED,
    ARITHMETIC_OVERFLOW,
    INVALID_INTERVAL
}
