package com.github.nankotsu029.landformcraft.core.v2.placement.undo;

/**
 * Canonical failure classification for Release 2 Undo (V2-6-09). Prepare failures never mutate
 * the world. Execute failures after the first restore leave {@code RECOVERY_REQUIRED}; world drift
 * and missing／tampered snapshot are always rejected with zero mutations.
 */
public enum PlacementUndoFailureCodeV2 {
    UNKNOWN_POLICY,
    STATE_MISMATCH,
    BINDING_MISMATCH,
    ACTOR_MISMATCH,
    CONFIRMATION_INVALID,
    CONFIRMATION_EXPIRED,
    CONFIRMATION_REPLAY,
    RESERVATION_MISSING,
    RESERVATION_FAILED,
    DISK_SHORTAGE,
    WORLD_DRIFT,
    SNAPSHOT_MISSING,
    SNAPSHOT_TAMPERED,
    SNAPSHOT_COVERAGE_GAP,
    RESTORE_GATEWAY_FAILURE,
    RESTORE_RECEIPT_INVALID,
    RESTORE_SLICE_BUDGET,
    GATEWAY_FAILURE,
    GATEWAY_RECEIPT_INVALID,
    RESERVATION_RELEASE_FAILED,
    SETTLE_TIMEOUT,
    SETTLE_OUT_OF_ENVELOPE_UPDATE,
    VERIFY_MISMATCH,
    VERIFY_SLICE_BUDGET,
    QUEUE_SATURATED,
    SERVICE_CLOSED,
    UNDO_SHUTDOWN,
    CANCELLED,
    JOURNAL_PERSISTENCE_FAILED,
    RESOURCE_BUDGET_EXCEEDED
}
