package com.github.nankotsu029.landformcraft.model.v2.scale;

/**
 * Unified horizontal dimension policy for v2 grids (V2-8-02). Every formerly scattered
 * {@code 1..1_000} grid check routes through this class so the route ceiling has exactly one
 * owner: the {@link ScaleClassV2#MEDIUM} contract. LARGE dimensions stay rejected on every
 * non-streaming path until the V2-8 streaming gates complete. Serialized request and Blueprint
 * Schema horizontal ceilings follow the same MEDIUM contract ({@code GenerationRequestV2.Bounds}
 * and Schema {@code width}/{@code length} maxima). Placement measured catalogs
 * ({@code PlacementDimensionLimitV2}) remain a separate gate.
 */
public final class ScaleDimensionPolicyV2 {
    /** Route ceiling for every non-streaming v2 grid: the MEDIUM scale-class contract. */
    public static final int MEDIUM_HORIZONTAL_CEILING = ScaleClassV2.MEDIUM.maximumHorizontalBlocks();

    /**
     * Cell-count ceiling for one full-resolution MEDIUM grid ({@code MEDIUM_HORIZONTAL_CEILING}²).
     * Single owner for every per-plan {@code globalCellCount}/raster/preview/scan cell budget that
     * previously hard-coded the pre-MEDIUM-1024 {@code 1_000_000} literal; those budgets now admit
     * every width/length pair already permitted by {@link #MEDIUM_HORIZONTAL_CEILING}.
     */
    public static final long MEDIUM_MAXIMUM_CELLS =
            (long) MEDIUM_HORIZONTAL_CEILING * (long) MEDIUM_HORIZONTAL_CEILING;

    private ScaleDimensionPolicyV2() {
    }

    /** True when both horizontal dimensions are within {@code minimum..MEDIUM_HORIZONTAL_CEILING}. */
    public static boolean withinMediumGrid(int width, int length, int minimum) {
        return width >= minimum && length >= minimum
                && width <= MEDIUM_HORIZONTAL_CEILING && length <= MEDIUM_HORIZONTAL_CEILING;
    }
}
