package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record PlacementPlan(
        int schemaVersion,
        UUID placementId,
        String releaseDirectory,
        String releaseChecksum,
        String requestId,
        UUID worldId,
        String worldName,
        ActorIdentity actor,
        int targetX,
        int targetY,
        int targetZ,
        int minimumX,
        int minimumY,
        int minimumZ,
        int maximumX,
        int maximumY,
        int maximumZ,
        Instant createdAt
) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public PlacementPlan {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        Objects.requireNonNull(placementId, "placementId");
        releaseDirectory = ModelValidation.requireSafeRelativePath(releaseDirectory, "releaseDirectory");
        releaseChecksum = ModelValidation.requireNonBlank(releaseChecksum, "releaseChecksum");
        requestId = ModelValidation.requireSlug(requestId, "requestId");
        Objects.requireNonNull(worldId, "worldId");
        worldName = ModelValidation.requireNonBlank(worldName, "worldName", 128);
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!SHA_256.matcher(releaseChecksum).matches()
                || minimumX != targetX || minimumY != targetY || minimumZ != targetZ
                || maximumX < minimumX || maximumY < minimumY || maximumZ < minimumZ) {
            throw new IllegalArgumentException("invalid placement plan");
        }
    }
}
