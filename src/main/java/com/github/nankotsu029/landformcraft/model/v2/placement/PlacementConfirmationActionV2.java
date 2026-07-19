package com.github.nankotsu029.landformcraft.model.v2.placement;

/**
 * Confirmation token action bound into a Release 2 placement plan/journal. Distinct from v1
 * {@code ConfirmationAction}. V2-6-03 issues {@link #APPLY}; other actions remain for later Tasks.
 */
public enum PlacementConfirmationActionV2 {
    NONE,
    APPLY,
    UNDO,
    RECOVERY_ROLLBACK,
    RECOVERY_ACCEPT
}
