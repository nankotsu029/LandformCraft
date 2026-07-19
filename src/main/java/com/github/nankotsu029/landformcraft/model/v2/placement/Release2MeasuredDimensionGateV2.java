package com.github.nankotsu029.landformcraft.model.v2.placement;

import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;

/**
 * Paper placement dimension ceiling for Release 2. Default admits smoke-sized layouts only
 * (WE／FAWE smoke evidence). Catalog {@code SUPPORTED} promotion is V2-6-18; unmeasured sizes
 * such as 500／1000 must not be admitted without a new measurement Task.
 */
public record Release2MeasuredDimensionGateV2(int maximumWidth, int maximumLength) {
    public static final String CONFIG_WIDTH_KEY = "placement.release2.measured-candidate-max-width";
    public static final String CONFIG_LENGTH_KEY = "placement.release2.measured-candidate-max-length";

    /** Unit tests and offline callers may opt out of a published ceiling. */
    public static Release2MeasuredDimensionGateV2 unlimited() {
        return new Release2MeasuredDimensionGateV2(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** Normal-operation ceiling, always the Feature Support Catalog hard limit (V2-11-02). */
    public static Release2MeasuredDimensionGateV2 production() {
        return new Release2MeasuredDimensionGateV2(
                PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM,
                PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM);
    }

    /**
     * Normal-operation ceiling from configuration. V2-11-02 clamps configuration to the catalog
     * hard limit so no setting can widen the production ceiling; above-limit dimensions are only
     * reachable through the explicit measurement profile.
     */
    public static Release2MeasuredDimensionGateV2 production(int configuredWidth, int configuredLength) {
        requireWithinCatalog(configuredWidth, CONFIG_WIDTH_KEY);
        requireWithinCatalog(configuredLength, CONFIG_LENGTH_KEY);
        return new Release2MeasuredDimensionGateV2(configuredWidth, configuredLength);
    }

    private static void requireWithinCatalog(int value, String key) {
        if (value < 1 || value > PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM) {
            throw new IllegalArgumentException(
                    key + " must be between 1 and the Feature Support Catalog hard limit "
                            + PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM
                            + " (unmeasured dimensions require the V2-11-02 measurement profile)");
        }
    }

    public Release2MeasuredDimensionGateV2 {
        if (maximumWidth < 1 || maximumLength < 1) {
            throw new IllegalArgumentException("measured candidate dimensions must be >= 1");
        }
    }

    public void requireAdmitted(int width, int length) {
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("Release layout dimensions must be >= 1");
        }
        if (width > maximumWidth || length > maximumLength) {
            throw new IllegalArgumentException(
                    "Release 2 Paper placement dimensions "
                            + width + "x" + length
                            + " exceed measured candidate ceiling "
                            + maximumWidth + "x" + maximumLength
                            + " (smoke-sized evidence only; catalog SUPPORTED is V2-6-18)");
        }
    }
}
