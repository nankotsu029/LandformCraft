package com.github.nankotsu029.landformcraft.model.v2.placement;

/**
 * Physics-sensitive content classes that expand mutation AABBs into effect envelopes.
 * Unknown values are rejected by Schema／codec; there is no fallback radius.
 */
public enum PlacementPhysicsClassV2 {
    SOLID,
    AIR,
    FLUID,
    GRAVITY,
    NEIGHBOR
}
