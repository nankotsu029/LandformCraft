package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;

public record WorldBlueprint(
        int schemaVersion,
        String requestId,
        GenerationBounds bounds,
        TerrainIntent intent,
        long seed,
        int tileSize,
        int logicalResolution,
        String generatorVersion
) {
    public WorldBlueprint {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        requestId = ModelValidation.requireSlug(requestId, "requestId");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(intent, "intent");
        if (tileSize < 32 || tileSize > 256 || Integer.bitCount(tileSize) != 1) {
            throw new IllegalArgumentException("tileSize must be a power of two between 32 and 256");
        }
        if (logicalResolution < 16 || logicalResolution > 512 || Integer.bitCount(logicalResolution) != 1) {
            throw new IllegalArgumentException("logicalResolution must be a power of two between 16 and 512");
        }
        generatorVersion = ModelValidation.requireNonBlank(generatorVersion, "generatorVersion");
    }

    public int tileCountX() {
        return Math.ceilDiv(bounds.width(), tileSize);
    }

    public int tileCountZ() {
        return Math.ceilDiv(bounds.length(), tileSize);
    }
}
