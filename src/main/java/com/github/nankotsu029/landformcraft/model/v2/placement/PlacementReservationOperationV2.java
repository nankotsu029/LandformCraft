package com.github.nankotsu029.landformcraft.model.v2.placement;

/**
 * Confirmation operation bound into Release 2 reservation confirmation hashes.
 * Distinct from v1 {@code PlacementOperation}; V2-6-03 issues {@link #APPLY} only.
 */
public enum PlacementReservationOperationV2 {
    APPLY,
    UNDO,
    RECOVERY_ROLLBACK,
    RECOVERY_ACCEPT
}
