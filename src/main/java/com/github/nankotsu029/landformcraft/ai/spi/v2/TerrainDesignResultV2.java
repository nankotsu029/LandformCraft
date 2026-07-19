package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;

import com.github.nankotsu029.landformcraft.model.ProviderUsage;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record TerrainDesignResultV2(
        TerrainIntentV2 intent,
        String providerId,
        String modelId,
        String promptVersion,
        String responseId,
        ProviderUsage usage,
        int attempts,
        Instant createdAt,
        Set<DesignCapabilityV2> negotiatedCapabilities,
        String capabilityCatalogVersion
) {
    public TerrainDesignResultV2 {
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
        Objects.requireNonNull(negotiatedCapabilities, "negotiatedCapabilities");
        negotiatedCapabilities = Set.copyOf(negotiatedCapabilities);
        if (negotiatedCapabilities.isEmpty()) {
            throw new IllegalArgumentException("negotiatedCapabilities must not be empty");
        }
        capabilityCatalogVersion = requireNonBlank(capabilityCatalogVersion, "capabilityCatalogVersion");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
