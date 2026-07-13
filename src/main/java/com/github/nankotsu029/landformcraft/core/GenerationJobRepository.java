package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.GenerationJobSnapshot;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface GenerationJobRepository {
    CompletableFuture<Void> save(GenerationJobSnapshot snapshot);

    CompletableFuture<Optional<GenerationJobSnapshot>> find(String jobId);
}
