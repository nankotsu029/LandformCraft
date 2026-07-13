package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;
import java.util.UUID;

public record WorldDescriptor(
        UUID worldId,
        String worldName,
        int minY,
        int maxY,
        int borderMinX,
        int borderMaxX,
        int borderMinZ,
        int borderMaxZ
) {
    public WorldDescriptor {
        Objects.requireNonNull(worldId, "worldId");
        worldName = ModelValidation.requireNonBlank(worldName, "worldName", 128);
        if (minY >= maxY || borderMinX > borderMaxX || borderMinZ > borderMaxZ) {
            throw new IllegalArgumentException("invalid world descriptor bounds");
        }
    }
}
