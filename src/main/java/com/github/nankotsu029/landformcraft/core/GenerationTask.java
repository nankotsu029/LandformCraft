package com.github.nankotsu029.landformcraft.core;

/** A CPU-heavy task that must check its token at bounded intervals. */
@FunctionalInterface
public interface GenerationTask<T> {
    T run(CancellationToken cancellationToken);
}
