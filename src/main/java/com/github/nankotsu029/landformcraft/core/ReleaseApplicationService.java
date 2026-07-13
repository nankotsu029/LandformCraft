package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.ReleaseArtifacts;
import com.github.nankotsu029.landformcraft.format.ReleasePublisher;
import com.github.nankotsu029.landformcraft.format.ReleaseVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Offline export pipeline sharing the same deterministic generation service as preview generation. */
public final class ReleaseApplicationService {
    private final GenerationExecutors executors;
    private final GenerationApplicationService generationService;
    private final ReleasePublisher publisher;

    public ReleaseApplicationService(GenerationExecutors executors) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.generationService = new GenerationApplicationService(executors);
        this.publisher = new ReleasePublisher();
    }

    public CompletableFuture<ReleaseArtifacts> export(
            Path requestPath,
            Path intentPath,
            Path exportsRoot,
            int candidateIndex
    ) {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(intentPath, "intentPath");
        Objects.requireNonNull(exportsRoot, "exportsRoot");
        Path work;
        try {
            Path absoluteRoot = exportsRoot.toAbsolutePath().normalize();
            Files.createDirectories(absoluteRoot);
            work = absoluteRoot.resolve(".generation-" + UUID.randomUUID());
            Files.createDirectories(work);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        CancellationToken token = () -> Thread.currentThread().isInterrupted();
        CompletableFuture<ReleaseArtifacts> future = generationService
                .generatePreview(requestPath, intentPath, work.resolve("previews"), candidateIndex)
                .thenCompose(outcome -> executors.supplyIo(() -> {
                    try {
                        return publisher.publish(requestPath, intentPath, exportsRoot, outcome, token);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                }));
        return future.whenComplete((ignored, failure) -> {
            try {
                ReleaseVerifier.deleteTree(work);
            } catch (IOException cleanupFailure) {
                if (failure != null) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        });
    }
}
