package com.github.nankotsu029.landformcraft.model.v2.placement;

/**
 * Safe operator action offered by a Release 2 recovery diagnosis (V2-6-10). World-mutating
 * actions ({@link #ROLLBACK}, {@link #ACCEPT}) always require an actor-bound one-time
 * confirmation; {@link #CLEANUP_SNAPSHOTS} requires a matching dry-run cleanup plan.
 */
public enum PlacementRecoveryActionV2 {
    NONE,
    RELEASE_LEASES,
    ROLLBACK,
    ACCEPT,
    CLEANUP_SNAPSHOTS
}
