package com.github.nankotsu029.landformcraft.model;

import java.util.regex.Pattern;

public record ManifestTile(
        String id,
        int xIndex,
        int zIndex,
        int originX,
        int originY,
        int originZ,
        int width,
        int length,
        int minY,
        int maxY,
        String file,
        String checksum
) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ManifestTile {
        id = ModelValidation.requireNonBlank(id, "id");
        file = ModelValidation.requireSafeRelativePath(file, "file");
        checksum = ModelValidation.requireNonBlank(checksum, "checksum");
        if (xIndex < 0 || zIndex < 0 || originX < 0 || originZ < 0
                || width < 1 || length < 1 || minY >= maxY
                || !SHA_256.matcher(checksum).matches()) {
            throw new IllegalArgumentException("invalid manifest tile");
        }
    }
}
