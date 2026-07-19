package com.github.nankotsu029.landformcraft.model.v2.placement;

/**
 * Format-2 placement journal lifecycle. Distinct from v1 {@code PlacementState}; states beyond
 * {@link #PLANNED} are defined here so later V2-6 Tasks can advance the machine without Schema
 * churn. V2-6-01 only emits {@link #PLANNED}.
 */
public enum PlacementJournalStateV2 {
    PLANNED,
    RELEASE_VALIDATED,
    ENVELOPE_BOUND,
    RESERVATION_BOUND,
    CONFIRMATION_ISSUED,
    SNAPSHOTTING,
    SNAPSHOT_COMPLETE,
    APPLYING,
    SETTLING,
    VERIFYING,
    APPLIED,
    ROLLING_BACK,
    ROLLED_BACK,
    UNDOING,
    UNDONE,
    RECOVERY_REQUIRED
}
