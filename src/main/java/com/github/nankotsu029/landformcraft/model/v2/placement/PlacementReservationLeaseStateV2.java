package com.github.nankotsu029.landformcraft.model.v2.placement;

/** Lease lifecycle for a Release 2 region/disk reservation. Distinct from v1 {@code ReservationState}. */
public enum PlacementReservationLeaseStateV2 {
    PLANNED,
    ACTIVE,
    RECOVERY_REQUIRED
}
