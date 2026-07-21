package com.github.nankotsu029.landformcraft.model.v2.catalog;

/**
 * Measured Paper placement dimension ceiling published by the Feature Support Catalog.
 * Matches {@code placement.release2.measured-candidate-max-*}; every value here is backed by a
 * completed real-machine measurement Task, never by an estimate.
 */
public record PlacementDimensionLimitV2(int maximumWidth, int maximumLength) {
    /**
     * Dimension measured on both supported runtimes (WorldEdit 7.3.19 in V2-6-14 and
     * FAWE 2.15.2 in V2-6-15). This stays the production ceiling for a WorldEdit-only server.
     */
    public static final int SMOKE_MEASURED_MAXIMUM = 64;

    /**
     * Dimension measured on FAWE 2.15.2 only: V2-11-04 (500x500) and V2-11-05 (1000x1000) each
     * completed plan→confirm→snapshot-all→apply→settle→full effect-envelope verify→Undo twice on
     * a dedicated host. V2-11-06 publishes this as the catalog ceiling; nothing above it is
     * measured, and a WorldEdit-only runtime is still clamped to {@link #SMOKE_MEASURED_MAXIMUM}.
     */
    public static final int MEASURED_MAXIMUM = 1_000;

    /**
     * Absolute catalog budget for any published or measurement-profile dimension ceiling.
     * Normal Release 2 placement is clamped to the measured ceiling for the detected runtime;
     * only the V2-11-02 measurement profile may declare a ceiling between those two values.
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

    /** Published catalog ceiling after the V2-11-04/V2-11-05 measurements (V2-11-06). */
    public static PlacementDimensionLimitV2 measured() {
        return new PlacementDimensionLimitV2(MEASURED_MAXIMUM, MEASURED_MAXIMUM);
    }

    public boolean admits(int width, int length) {
        return width >= 1 && length >= 1 && width <= maximumWidth && length <= maximumLength;
    }
}
