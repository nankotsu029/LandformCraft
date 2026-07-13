package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.DesignArtifacts;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public record DesignJobHandle(UUID jobId, CompletableFuture<DesignArtifacts> completion) {
    public DesignJobHandle {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(completion, "completion");
    }

    public boolean cancel() {
        return completion.cancel(true);
    }
}
