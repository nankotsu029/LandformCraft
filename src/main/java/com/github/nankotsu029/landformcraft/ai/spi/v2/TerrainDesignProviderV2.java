package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;

import java.util.concurrent.CompletableFuture;

/**
 * Versioned design provider. Implementations return {@code TerrainIntentV2} only and never emit
 * block lists or executable code. Image-capable adapters receive verified handles only.
 */
public interface TerrainDesignProviderV2 extends AutoCloseable {
    String id();

    DesignPathKindV2 path();

    CompletableFuture<TerrainDesignResultV2> design(TerrainDesignRequestV2 request);

    default boolean submitsReferenceImages() {
        return false;
    }

    @Override
    default void close() {
    }
}
