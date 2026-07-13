package com.github.nankotsu029.landformcraft.model;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
    private static final Pattern MINECRAFT_VERSION = Pattern.compile("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?");

    public ExportManifest {
        formatVersion = ModelValidation.requireSchemaVersion(formatVersion, "formatVersion");
        generatorVersion = ModelValidation.requireNonBlank(generatorVersion, "generatorVersion");
        minecraftVersion = ModelValidation.requireNonBlank(minecraftVersion, "minecraftVersion");
        requestId = ModelValidation.requireSlug(requestId, "requestId");
        Objects.requireNonNull(anchor, "anchor");
        if (!MINECRAFT_VERSION.matcher(minecraftVersion).matches()
                || width < 1 || width > 1_000 || length < 1 || length > 1_000
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
        Set<String> ids = new HashSet<>();
        Set<String> files = new HashSet<>();
        Set<Long> coordinates = new HashSet<>();
        for (ManifestTile tile : tiles) {
            long coordinate = ((long) tile.zIndex() << 32) | Integer.toUnsignedLong(tile.xIndex());
            int expectedOriginX = tile.xIndex() * tileSize;
            int expectedOriginZ = tile.zIndex() * tileSize;
            int expectedWidth = Math.min(tileSize, width - expectedOriginX);
            int expectedLength = Math.min(tileSize, length - expectedOriginZ);
            if (!ids.add(tile.id()) || !files.add(tile.file().toLowerCase(java.util.Locale.ROOT))
                    || !coordinates.add(coordinate)
                    || tile.xIndex() >= tileCountX || tile.zIndex() >= tileCountZ
                    || tile.originX() != expectedOriginX || tile.originY() != minY
                    || tile.originZ() != expectedOriginZ
                    || tile.width() != expectedWidth || tile.length() != expectedLength
                    || tile.minY() != minY || tile.maxY() != maxY) {
                throw new IllegalArgumentException("manifest contains duplicate, missing, or misplaced tiles");
            }
        }
    }
}
