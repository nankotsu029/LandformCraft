package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record StructurePlan(
        String assetId,
        String assetChecksum,
        String minecraftVersion,
        StructureType type,
        int anchorX,
        int anchorY,
        int anchorZ,
        QuarterTurn rotation,
        int sizeX,
        int sizeY,
        int sizeZ,
        boolean terrainFollowing,
        boolean preferredZoneFallback
) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MINECRAFT_VERSION = Pattern.compile("1\\.21\\.[0-9]{1,2}");

    public StructurePlan {
        assetId = ModelValidation.requireSlug(assetId, "assetId");
        assetChecksum = ModelValidation.requireNonBlank(assetChecksum, "assetChecksum");
        minecraftVersion = ModelValidation.requireNonBlank(minecraftVersion, "minecraftVersion", 32);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rotation, "rotation");
        if (!SHA_256.matcher(assetChecksum).matches()) {
            throw new IllegalArgumentException("assetChecksum must be a lowercase SHA-256 value");
        }
        if (!MINECRAFT_VERSION.matcher(minecraftVersion).matches()) {
            throw new IllegalArgumentException("unsupported minecraftVersion format");
        }
        if (anchorX < 0 || anchorZ < 0 || sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("invalid structure anchor or size");
        }
    }
}
