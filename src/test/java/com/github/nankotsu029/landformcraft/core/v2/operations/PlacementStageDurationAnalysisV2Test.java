package com.github.nankotsu029.landformcraft.core.v2.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricLabelV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricUnitV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricsSnapshotV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-13-01: the placement-stage duration labels are additive and measurement-only, and the
 * bottleneck analysis over them is deterministic. These are the machine-checkable acceptance
 * gates; the actual re-measurement is a dedicated real-host Task (BLOCKED_EXTERNAL here).
 */
class PlacementStageDurationAnalysisV2Test {

    private final OperationalMetricsCollectorV2 collector = new OperationalMetricsCollectorV2();

    private static Map<OperationalMetricLabelV2, Long> durations(
            long plan, long snapshot, long apply, long settle, long verify, long undo) {
        Map<OperationalMetricLabelV2, Long> map = new EnumMap<>(OperationalMetricLabelV2.class);
        map.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_PLAN, plan);
        map.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_SNAPSHOT, snapshot);
        map.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_APPLY, apply);
        map.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_SETTLE, settle);
        map.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_VERIFY, verify);
        map.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_UNDO, undo);
        return map;
    }

    @Test
    void identifiesLargestStageAndItsShare() {
        PlacementStageDurationAnalysisV2.BottleneckV2 bottleneck =
                PlacementStageDurationAnalysisV2.bottleneck(durations(12, 3600, 1500, 90, 720, 420));
        assertEquals(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_SNAPSHOT, bottleneck.stage());
        assertEquals(3600L, bottleneck.seconds());
        assertEquals(6342L, bottleneck.totalSeconds());
        assertTrue(bottleneck.fraction() > 0.56 && bottleneck.fraction() < 0.57);
    }

    @Test
    void bottleneckTieBreakIsDeterministicByLifecycleOrder() {
        // PLAN and SNAPSHOT tie for the max; the earlier lifecycle stage must always win, and the
        // result must not depend on map iteration order.
        PlacementStageDurationAnalysisV2.BottleneckV2 first =
                PlacementStageDurationAnalysisV2.bottleneck(durations(100, 100, 0, 0, 0, 0));
        assertEquals(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_PLAN, first.stage());

        Map<OperationalMetricLabelV2, Long> shuffled = new LinkedHashMap<>();
        shuffled.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_UNDO, 0L);
        shuffled.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_SNAPSHOT, 100L);
        shuffled.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_VERIFY, 0L);
        shuffled.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_PLAN, 100L);
        shuffled.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_APPLY, 0L);
        shuffled.put(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_SETTLE, 0L);
        assertEquals(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_PLAN,
                PlacementStageDurationAnalysisV2.bottleneck(shuffled).stage());
    }

    @Test
    void buildsCanonicalDurationOnlySnapshotThatValidatesAndRoundTrips() throws Exception {
        OperationalMetricsSnapshotV2 snapshot = PlacementStageDurationAnalysisV2.snapshot(
                "2026-07-21T00:00:00Z", durations(12, 3600, 1500, 90, 720, 420), collector);

        assertEquals(PlacementStageDurationAnalysisV2.STAGE_LABELS.size(), snapshot.samples().size());
        assertFalse(snapshot.hasPendingCanonicalChecksum());
        assertEquals(3600L,
                snapshot.valueOf(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_SNAPSHOT));
        snapshot.samples().forEach(sample ->
                assertEquals(OperationalMetricUnitV2.SECONDS, sample.unit()));

        // The duration-only snapshot must satisfy the same strict operational-metrics schema.
        new StructuredDataValidator().validate(
                "operational-metrics-snapshot-v2.schema.json",
                "durations",
                new ObjectMapper().valueToTree(snapshot));

        // Deterministic: same inputs → same canonical checksum.
        OperationalMetricsSnapshotV2 again = PlacementStageDurationAnalysisV2.snapshot(
                "2026-07-21T00:00:00Z", durations(12, 3600, 1500, 90, 720, 420), collector);
        assertEquals(snapshot.canonicalChecksum(), again.canonicalChecksum());
    }

    @Test
    void committedExampleParsesAndNamesTheSnapshotStageAsBottleneck() throws Exception {
        Path example = Path.of("examples/v2/operations/placement-stage-durations-v2.json");
        var tree = new ObjectMapper().readTree(example.toFile());
        new StructuredDataValidator().validate(
                "operational-metrics-snapshot-v2.schema.json", example.toString(), tree);

        Map<OperationalMetricLabelV2, Long> parsed = new EnumMap<>(OperationalMetricLabelV2.class);
        tree.get("samples").forEach(sample -> {
            assertEquals("SECONDS", sample.get("unit").asText());
            parsed.put(OperationalMetricLabelV2.valueOf(sample.get("label").asText()),
                    sample.get("value").asLong());
        });
        assertEquals(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_SNAPSHOT,
                PlacementStageDurationAnalysisV2.bottleneck(parsed).stage());
    }

    @Test
    void rejectsIncompleteOrNegativeOrForeignLabelSets() {
        // Missing one label.
        Map<OperationalMetricLabelV2, Long> missing = durations(1, 2, 3, 4, 5, 6);
        missing.remove(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_UNDO);
        assertThrows(IllegalArgumentException.class,
                () -> PlacementStageDurationAnalysisV2.bottleneck(missing));

        // Negative seconds.
        assertThrows(IllegalArgumentException.class,
                () -> PlacementStageDurationAnalysisV2.bottleneck(durations(1, -2, 3, 4, 5, 6)));

        // A non-duration label smuggled in (wrong closed vocabulary).
        Map<OperationalMetricLabelV2, Long> foreign = durations(1, 2, 3, 4, 5, 6);
        foreign.remove(OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_UNDO);
        foreign.put(OperationalMetricLabelV2.SETTLE_TICKS_OBSERVED, 7L);
        assertThrows(IllegalArgumentException.class,
                () -> PlacementStageDurationAnalysisV2.bottleneck(foreign));
    }

    @Test
    void durationLabelsRequireSecondsAndAreExcludedFromRuntimeCollector() {
        // Closed-enum unit binding: a duration label may only carry SECONDS.
        assertThrows(IllegalArgumentException.class, () -> new OperationalMetricsSnapshotV2.SampleV2(
                OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_APPLY,
                OperationalMetricUnitV2.COUNT, 1L));
        // And a runtime (COUNT) label may not claim the SECONDS unit.
        assertThrows(IllegalArgumentException.class, () -> new OperationalMetricsSnapshotV2.SampleV2(
                OperationalMetricLabelV2.VERIFY_SCANNED_BLOCKS,
                OperationalMetricUnitV2.SECONDS, 1L));

        // The runtime collector never emits the measurement-only duration labels.
        for (OperationalMetricLabelV2 stage : PlacementStageDurationAnalysisV2.STAGE_LABELS) {
            assertFalse(OperationalMetricsCollectorV2.RUNTIME_LABELS.contains(stage));
        }
        assertEquals(17, OperationalMetricsCollectorV2.RUNTIME_LABELS.size());
    }
}
