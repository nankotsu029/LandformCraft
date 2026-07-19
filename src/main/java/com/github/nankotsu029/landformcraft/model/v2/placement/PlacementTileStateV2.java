package com.github.nankotsu029.landformcraft.model.v2.placement;

/**
 * Per-tile checkpoint state for Release 2 journals. Distinct from v1 {@code PlacementTileState}.
 * V2-6-01 only emits {@link #PENDING}.
 */
public enum PlacementTileStateV2 {
    PENDING,
    SNAPSHOTTED,
    APPLIED,
    VERIFIED,
    RESTORED
}
