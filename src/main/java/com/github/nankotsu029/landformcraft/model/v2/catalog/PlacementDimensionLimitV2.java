package com.github.nankotsu029.landformcraft.model.v2.catalog;

/**
 * Measured Paper placement dimension ceiling published by the Feature Support Catalog.
 * Matches {@code placement.release2.measured-candidate-max-*} (smoke-sized WE/FAWE evidence).
 */
public record PlacementDimensionLimitV2(int maximumWidth, int maximumLength) {
    public static final int SMOKE_MEASURED_MAXIMUM = 64;

    /**
     * Absolute catalog budget for any published or measurement-profile dimension ceiling.
     * Normal Release 2 placement is clamped to {@link #SMOKE_MEASURED_MAXIMUM}; only the
     * V2-11-02 measurement profile may declare a ceiling between those two values.
     */
    public static final int CATALOG_BUDGET_MAXIMUM = 10_000;

    public PlacementDimensionLimitV2 {
        if (maximumWidth < 1 || maximumLength < 1) {
            throw new IllegalArgumentException("placement dimension limit must be >= 1");
        }
        if (maximumWidth > CATALOG_BUDGET_MAXIMUM || maximumLength > CATALOG_BUDGET_MAXIMUM) {
            throw new IllegalArgumentException("placement dimension limit exceeds catalog budget");
        }
    }

    public static PlacementDimensionLimitV2 smokeMeasured() {
        return new PlacementDimensionLimitV2(SMOKE_MEASURED_MAXIMUM, SMOKE_MEASURED_MAXIMUM);
    }

    public boolean admits(int width, int length) {
        return width >= 1 && length >= 1 && width <= maximumWidth && length <= maximumLength;
    }
}
