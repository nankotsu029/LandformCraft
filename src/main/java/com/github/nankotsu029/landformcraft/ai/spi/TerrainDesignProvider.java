package com.github.nankotsu029.landformcraft.ai.spi;

import java.util.concurrent.CompletableFuture;

/**
 * Converts validated human input into a structured intent; it never emits raw block lists or executable code.
 * Implementations must return without blocking the caller, avoid the common pool, apply transport timeouts, and
 * propagate cancellation of the returned future to the underlying request when the transport supports it.
 * Until Phase 5 introduces verified image handles, implementations must reject requests containing raw image paths.
 */
public interface TerrainDesignProvider extends AutoCloseable {
    String id();

    CompletableFuture<TerrainDesignResult> design(TerrainDesignRequest request);

    @Override
    default void close() {
        // Most fixture/import providers own no resources. Network providers override this method.
    }
}
