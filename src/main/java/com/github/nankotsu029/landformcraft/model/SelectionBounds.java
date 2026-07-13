package com.github.nankotsu029.landformcraft.model;

public record SelectionBounds(
        String worldName,
        int minimumX,
        int minimumY,
        int minimumZ,
        int maximumX,
        int maximumY,
        int maximumZ
) {
    public SelectionBounds {
        worldName = ModelValidation.requireNonBlank(worldName, "worldName", 128);
        if (maximumX < minimumX || maximumY < minimumY || maximumZ < minimumZ) {
            throw new IllegalArgumentException("invalid selection bounds");
        }
    }
}
