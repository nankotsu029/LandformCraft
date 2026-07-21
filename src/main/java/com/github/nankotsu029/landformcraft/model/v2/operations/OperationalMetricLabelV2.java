package com.github.nankotsu029.landformcraft.model.v2.operations;

/**
 * Closed set of operational metric labels (V2-6-13). Unbounded custom labels are rejected so
 * cardinality cannot grow with operator input.
 */
public enum OperationalMetricLabelV2 {
    GENERATION_QUEUE_DEPTH,
    GENERATION_ACTIVE_TASKS,
    GENERATION_QUEUE_CAPACITY,
    IO_AVAILABLE_PERMITS,
    IN_FLIGHT_TASKS,
    DISK_USABLE_BYTES,
    MEMORY_HEAP_USED_BYTES,
    MEMORY_HEAP_MAX_BYTES,
    PLACEMENT_STAGE_PLANNED,
    PLACEMENT_STAGE_APPLYING,
    PLACEMENT_STAGE_SETTLING,
    PLACEMENT_STAGE_VERIFYING,
    PLACEMENT_STAGE_RECOVERY_REQUIRED,
    PLACEMENT_STAGE_TERMINAL,
    SETTLE_TICKS_OBSERVED,
    VERIFY_SCANNED_BLOCKS,
    RUNNING_GENERATION_JOBS,
    // Additive placement-stage wall-clock durations (V2-13-01). These decompose the total
    // real-world placement wall time (V2-11-05: ~106 min/pass) into the observable lifecycle
    // stages so the largest bottleneck stage can be identified from committed evidence. Unit is
    // SECONDS. They are NOT emitted by the runtime OperationalMetricsCollectorV2 (see its
    // RUNTIME_LABELS set); they are populated only by the offline measurement runner, so existing
    // runtime snapshot samples and their canonical checksums are unchanged.
    PLACEMENT_STAGE_DURATION_PLAN,
    PLACEMENT_STAGE_DURATION_SNAPSHOT,
    PLACEMENT_STAGE_DURATION_APPLY,
    PLACEMENT_STAGE_DURATION_SETTLE,
    PLACEMENT_STAGE_DURATION_VERIFY,
    PLACEMENT_STAGE_DURATION_UNDO
}
