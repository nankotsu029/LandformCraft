package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable capacity reservation scoped to the actual FileStore that will receive snapshots. */
public record DiskReservation(
        UUID placementId,
        String fileStoreKey,
        long reservedBytes,
        Instant createdAt,
        Instant expiresAt
) {
    public DiskReservation {
        Objects.requireNonNull(placementId, "placementId");
        fileStoreKey = ModelValidation.requireNonBlank(fileStoreKey, "fileStoreKey", 512);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (reservedBytes < 0 || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("invalid disk reservation");
        }
    }
}
