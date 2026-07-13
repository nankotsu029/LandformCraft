package com.github.nankotsu029.landformcraft.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict release catalog containing exactly the structure assets used by placements. */
public record RequiredAssets(int schemaVersion, String minecraftVersion, List<RequiredAsset> assets) {
    public RequiredAssets {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        minecraftVersion = ModelValidation.requireNonBlank(minecraftVersion, "minecraftVersion", 32);
        assets = ModelValidation.immutableList(assets, "assets", 64);
        Set<String> ids = new HashSet<>();
        Set<String> files = new HashSet<>();
        for (RequiredAsset asset : assets) {
            if (!minecraftVersion.equals(asset.minecraftVersion())) {
                throw new IllegalArgumentException("asset minecraftVersion does not match catalog");
            }
            if (!ids.add(asset.assetId()) || !files.add(asset.file())) {
                throw new IllegalArgumentException("asset ids and files must be unique");
            }
        }
    }
}
