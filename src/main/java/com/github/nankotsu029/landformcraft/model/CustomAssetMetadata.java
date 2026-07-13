package com.github.nankotsu029.landformcraft.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Restricted metadata supplied alongside a custom Sponge v3 schematic. */
public record CustomAssetMetadata(
        int schemaVersion,
        String assetId,
        StructureType type,
        String minecraftVersion,
        int anchorX,
        int anchorY,
        int anchorZ,
        List<QuarterTurn> allowedRotations,
        CustomAssetPlacementKind placementKind,
        int maximumSlope,
        boolean waterAllowed
) {
    public CustomAssetMetadata {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        assetId = ModelValidation.requireSlug(assetId, "assetId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        if (!"1.21.11".equals(minecraftVersion)) {
            throw new IllegalArgumentException("custom asset Minecraft version must be 1.21.11");
        }
        allowedRotations = List.copyOf(allowedRotations);
        if (allowedRotations.isEmpty() || allowedRotations.size() > 4
                || new HashSet<>(allowedRotations).size() != allowedRotations.size()) {
            throw new IllegalArgumentException("allowedRotations must contain 1..4 unique rotations");
        }
        Objects.requireNonNull(placementKind, "placementKind");
        if (maximumSlope < 0 || maximumSlope > 32) {
            throw new IllegalArgumentException("maximumSlope must be between 0 and 32");
        }
    }
}
