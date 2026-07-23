package com.github.nankotsu029.landformcraft.model.v2.composition;

/**
 * ADR 0038 D3 composition stage: which stages of the fixed composition order a compiled feature
 * plan contributes to. Execution order is foundation → surface → volume → fluid; inside the volume
 * stage the existing {@code ADD_SOLID → CARVE_SOLID → ADD_FLUID} order (AGENTS.md §8) is unchanged.
 */
public enum CompositionStageV2 {
    /** Determines land-water medium and base elevation (ADR 0038 D1 foundation candidate). */
    FOUNDATION,
    /** 2.5D deformation, deposition, incision, or transition over an existing foundation. */
    SURFACE_MODIFICATION,
    /** Bounded sparse-AABB {@code ADD_SOLID} / {@code CARVE_SOLID} contribution. */
    VOLUME_OPERATION,
    /** Checksum-bound, bounded {@code ADD_FLUID} contribution. */
    FLUID_OPERATION
}
