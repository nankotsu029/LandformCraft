package com.github.nankotsu029.landformcraft.generator.v2.volume.query;

/**
 * Failure codes for V2-5-05 volume-aware TerrainQuery composition.
 */
public enum VolumeTerrainQueryFailureCodeV2 {
    UNKNOWN_KERNEL,
    BINDING_MISMATCH,
    BUDGET_EXCEEDED,
    Y_OVERFLOW,
    INVALID_INTERVAL,
    OWNER_CONFLICT,
    OUT_OF_BOUNDS
}
