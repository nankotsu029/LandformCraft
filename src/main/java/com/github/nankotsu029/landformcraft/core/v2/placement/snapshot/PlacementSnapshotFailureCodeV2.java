package com.github.nankotsu029.landformcraft.core.v2.placement.snapshot;

/** Canonical Release 2 snapshot-all failure classification (V2-6-04). */
public enum PlacementSnapshotFailureCodeV2 {
    STATE_MISMATCH,
    BINDING_MISMATCH,
    RESERVATION_MISSING,
    DISK_SHORTAGE,
    DISK_BUDGET_EXCEEDED,
    SNAPSHOT_BUDGET_EXCEEDED,
    PALETTE_BUDGET_EXCEEDED,
    GATEWAY_CONTRACT_VIOLATION,
    WORLD_DRIFT,
    SNAPSHOT_IO_FAILURE,
    SNAPSHOT_CORRUPT,
    FILE_SET_MISMATCH,
    PATH_UNSAFE,
    SNAPSHOT_IN_PROGRESS,
    ALREADY_PUBLISHED,
    CANCELLED
}
