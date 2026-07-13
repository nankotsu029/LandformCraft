package com.github.nankotsu029.landformcraft.ai.http;

import com.github.nankotsu029.landformcraft.model.ProviderUsage;

import java.util.Objects;

public record ProviderPayload(String responseId, String modelId, String intentJson, ProviderUsage usage) {
    public ProviderPayload {
        responseId = requireNonBlank(responseId, "responseId");
        modelId = requireNonBlank(modelId, "modelId");
        intentJson = requireNonBlank(intentJson, "intentJson");
        Objects.requireNonNull(usage, "usage");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
