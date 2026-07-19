package com.github.nankotsu029.landformcraft.core.v2.placement.rollback;

/**
 * Canonical partial-failure classification for Release 2 rollback (V2-6-08). Every rollback that
 * does not reach terminal {@code ROLLED_BACK} is classified here and the journal is left in
 * {@code RECOVERY_REQUIRED}; rollback never reports success it cannot prove.
 */
public enum PlacementRollbackFailureCodeV2 {
    UNKNOWN_POLICY,
    STATE_MISMATCH,
    BINDING_MISMATCH,
    RESERVATION_MISSING,
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
    ROLLBACK_SHUTDOWN,
    CANCELLED,
    JOURNAL_PERSISTENCE_FAILED,
    RESOURCE_BUDGET_EXCEEDED
}
