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
    RUNNING_GENERATION_JOBS
}
