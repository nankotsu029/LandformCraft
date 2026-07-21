package com.github.nankotsu029.landformcraft.model.v2.placement;

import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;

/**
 * Paper placement dimension ceiling for Release 2. The ceiling is whatever the detected runtime
 * has actually measured: 64x64 on WorldEdit 7.3.19 (V2-6-14/15 smoke) and 1000x1000 on
 * FAWE 2.15.2 (V2-11-04 500x500, V2-11-05 1000x1000, published by V2-11-06). Anything above the
 * runtime ceiling is unmeasured and must not be admitted without a new measurement Task.
 */
public record Release2MeasuredDimensionGateV2(int maximumWidth, int maximumLength) {
    public static final String CONFIG_WIDTH_KEY = "placement.release2.measured-candidate-max-width";
    public static final String CONFIG_LENGTH_KEY = "placement.release2.measured-candidate-max-length";

    /** Unit tests and offline callers may opt out of a published ceiling. */
    public static Release2MeasuredDimensionGateV2 unlimited() {
        return new Release2MeasuredDimensionGateV2(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Measured ceiling for a runtime. FAWE 2.15.2 carries the V2-11-04/V2-11-05 evidence up to
     * 1000x1000; every other runtime stays at the 64x64 size measured on both runtimes.
     */
    public static int measuredCeilingFor(boolean fastAsyncWorldEdit) {
        return fastAsyncWorldEdit
                ? PlacementDimensionLimitV2.MEASURED_MAXIMUM
                : PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM;
    }

    /** Conservative normal-operation ceiling: the size measured on every supported runtime. */
    public static Release2MeasuredDimensionGateV2 production() {
        return production(false,
                PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM,
                PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM);
    }

    /** Normal-operation ceiling for the detected runtime, at its full measured size. */
    public static Release2MeasuredDimensionGateV2 production(boolean fastAsyncWorldEdit) {
        int ceiling = measuredCeilingFor(fastAsyncWorldEdit);
        return production(fastAsyncWorldEdit, ceiling, ceiling);
    }

    /**
     * Normal-operation ceiling from configuration, for a runtime without the above-smoke
     * measurement evidence.
     */
    public static Release2MeasuredDimensionGateV2 production(int configuredWidth, int configuredLength) {
        return production(false, configuredWidth, configuredLength);
    }

    /**
     * Normal-operation ceiling from configuration. V2-11-02 clamps configuration to the measured
     * ceiling so no setting can widen the production ceiling past real evidence; V2-11-06 makes
     * that ceiling runtime-dependent. Above-limit dimensions stay reachable only through the
     * explicit measurement profile.
     */
    public static Release2MeasuredDimensionGateV2 production(
            boolean fastAsyncWorldEdit, int configuredWidth, int configuredLength) {
        int ceiling = measuredCeilingFor(fastAsyncWorldEdit);
        requireWithinCatalog(configuredWidth, CONFIG_WIDTH_KEY, ceiling);
        requireWithinCatalog(configuredLength, CONFIG_LENGTH_KEY, ceiling);
        return new Release2MeasuredDimensionGateV2(configuredWidth, configuredLength);
    }

    private static void requireWithinCatalog(int value, String key, int ceiling) {
        if (value < 1 || value > ceiling) {
            throw new IllegalArgumentException(
                    key + " must be between 1 and the measured ceiling for this runtime "
                            + ceiling
                            + " (unmeasured dimensions require the V2-11-02 measurement profile;"
                            + " sizes above "
                            + PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM
                            + " are measured on FAWE 2.15.2 only)");
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
                            + " (measured evidence only; catalog dimensions are V2-11-06)");
        }
    }
}
