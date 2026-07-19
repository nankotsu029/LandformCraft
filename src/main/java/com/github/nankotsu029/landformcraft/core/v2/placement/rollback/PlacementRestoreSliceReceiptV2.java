package com.github.nankotsu029.landformcraft.core.v2.placement.rollback;

import java.util.Objects;
import java.util.UUID;

/** Completion evidence returned only after a scheduler-accepted restore slice closed its resources. */
public record PlacementRestoreSliceReceiptV2(
        UUID operationId,
        String tileId,
        long sliceSequence,
        int restoredBlocks,
        boolean schedulerAccepted,
        boolean executedOnMainThread,
        boolean resourcesClosed
) {
    public PlacementRestoreSliceReceiptV2 {
        Objects.requireNonNull(operationId, "operationId");
        if (tileId == null || tileId.isBlank() || sliceSequence < 0 || restoredBlocks < 1) {
            throw new IllegalArgumentException("invalid placement restore receipt");
        }
    }

    public void requireMatches(PlacementRestoreSliceV2 slice) {
        Objects.requireNonNull(slice, "slice");
        if (!operationId.equals(slice.operationId()) || !tileId.equals(slice.tileId())
                || sliceSequence != slice.sliceSequence()
                || restoredBlocks != slice.blocks().size()
                || !schedulerAccepted || !executedOnMainThread || !resourcesClosed) {
            throw new IllegalArgumentException(
                    "gateway receipt is incomplete or does not match the accepted restore slice");
        }
    }
}
