package com.github.nankotsu029.landformcraft.validation.v2.conformance;

import com.github.nankotsu029.landformcraft.model.v2.ValidationTargetV2;

import java.util.Map;

/**
 * Classifies a compiled {@link ValidationTargetV2} into its {@link ConformanceTargetKindV2} (V2-18-07).
 *
 * <p>The mapping is an explicit, version-pinned table keyed on the target's {@code ruleId}, so which
 * conformance axis a rule belongs to is a single reviewable authority rather than being re-derived from
 * a metric string at every call site. The table covers every rule id the current
 * {@code DiagnosticBlueprintCompilerV2} emits (coastal geometry/topology, the edge-share aggregate, the
 * hydrology graph checks, and the user {@code v2.metric-range}); a rule id with no entry defaults to
 * {@link ConformanceTargetKindV2#AGGREGATE_METRIC}, the most general scalar shape, so a newly-introduced
 * rule degrades to a plain scalar residual rather than being dropped. A new rule that needs a specific
 * axis is a deliberate table entry plus a {@link #CLASSIFICATION_VERSION} bump.</p>
 */
public final class ConformanceTargetClassifierV2 {
    /** Bump when the rule-id → kind table changes, so a reclassification is an explicit, reviewable event. */
    public static final int CLASSIFICATION_VERSION = 1;

    private static final Map<String, ConformanceTargetKindV2> RULE_KINDS = Map.ofEntries(
            // Coastal geometry — a physical dimension measured from the field.
            Map.entry("coastal.beach.width", ConformanceTargetKindV2.GEOMETRIC),
            Map.entry("coastal.harbor.depth", ConformanceTargetKindV2.GEOMETRIC),
            Map.entry("coastal.breakwater.opening", ConformanceTargetKindV2.GEOMETRIC),
            // Coastal share and consistency.
            Map.entry("coastal.cape.exposure", ConformanceTargetKindV2.AGGREGATE_METRIC),
            Map.entry("coastal.transition.conflict", ConformanceTargetKindV2.TOPOLOGY),
            // Edge land/sea share and the generic user scalar range.
            Map.entry("v2.edge-classification", ConformanceTargetKindV2.AGGREGATE_METRIC),
            Map.entry("v2.metric-range", ConformanceTargetKindV2.AGGREGATE_METRIC),
            // Hydrology graph connectivity.
            Map.entry("hydrology.river.reachability", ConformanceTargetKindV2.TOPOLOGY),
            Map.entry("hydrology.lake.leaking", ConformanceTargetKindV2.TOPOLOGY),
            Map.entry("hydrology.delta.dead-branch", ConformanceTargetKindV2.TOPOLOGY),
            Map.entry("hydrology.tidal.marine-connection", ConformanceTargetKindV2.TOPOLOGY),
            Map.entry("hydrology.fjord.broken-outlet", ConformanceTargetKindV2.TOPOLOGY),
            Map.entry("hydrology.mountain.ridge", ConformanceTargetKindV2.TOPOLOGY),
            Map.entry("hydrology.volcanic.components", ConformanceTargetKindV2.TOPOLOGY),
            // Hydrology geometry — bed monotonicity and fall envelope.
            Map.entry("hydrology.river.reverse-gradient", ConformanceTargetKindV2.GEOMETRIC),
            Map.entry("hydrology.waterfall.fall-mismatch", ConformanceTargetKindV2.GEOMETRIC));

    private ConformanceTargetClassifierV2() {
    }

    /** The conformance axis of a rule id; unknown rules default to {@link ConformanceTargetKindV2#AGGREGATE_METRIC}. */
    public static ConformanceTargetKindV2 kindOf(String ruleId) {
        return RULE_KINDS.getOrDefault(ruleId, ConformanceTargetKindV2.AGGREGATE_METRIC);
    }

    /** Builds the scalar conformance target for a compiled validation target, by its classified kind. */
    public static ConformanceTargetV2 classify(ValidationTargetV2 target) {
        return switch (kindOf(target.ruleId())) {
            case AGGREGATE_METRIC -> new ConformanceTargetV2.AggregateMetric(
                    target.targetId(), target.ruleId(), target.metric(), target.hardness(),
                    target.expected(), target.toleranceMillionths());
            case TOPOLOGY -> new ConformanceTargetV2.Topology(
                    target.targetId(), target.ruleId(), target.metric(), target.hardness(),
                    target.expected(), target.toleranceMillionths());
            case GEOMETRIC -> new ConformanceTargetV2.Geometric(
                    target.targetId(), target.ruleId(), target.metric(), target.hardness(),
                    target.expected(), target.toleranceMillionths());
            // A ValidationTargetV2 never classifies to a desired raster: that target has no scalar
            // expected range and is built from a LAND_WATER_MASK binding, not a compiled target.
            case DESIRED_RASTER -> throw new IllegalStateException(
                    "validation target must not classify as a desired raster: " + target.targetId());
        };
    }
}
