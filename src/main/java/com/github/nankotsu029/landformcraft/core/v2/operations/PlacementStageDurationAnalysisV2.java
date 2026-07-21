package com.github.nankotsu029.landformcraft.core.v2.operations;

import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricLabelV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricUnitV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricsSnapshotV2;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic analysis of the additive placement-stage wall-clock durations (V2-13-01).
 *
 * <p>The offline measurement runner times each observable placement lifecycle stage and reports a
 * whole-second duration keyed by the closed {@link OperationalMetricLabelV2} duration labels. This
 * helper turns those durations into (1) a canonical {@link OperationalMetricsSnapshotV2} evidence
 * record — the committed measurement format — and (2) a deterministic bottleneck identification so
 * the largest stage of the total wall time can be named from committed evidence without any
 * free-form label.
 *
 * <p>Ties are broken by lifecycle order ({@link #STAGE_LABELS}): the earliest stage wins, so the
 * bottleneck is a pure function of the input durations and never depends on map iteration order.
 * This class performs no measurement and touches no world state; it only shapes evidence.
 */
public final class PlacementStageDurationAnalysisV2 {

    /** The six placement-stage duration labels in lifecycle order (also the tie-break order). */
    public static final List<OperationalMetricLabelV2> STAGE_LABELS = List.of(
            OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_PLAN,
            OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_SNAPSHOT,
            OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_APPLY,
            OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_SETTLE,
            OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_VERIFY,
            OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_UNDO);

    private PlacementStageDurationAnalysisV2() {
    }

    /** Identified bottleneck stage plus its share of the summed stage durations. */
    public record BottleneckV2(OperationalMetricLabelV2 stage, long seconds, long totalSeconds) {
        public BottleneckV2 {
            Objects.requireNonNull(stage, "stage");
            if (seconds < 0L || totalSeconds < 0L || seconds > totalSeconds) {
                throw new IllegalArgumentException("invalid bottleneck accounting");
            }
        }

        /** Fraction of the summed stage wall time attributable to the bottleneck stage (0..1). */
        public double fraction() {
            return totalSeconds == 0L ? 0.0 : (double) seconds / (double) totalSeconds;
        }
    }

    /**
     * Identifies the largest stage. Requires exactly the six {@link #STAGE_LABELS} with
     * non-negative whole-second values. Deterministic under ties (lifecycle order).
     */
    public static BottleneckV2 bottleneck(Map<OperationalMetricLabelV2, Long> stageSeconds) {
        EnumMap<OperationalMetricLabelV2, Long> validated = validate(stageSeconds);
        OperationalMetricLabelV2 top = STAGE_LABELS.get(0);
        long best = -1L;
        long total = 0L;
        for (OperationalMetricLabelV2 label : STAGE_LABELS) {
            long value = validated.get(label);
            total += value;
            if (value > best) {
                best = value;
                top = label;
            }
        }
        return new BottleneckV2(top, best, total);
    }

    /**
     * Builds the committed measurement evidence as a canonical {@link OperationalMetricsSnapshotV2}
     * containing only the six stage-duration samples (unit SECONDS). The runtime collector's own
     * snapshots are unaffected; this is a separate, duration-only view.
     */
    public static OperationalMetricsSnapshotV2 snapshot(
            String capturedAt,
            Map<OperationalMetricLabelV2, Long> stageSeconds,
            OperationalMetricsCollectorV2 collector) {
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(collector, "collector");
        EnumMap<OperationalMetricLabelV2, Long> validated = validate(stageSeconds);
        List<OperationalMetricsSnapshotV2.SampleV2> samples = new ArrayList<>();
        for (OperationalMetricLabelV2 label : STAGE_LABELS) {
            samples.add(new OperationalMetricsSnapshotV2.SampleV2(
                    label, OperationalMetricUnitV2.SECONDS, validated.get(label)));
        }
        OperationalMetricsSnapshotV2 draft = OperationalMetricsSnapshotV2.draft(capturedAt, samples);
        return draft.withCanonicalChecksum(collector.canonicalChecksum(draft));
    }

    private static EnumMap<OperationalMetricLabelV2, Long> validate(
            Map<OperationalMetricLabelV2, Long> stageSeconds) {
        Objects.requireNonNull(stageSeconds, "stageSeconds");
        if (stageSeconds.size() != STAGE_LABELS.size()) {
            throw new IllegalArgumentException(
                    "expected exactly the six placement-stage duration labels");
        }
        EnumMap<OperationalMetricLabelV2, Long> validated =
                new EnumMap<>(OperationalMetricLabelV2.class);
        for (OperationalMetricLabelV2 label : STAGE_LABELS) {
            Long value = stageSeconds.get(label);
            if (value == null) {
                throw new IllegalArgumentException("missing stage duration: " + label);
            }
            if (value < 0L) {
                throw new IllegalArgumentException("stage duration seconds must be >= 0: " + label);
            }
            validated.put(label, value);
        }
        return validated;
    }
}
