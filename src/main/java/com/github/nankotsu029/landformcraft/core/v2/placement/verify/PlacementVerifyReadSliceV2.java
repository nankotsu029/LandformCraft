package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

import java.util.Objects;
import java.util.UUID;

import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

/**
 * One bounded verify-read slice covering {@code blockCount} positions of the effect envelope
 * starting at canonical linear {@code startIndex} (X-fastest→Z→Y).
 */
public record PlacementVerifyReadSliceV2(
        UUID operationId,
        UUID worldId,
        WorldAabbV2 effectEnvelope,
        long startIndex,
        int blockCount,
        int sliceSequence
) {
    public PlacementVerifyReadSliceV2 {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(effectEnvelope, "effectEnvelope");
        if (startIndex < 0
                || blockCount < 1
                || sliceSequence < 0
                || startIndex + blockCount > effectEnvelope.volumeBlocks()) {
            throw new IllegalArgumentException("invalid verify read slice bounds");
        }
    }
}
