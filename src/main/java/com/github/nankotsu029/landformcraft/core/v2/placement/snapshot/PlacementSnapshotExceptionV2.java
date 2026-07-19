package com.github.nankotsu029.landformcraft.core.v2.placement.snapshot;

import java.util.Objects;

/** Snapshot-all failure carrying a canonical {@link PlacementSnapshotFailureCodeV2}. */
public final class PlacementSnapshotExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final PlacementSnapshotFailureCodeV2 failureCode;

    public PlacementSnapshotExceptionV2(PlacementSnapshotFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public PlacementSnapshotExceptionV2(
            PlacementSnapshotFailureCodeV2 failureCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public PlacementSnapshotFailureCodeV2 failureCode() {
        return failureCode;
    }
}
