package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

/**
 * Canonical failure classification for Release 2 settle／full verify (V2-6-07).
 */
public enum PlacementVerifyFailureCodeV2 {
    UNKNOWN_POLICY,
    STATE_MISMATCH,
    BINDING_MISMATCH,
    TILE_CHECKPOINT_INSUFFICIENT,
    SETTLE_TIMEOUT,
    SETTLE_OUT_OF_ENVELOPE_UPDATE,
    SETTLE_SHUTDOWN,
    VERIFY_MISMATCH,
    VERIFY_SLICE_BUDGET,
    QUEUE_SATURATED,
    SERVICE_CLOSED,
    CANCELLED,
    GATEWAY_FAILURE,
    GATEWAY_RECEIPT_INVALID,
    SOURCE_INVALID,
    JOURNAL_PERSISTENCE_FAILED,
    RESOURCE_BUDGET_EXCEEDED,
    RECOVERY_REQUIRED
}
