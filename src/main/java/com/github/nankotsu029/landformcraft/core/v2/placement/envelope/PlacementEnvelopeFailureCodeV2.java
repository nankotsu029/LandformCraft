package com.github.nankotsu029.landformcraft.core.v2.placement.envelope;

/** Stable failure codes for Release 2 mutation／effect envelope admission. */
public enum PlacementEnvelopeFailureCodeV2 {
    UNKNOWN_PHYSICS_CLASS,
    TILE_ORDER_MISMATCH,
    MUTATION_OUTSIDE_TILE_CORE,
    WORLD_BOUNDS_OVERFLOW,
    Y_OVERFLOW,
    UNDER_APPROXIMATION,
    ENVELOPE_COUNT_EXCEEDED,
    EFFECT_VOLUME_EXCEEDED,
    DISK_ESTIMATE_EXCEEDED,
    PLACEMENT_PLAN_BINDING_MISMATCH
}
