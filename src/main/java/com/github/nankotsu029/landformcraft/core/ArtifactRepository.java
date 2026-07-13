package com.github.nankotsu029.landformcraft.core;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous storage boundary; implementations must confine paths to their configured artifact root. */
public interface ArtifactRepository {
    CompletableFuture<Void> write(String jobId, String relativePath, byte[] content);

    CompletableFuture<Optional<byte[]>> read(String jobId, String relativePath);
}
