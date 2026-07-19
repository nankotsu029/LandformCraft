package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Completion receipt for one bounded verify-read slice. */
public record PlacementVerifyReadSliceReceiptV2(
        UUID operationId,
        int sliceSequence,
        boolean schedulerAccepted,
        boolean mainThread,
        List<String> blockStates
) {
    public PlacementVerifyReadSliceReceiptV2 {
        Objects.requireNonNull(operationId, "operationId");
        if (sliceSequence < 0) {
            throw new IllegalArgumentException("sliceSequence must be non-negative");
        }
        Objects.requireNonNull(blockStates, "blockStates");
        blockStates = List.copyOf(blockStates);
        for (String state : blockStates) {
            if (state == null || state.isBlank() || state.length() > 256) {
                throw new IllegalArgumentException("invalid verify-read block state");
            }
        }
    }

    public void requireMatches(PlacementVerifyReadSliceV2 slice) {
        Objects.requireNonNull(slice, "slice");
        if (!operationId.equals(slice.operationId()) || sliceSequence != slice.sliceSequence()) {
            throw new IllegalArgumentException("verify-read receipt does not match the request");
        }
        if (!schedulerAccepted) {
            throw new IllegalArgumentException("verify-read slice was not scheduler-accepted");
        }
        if (!mainThread) {
            throw new IllegalArgumentException("verify-read must execute on the Paper main thread");
        }
        if (blockStates.size() != slice.blockCount()) {
            throw new IllegalArgumentException("verify-read receipt block count mismatch");
        }
    }
}
