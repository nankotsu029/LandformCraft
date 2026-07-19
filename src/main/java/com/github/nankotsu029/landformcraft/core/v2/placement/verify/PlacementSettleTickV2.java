package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

import java.util.Objects;
import java.util.UUID;

import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

/** One bounded settle tick request dispatched through the world gateway. */
public record PlacementSettleTickV2(
        UUID operationId,
        UUID worldId,
        WorldAabbV2 effectEnvelope,
        int tickIndex
) {
    public PlacementSettleTickV2 {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(effectEnvelope, "effectEnvelope");
        if (tickIndex < 0) {
            throw new IllegalArgumentException("tickIndex must be non-negative");
        }
    }
}
