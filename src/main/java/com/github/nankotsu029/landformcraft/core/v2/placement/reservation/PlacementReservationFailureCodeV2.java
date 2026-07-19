package com.github.nankotsu029.landformcraft.core.v2.placement.reservation;

/** Stable failure codes for Release 2 reservation／confirmation admission. */
public enum PlacementReservationFailureCodeV2 {
    REGION_OVERLAP,
    DISK_SHORTAGE,
    ENTRY_BUDGET_EXCEEDED,
    ACTOR_MISMATCH,
    TARGET_MISMATCH,
    CHECKSUM_MISMATCH,
    CONFIRMATION_EXPIRED,
    CONFIRMATION_REPLAY,
    CONFIRMATION_INVALID,
    STATE_MISMATCH,
    PARTIAL_RESERVATION_ROLLED_BACK
}
