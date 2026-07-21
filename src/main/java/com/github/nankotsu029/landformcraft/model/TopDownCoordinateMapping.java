package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;

/** Maps normalized image coordinates u/v in [0,1] to release-local x/z coordinates. */
public record TopDownCoordinateMapping(
        ImageMapOrigin origin,
        ImageMapAxis horizontalAxis,
        ImageMapAxis verticalAxis,
        int targetWidth,
        int targetLength
) {
    public TopDownCoordinateMapping {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(horizontalAxis, "horizontalAxis");
        Objects.requireNonNull(verticalAxis, "verticalAxis");
        if (origin != ImageMapOrigin.TOP_LEFT
                || horizontalAxis != ImageMapAxis.POSITIVE_X_EAST
                || verticalAxis != ImageMapAxis.POSITIVE_Z_SOUTH) {
            throw new IllegalArgumentException("top-down mapping must use north-up, east-right coordinates");
        }
        if (targetWidth < 1 || targetWidth > 1_024 || targetLength < 1 || targetLength > 1_024) {
            throw new IllegalArgumentException("top-down target dimensions must be between 1 and 1024");
        }
    }
}
