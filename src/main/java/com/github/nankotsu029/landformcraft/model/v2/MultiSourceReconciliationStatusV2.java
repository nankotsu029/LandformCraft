package com.github.nankotsu029.landformcraft.model.v2;

/** Overall reconciliation outcome. Unresolved statuses fail closed — no invented merge. */
public enum MultiSourceReconciliationStatusV2 {
    RESOLVED,
    UNRESOLVED_HARD_CONFLICT,
    UNRESOLVED_SOFT_PEER_CONFLICT
}
