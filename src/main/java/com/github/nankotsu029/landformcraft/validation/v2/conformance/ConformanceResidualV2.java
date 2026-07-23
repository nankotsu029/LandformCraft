package com.github.nankotsu029.landformcraft.validation.v2.conformance;

import java.util.Objects;

/**
 * The residual of one conformance target (V2-18-07): how far the actual result is from the desired one,
 * represented in the shape that fits the target's {@link ConformanceTargetKindV2}.
 *
 * <p>The macro-foundation audit's item 3 was that residuals were forced through one scalar-count shape,
 * so a self-referential desired field produced an identically-zero residual that read as "conforming".
 * This sealed taxonomy replaces that single shape with the distinct residual kinds a heterogeneous
 * target set actually needs, and — crucially — separates "measured and within tolerance" from
 * "never measured": a target with no resolvable desired reference or no evaluator becomes an
 * {@link UnconsumedTarget}, never a vacuously-passing zero.</p>
 *
 * <ul>
 *   <li>{@link RasterResidual} — mismatched cell count between a desired and an actual raster.</li>
 *   <li>{@link ScalarMetricResidual} — a measured scalar against its expected range (aggregate/geometric).</li>
 *   <li>{@link TopologyPassFail} — a connectivity property that holds or does not.</li>
 *   <li>{@link UnconsumedTarget} — a target that could not be checked (no desired reference / no
 *       evaluator / missing measurement). This is the honest replacement for a false zero.</li>
 *   <li>{@link ToleranceViolation} — a measured scalar that falls outside its expected range even after
 *       its tolerance is applied. Emitted alongside the {@link ScalarMetricResidual} it violates.</li>
 * </ul>
 */
public sealed interface ConformanceResidualV2
        permits ConformanceResidualV2.RasterResidual,
        ConformanceResidualV2.ScalarMetricResidual,
        ConformanceResidualV2.TopologyPassFail,
        ConformanceResidualV2.UnconsumedTarget,
        ConformanceResidualV2.ToleranceViolation {

    /** The target this residual belongs to. */
    String targetId();

    /** True when this residual represents a satisfied target. Unconsumed targets are never satisfied. */
    boolean satisfied();

    /** Per-cell raster mismatch between a resolvable desired raster and the actual field. */
    record RasterResidual(String targetId, long mismatchCells, long comparedCells)
            implements ConformanceResidualV2 {
        public RasterResidual {
            targetId = Objects.requireNonNull(targetId, "targetId");
            if (mismatchCells < 0 || comparedCells < 0 || mismatchCells > comparedCells) {
                throw new IllegalArgumentException("raster residual cell counts are invalid");
            }
        }

        @Override
        public boolean satisfied() {
            return mismatchCells == 0;
        }
    }

    /** A measured scalar (aggregate share or geometric dimension) against its expected range. */
    record ScalarMetricResidual(
            String targetId,
            long measuredMillionths,
            long expectedMinimumMillionths,
            long expectedMaximumMillionths,
            long toleranceMillionths
    ) implements ConformanceResidualV2 {
        public ScalarMetricResidual {
            targetId = Objects.requireNonNull(targetId, "targetId");
            if (expectedMinimumMillionths > expectedMaximumMillionths) {
                throw new IllegalArgumentException("expected minimum exceeds maximum");
            }
            if (toleranceMillionths < 0) {
                throw new IllegalArgumentException("tolerance must be non-negative");
            }
        }

        @Override
        public boolean satisfied() {
            return measuredMillionths + toleranceMillionths >= expectedMinimumMillionths
                    && measuredMillionths - toleranceMillionths <= expectedMaximumMillionths;
        }
    }

    /** A connectivity/graph property that either holds or does not. */
    record TopologyPassFail(String targetId, boolean passed, String detail)
            implements ConformanceResidualV2 {
        public TopologyPassFail {
            targetId = Objects.requireNonNull(targetId, "targetId");
            detail = Objects.requireNonNull(detail, "detail");
        }

        @Override
        public boolean satisfied() {
            return passed;
        }
    }

    /** A target that could not be checked. The honest replacement for a false zero residual. */
    record UnconsumedTarget(String targetId, ConformanceTargetKindV2 kind, String reason)
            implements ConformanceResidualV2 {
        public UnconsumedTarget {
            targetId = Objects.requireNonNull(targetId, "targetId");
            kind = Objects.requireNonNull(kind, "kind");
            reason = Objects.requireNonNull(reason, "reason");
        }

        @Override
        public boolean satisfied() {
            return false;
        }
    }

    /** A measured scalar outside its expected range even after tolerance. Accompanies its residual. */
    record ToleranceViolation(
            String targetId,
            long measuredMillionths,
            long nearestBoundMillionths,
            long toleranceMillionths
    ) implements ConformanceResidualV2 {
        public ToleranceViolation {
            targetId = Objects.requireNonNull(targetId, "targetId");
            if (toleranceMillionths < 0) {
                throw new IllegalArgumentException("tolerance must be non-negative");
            }
        }

        @Override
        public boolean satisfied() {
            return false;
        }
    }
}
