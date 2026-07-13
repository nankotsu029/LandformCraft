package com.github.nankotsu029.landformcraft.ai.spi;

import com.github.nankotsu029.landformcraft.model.GenerationRequest;

import java.util.Objects;

public record TerrainDesignRequest(GenerationRequest generationRequest) {
    public TerrainDesignRequest {
        Objects.requireNonNull(generationRequest, "generationRequest");
        if (!generationRequest.images().isEmpty()) {
            throw new IllegalArgumentException(
                    "raw image paths are not accepted; Phase 5 will introduce verified image handles"
            );
        }
    }
}
