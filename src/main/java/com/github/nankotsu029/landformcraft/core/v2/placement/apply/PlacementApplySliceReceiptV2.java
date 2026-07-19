package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import java.util.Objects;
import java.util.UUID;

/** Completion evidence returned only after a scheduler-accepted slice and its resources close. */
public record PlacementApplySliceReceiptV2(
        UUID operationId,
        String tileId,
        long sliceSequence,
        int appliedMutations,
        boolean schedulerAccepted,
        boolean executedOnMainThread,
        boolean resourcesClosed
) {
    public PlacementApplySliceReceiptV2 {
        Objects.requireNonNull(operationId, "operationId");
        if (tileId == null || tileId.isBlank() || sliceSequence < 0 || appliedMutations < 1) {
            throw new IllegalArgumentException("invalid placement apply receipt");
        }
    }

    public void requireMatches(PlacementApplySliceV2 slice) {
        Objects.requireNonNull(slice, "slice");
        if (!operationId.equals(slice.operationId()) || !tileId.equals(slice.tileId())
                || sliceSequence != slice.sliceSequence()
                || appliedMutations != slice.mutations().size()
                || !schedulerAccepted || !executedOnMainThread || !resourcesClosed) {
            throw new IllegalArgumentException(
                    "gateway receipt is incomplete or does not match the accepted slice");
        }
    }
}
