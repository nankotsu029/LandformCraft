package com.github.nankotsu029.landformcraft.core.v2.placement.safety;

/**
 * Hard-failure codes for Release 2 fluid／gravity／neighbor containment preflight (V2-6-05).
 */
public enum PlacementContainmentFailureCodeV2 {
    JOURNAL_STATE_INVALID,
    BINDING_MISMATCH,
    POLICY_MISMATCH,
    UNKNOWN_POLICY_VERSION,
    UNKNOWN_CATALOG_VERSION,
    UNKNOWN_BLOCK_STATE,
    UNSUPPORTED_BLOCK_STATE,
    PHYSICS_CLASS_UNDERSTATED,
    UNCONTAINED_FLUID,
    UNCONTAINED_GRAVITY,
    NO_GRAVITY_SUPPORT,
    UNCONTAINED_NEIGHBOR,
    ENVELOPE_GAP,
    SCAN_BUDGET_EXCEEDED,
    CACHE_BUDGET_EXCEEDED,
    BFS_BUDGET_EXCEEDED,
    CANONICAL_BUDGET_EXCEEDED,
    WORLD_VIEW_FAILURE
}
