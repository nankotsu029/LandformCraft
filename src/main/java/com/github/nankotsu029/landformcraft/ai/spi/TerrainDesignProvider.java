package com.github.nankotsu029.landformcraft.ai.spi;

import java.util.concurrent.CompletableFuture;

/**
 * Converts validated human input into a structured intent; it never emits raw block lists or executable code.
 * Implementations must return without blocking the caller, avoid the common pool, apply transport timeouts, and
 * propagate cancellation of the returned future to the underlying request when the transport supports it.
 * Image-capable implementations receive only verified, normalized handles and never raw filesystem access.
 */
public interface TerrainDesignProvider extends AutoCloseable {
    String id();

    CompletableFuture<TerrainDesignResult> design(TerrainDesignRequest request);

    /** Whether this provider transmits prepared image bytes outside the local process. */
    default boolean submitsReferenceImages() {
        return false;
    }

    @Override
    default void close() {
        // Most fixture/import providers own no resources. Network providers override this method.
    }
}
