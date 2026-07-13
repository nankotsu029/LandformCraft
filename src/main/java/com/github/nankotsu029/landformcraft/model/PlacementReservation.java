package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable inclusive world-region lease used to reject concurrent overlapping mutations. */
public record PlacementReservation(
        UUID placementId,
        UUID worldId,
        int minimumX,
        int minimumY,
        int minimumZ,
        int maximumX,
        int maximumY,
        int maximumZ,
        PlacementOperation operation,
        ActorIdentity actor,
        Instant createdAt,
        Instant expiresAt,
        ReservationState state
) {
    public PlacementReservation {
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(state, "state");
        if (maximumX < minimumX || maximumY < minimumY || maximumZ < minimumZ
                || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("invalid placement reservation");
        }
    }

    public boolean overlaps(PlacementReservation other) {
        Objects.requireNonNull(other, "other");
        return worldId.equals(other.worldId)
                && minimumX <= other.maximumX && maximumX >= other.minimumX
                && minimumY <= other.maximumY && maximumY >= other.minimumY
                && minimumZ <= other.maximumZ && maximumZ >= other.minimumZ;
    }
}
