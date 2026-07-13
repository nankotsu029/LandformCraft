package com.github.nankotsu029.landformcraft.ai.spi;

import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.ProviderUsage;

import java.time.Instant;
import java.util.Objects;

public record TerrainDesignResult(
        TerrainIntent intent,
        String providerId,
        String modelId,
        String promptVersion,
        String responseId,
        ProviderUsage usage,
        int attempts,
        Instant createdAt
) {
    public TerrainDesignResult {
        Objects.requireNonNull(intent, "intent");
        providerId = requireNonBlank(providerId, "providerId");
        modelId = requireNonBlank(modelId, "modelId");
        promptVersion = requireNonBlank(promptVersion, "promptVersion");
        responseId = requireNonBlank(responseId, "responseId");
        Objects.requireNonNull(usage, "usage");
        if (attempts < 1 || attempts > 10) {
            throw new IllegalArgumentException("attempts must be between 1 and 10");
        }
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
