package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.Objects;

/** Immutable identity record published with a validated custom asset. */
public record CustomAssetCatalogEntry(
        int schemaVersion,
        CustomAssetMetadata metadata,
        int width,
        int height,
        int length,
        int blockCount,
        String artifactChecksum,
        String semanticChecksum,
        Instant importedAt
) {
    public CustomAssetCatalogEntry {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        Objects.requireNonNull(metadata, "metadata");
        if (width < 1 || width > 64 || height < 1 || height > 64 || length < 1 || length > 64
                || blockCount < 1 || blockCount > 32_768) {
            throw new IllegalArgumentException("custom asset dimensions or block count exceed beta limits");
        }
        if (metadata.anchorX() < 0 || metadata.anchorX() >= width
                || metadata.anchorY() < 0 || metadata.anchorY() >= height
                || metadata.anchorZ() < 0 || metadata.anchorZ() >= length) {
            throw new IllegalArgumentException("custom asset anchor is outside its dimensions");
        }
        artifactChecksum = checksum(artifactChecksum, "artifactChecksum");
        semanticChecksum = checksum(semanticChecksum, "semanticChecksum");
        Objects.requireNonNull(importedAt, "importedAt");
    }

    private static String checksum(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 checksum");
        }
        return value;
    }
}
