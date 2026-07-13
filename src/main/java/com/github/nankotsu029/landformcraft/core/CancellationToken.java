package com.github.nankotsu029.landformcraft.core;

import java.util.concurrent.CancellationException;

/** Cooperative cancellation checked inside CPU-heavy generation loops. */
@FunctionalInterface
public interface CancellationToken {
    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new CancellationException("generation task was cancelled");
        }
    }
}
