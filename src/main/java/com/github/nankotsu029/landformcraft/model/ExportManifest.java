package com.github.nankotsu029.landformcraft.model;

import java.util.List;
import java.util.Objects;

public record ExportManifest(
        int formatVersion,
        String generatorVersion,
        String minecraftVersion,
        String requestId,
        int width,
        int length,
        int minY,
        int maxY,
        int tileSize,
        int tileCountX,
        int tileCountZ,
        ManifestAnchor anchor,
        long seed,
        List<ManifestTile> tiles
) {
    public ExportManifest {
        formatVersion = ModelValidation.requireSchemaVersion(formatVersion, "formatVersion");
        generatorVersion = ModelValidation.requireNonBlank(generatorVersion, "generatorVersion");
        minecraftVersion = ModelValidation.requireNonBlank(minecraftVersion, "minecraftVersion");
        requestId = ModelValidation.requireSlug(requestId, "requestId");
        Objects.requireNonNull(anchor, "anchor");
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000
                || minY >= maxY
                || tileSize < 32 || tileSize > 256 || Integer.bitCount(tileSize) != 1
                || tileCountX != Math.ceilDiv(width, tileSize)
                || tileCountZ != Math.ceilDiv(length, tileSize)) {
            throw new IllegalArgumentException("manifest dimensions or tile counts are invalid");
        }
        tiles = ModelValidation.immutableList(tiles, "tiles", 1_024);
        if (tiles.size() != tileCountX * tileCountZ) {
            throw new IllegalArgumentException("manifest tile count does not match its grid");
        }
    }
}
