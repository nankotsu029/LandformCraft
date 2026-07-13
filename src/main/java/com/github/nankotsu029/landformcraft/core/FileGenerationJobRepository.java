package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.GenerationJobSnapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.time.Clock;
import com.github.nankotsu029.landformcraft.model.GenerationStage;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Atomic, schema-validated JSON job snapshots. */
public final class FileGenerationJobRepository implements GenerationJobRepository {
    private final Path root;
    private final GenerationExecutors executors;
    private final LandformDataCodec codec = new LandformDataCodec();

    public FileGenerationJobRepository(Path root, GenerationExecutors executors) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.executors = Objects.requireNonNull(executors, "executors");
    }

    @Override
    public CompletableFuture<Void> save(GenerationJobSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return executors.runIo(() -> {
            try {
                Files.createDirectories(root);
                codec.writeGenerationJob(path(snapshot.jobId()), snapshot);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<GenerationJobSnapshot>> find(String jobId) {
        Objects.requireNonNull(jobId, "jobId");
        final UUID parsed;
        try {
            parsed = UUID.fromString(jobId);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("invalid job ID", exception));
        }
        return executors.supplyIo(() -> {
            Path file = path(parsed);
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            try {
                return Optional.of(codec.readGenerationJob(file));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    public CompletableFuture<List<GenerationJobSnapshot>> findAll() {
        return executors.supplyIo(() -> {
            if (!Files.isDirectory(root)) {
                return List.of();
            }
            try (var files = Files.list(root)) {
                List<GenerationJobSnapshot> values = new ArrayList<>();
                for (Path file : files.filter(value -> value.getFileName().toString().endsWith(".json"))
                        .sorted().toList()) {
                    values.add(codec.readGenerationJob(file));
                }
                return List.copyOf(values);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /** Persists cancellation intent. In-process owners must additionally cancel their active future. */
    public CompletableFuture<GenerationJobSnapshot> cancel(String jobId, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return find(jobId).thenCompose(value -> {
            GenerationJobSnapshot current = value.orElseThrow(() -> new LandformException(
                    LandformErrorCode.NOT_FOUND, "Job was not found.", "job-cancel", jobId,
                    "load-job", "Check the job ID with job status."));
            if (current.stage() == GenerationStage.READY || current.stage() == GenerationStage.FAILED
                    || current.stage() == GenerationStage.CANCELLED) {
                return CompletableFuture.completedFuture(current);
            }
            GenerationJobSnapshot cancelled = new GenerationJobSnapshot(
                    current.schemaVersion(), current.jobId(), current.requestId(), GenerationStage.CANCELLED,
                    current.progress(), clock.instant(), "job cancellation requested");
            return save(cancelled).thenApply(ignored -> cancelled);
        });
    }

    private Path path(UUID jobId) {
        return root.resolve(jobId + ".json");
    }
}
