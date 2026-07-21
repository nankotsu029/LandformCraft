package com.github.nankotsu029.landformcraft.model.v2.scale;

/**
 * Unified horizontal dimension policy for v2 grids (V2-8-02). Every formerly scattered
 * {@code 1..1_000} grid check routes through this class so the route ceiling has exactly one
 * owner: the {@link ScaleClassV2#MEDIUM} contract. LARGE dimensions stay rejected on every
 * non-streaming path until the V2-8 streaming gates complete. The JSON Schema surface and
 * {@code GenerationRequestV2.Bounds} keep their own ceilings and are widened by a separate
 * approved Task, so this class never widens what a serialized artifact may declare.
 */
public final class ScaleDimensionPolicyV2 {
    /** Route ceiling for every non-streaming v2 grid: the MEDIUM scale-class contract. */
    public static final int MEDIUM_HORIZONTAL_CEILING = ScaleClassV2.MEDIUM.maximumHorizontalBlocks();

    private ScaleDimensionPolicyV2() {
    }

    /** True when both horizontal dimensions are within {@code minimum..MEDIUM_HORIZONTAL_CEILING}. */
    public static boolean withinMediumGrid(int width, int length, int minimum) {
        return width >= minimum && length >= minimum
                && width <= MEDIUM_HORIZONTAL_CEILING && length <= MEDIUM_HORIZONTAL_CEILING;
    }
}
