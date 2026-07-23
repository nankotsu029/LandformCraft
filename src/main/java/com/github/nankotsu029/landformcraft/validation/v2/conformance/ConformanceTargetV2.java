package com.github.nankotsu029.landformcraft.validation.v2.conformance;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Objects;

/**
 * One intent-conformance target (V2-18-07), a member of the heterogeneous {@link ConformanceTargetSetV2}.
 *
 * <p>Each variant carries only what its {@link ConformanceTargetKindV2} needs, so a residual can be
 * computed and represented faithfully for it ({@link ConformanceResidualV2}). The sealed hierarchy makes
 * the set of kinds closed and exhaustively switchable — a new kind is a deliberate, reviewable addition
 * rather than a silently-defaulted string.</p>
 */
public sealed interface ConformanceTargetV2
        permits ConformanceTargetV2.DesiredRaster,
        ConformanceTargetV2.AggregateMetric,
        ConformanceTargetV2.Topology,
        ConformanceTargetV2.Geometric {

    /** Stable id of this target (the compiled {@code ValidationTargetV2.targetId} where one exists). */
    String targetId();

    /** The kind axis this target belongs to. */
    ConformanceTargetKindV2 kind();

    /** HARD targets gate; SOFT targets are advisory. */
    TerrainIntentV2.Strength hardness();

    /**
     * A per-cell desired field the actual field must reproduce (e.g. the land/water mask). The desired
     * reference is described by {@link #provenance()} and may be self-derived, in which case the raster
     * residual is unconsumed rather than vacuously satisfied. A desired raster is optional: not every
     * request supplies one (V2-18-07 non-scope).
     */
    record DesiredRaster(
            String targetId,
            String fieldId,
            TerrainIntentV2.Strength hardness,
            ConformanceProvenanceV2 provenance
    ) implements ConformanceTargetV2 {
        public DesiredRaster {
            targetId = Objects.requireNonNull(targetId, "targetId");
            fieldId = Objects.requireNonNull(fieldId, "fieldId");
            hardness = Objects.requireNonNull(hardness, "hardness");
            provenance = Objects.requireNonNull(provenance, "provenance");
            if (targetId.isBlank()) throw new IllegalArgumentException("targetId must not be blank");
            if (fieldId.isBlank()) throw new IllegalArgumentException("fieldId must not be blank");
        }

        @Override
        public ConformanceTargetKindV2 kind() {
            return ConformanceTargetKindV2.DESIRED_RASTER;
        }
    }

    /** A share/ratio measured over a region and checked against an expected range (e.g. edge land share). */
    record AggregateMetric(
            String targetId,
            String ruleId,
            String metric,
            TerrainIntentV2.Strength hardness,
            TerrainIntentV2.FixedRange expected,
            long toleranceMillionths
    ) implements ConformanceTargetV2 {
        public AggregateMetric {
            targetId = requireScalarFields(targetId, ruleId, metric, hardness, expected, toleranceMillionths);
            ruleId = Objects.requireNonNull(ruleId, "ruleId");
            metric = Objects.requireNonNull(metric, "metric");
        }

        @Override
        public ConformanceTargetKindV2 kind() {
            return ConformanceTargetKindV2.AGGREGATE_METRIC;
        }
    }

    /** A connectivity/graph property that either holds or does not (checked against an expected range). */
    record Topology(
            String targetId,
            String ruleId,
            String metric,
            TerrainIntentV2.Strength hardness,
            TerrainIntentV2.FixedRange expected,
            long toleranceMillionths
    ) implements ConformanceTargetV2 {
        public Topology {
            targetId = requireScalarFields(targetId, ruleId, metric, hardness, expected, toleranceMillionths);
            ruleId = Objects.requireNonNull(ruleId, "ruleId");
            metric = Objects.requireNonNull(metric, "metric");
        }

        @Override
        public ConformanceTargetKindV2 kind() {
            return ConformanceTargetKindV2.TOPOLOGY;
        }
    }

    /** A physical dimension measured from the field (e.g. beach width, harbor depth, breakwater opening). */
    record Geometric(
            String targetId,
            String ruleId,
            String metric,
            TerrainIntentV2.Strength hardness,
            TerrainIntentV2.FixedRange expected,
            long toleranceMillionths
    ) implements ConformanceTargetV2 {
        public Geometric {
            targetId = requireScalarFields(targetId, ruleId, metric, hardness, expected, toleranceMillionths);
            ruleId = Objects.requireNonNull(ruleId, "ruleId");
            metric = Objects.requireNonNull(metric, "metric");
        }

        @Override
        public ConformanceTargetKindV2 kind() {
            return ConformanceTargetKindV2.GEOMETRIC;
        }
    }

    private static String requireScalarFields(
            String targetId,
            String ruleId,
            String metric,
            TerrainIntentV2.Strength hardness,
            TerrainIntentV2.FixedRange expected,
            long toleranceMillionths
    ) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(hardness, "hardness");
        Objects.requireNonNull(expected, "expected");
        if (targetId.isBlank()) throw new IllegalArgumentException("targetId must not be blank");
        if (toleranceMillionths < 0) throw new IllegalArgumentException("tolerance must be non-negative");
        return targetId;
    }
}
