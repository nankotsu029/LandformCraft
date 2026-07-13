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
        String checksum,
        String terrainChecksum,
        long blockCount,
        ManifestAirPolicy airPolicy,
        ManifestTileStatus status
) {
    private static final Pattern ID = Pattern.compile("tile-[0-9]{2,}-[0-9]{2,}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ManifestTile {
        id = ModelValidation.requireNonBlank(id, "id");
        file = ModelValidation.requireSafeRelativePath(file, "file");
        checksum = ModelValidation.requireNonBlank(checksum, "checksum");
        terrainChecksum = ModelValidation.requireNonBlank(terrainChecksum, "terrainChecksum");
        java.util.Objects.requireNonNull(airPolicy, "airPolicy");
        java.util.Objects.requireNonNull(status, "status");
        if (!ID.matcher(id).matches()
                || xIndex < 0 || zIndex < 0 || originX < 0 || originZ < 0
                || width < 1 || width > 256 || length < 1 || length > 256
                || minY >= maxY || (long) maxY - minY + 1L > 512L
                || blockCount != (long) width * length * (maxY - minY + 1L)
                || !SHA_256.matcher(checksum).matches() || !SHA_256.matcher(terrainChecksum).matches()
                || airPolicy != ManifestAirPolicy.INCLUDED
                || status != ManifestTileStatus.READY) {
            throw new IllegalArgumentException("invalid manifest tile");
        }
    }
}
