package com.github.nankotsu029.landformcraft.ai.spi;

import com.github.nankotsu029.landformcraft.model.TerrainIntent;

import java.time.Instant;
import java.util.Objects;

public record TerrainDesignResult(
        TerrainIntent intent,
        String providerId,
        String modelId,
        Instant createdAt
) {
    public TerrainDesignResult {
        Objects.requireNonNull(intent, "intent");
        providerId = requireNonBlank(providerId, "providerId");
        modelId = requireNonBlank(modelId, "modelId");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
