package com.github.nankotsu029.landformcraft.model;

import java.util.regex.Pattern;

public record TilePlan(
        String id,
        int xIndex,
        int zIndex,
        int originX,
        int originZ,
        int width,
        int length,
        int margin,
        String checksum
) {
    private static final Pattern ID = Pattern.compile("tile-[0-9]{2,}-[0-9]{2,}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public TilePlan {
        id = ModelValidation.requireNonBlank(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid tile id: " + id);
        }
        if (xIndex < 0 || zIndex < 0 || originX < 0 || originZ < 0) {
            throw new IllegalArgumentException("tile indexes and origins must not be negative");
        }
        if (width < 1 || length < 1 || margin < 0 || margin > 128) {
            throw new IllegalArgumentException("invalid tile dimensions or margin");
        }
        checksum = ModelValidation.requireNonBlank(checksum, "checksum");
        if (!SHA_256.matcher(checksum).matches()) {
            throw new IllegalArgumentException("checksum must be a lowercase SHA-256 value");
        }
    }
}
