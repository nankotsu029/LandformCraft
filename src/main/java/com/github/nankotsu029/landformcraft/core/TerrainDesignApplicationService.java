package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignException;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignRequest;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignResult;
import com.github.nankotsu029.landformcraft.format.DesignArtifactPublisher;
import com.github.nankotsu029.landformcraft.format.DesignArtifacts;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.GenerationJobSnapshot;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.GenerationStage;
import com.github.nankotsu029.landformcraft.validation.ImageInputException;
import com.github.nankotsu029.landformcraft.validation.LoadedImageInputs;
import com.github.nankotsu029.landformcraft.validation.PreparedImageInputs;
import com.github.nankotsu029.landformcraft.validation.ReferenceImageProcessor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Provider-neutral Phase 4/5 design state machine. It never invokes the terrain generator. */
public final class TerrainDesignApplicationService {
    private final GenerationExecutors executors;
    private final TerrainDesignProvider provider;
    private final GenerationJobRepository jobs;
    private final LandformDataCodec codec;
    private final DesignArtifactPublisher publisher;
    private final ReferenceImageProcessor imageProcessor;
    private final Clock clock;

    public TerrainDesignApplicationService(
            GenerationExecutors executors,
            TerrainDesignProvider provider,
            GenerationJobRepository jobs
    ) {
        this(
                executors, provider, jobs, new LandformDataCodec(), new DesignArtifactPublisher(),
                new ReferenceImageProcessor(), Clock.systemUTC()
        );
    }

    TerrainDesignApplicationService(
            GenerationExecutors executors,
            TerrainDesignProvider provider,
            GenerationJobRepository jobs,
            LandformDataCodec codec,
            DesignArtifactPublisher publisher,
            ReferenceImageProcessor imageProcessor,
            Clock clock
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.imageProcessor = Objects.requireNonNull(imageProcessor, "imageProcessor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DesignJobHandle start(Path requestPath, Path designsRoot) {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(designsRoot, "designsRoot");
        UUID jobId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        CompletableFuture<DesignArtifacts> result = new CompletableFuture<>();
        AtomicReference<CompletableFuture<?>> active = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>("unresolved");
        AtomicBoolean cancellationRequested = new AtomicBoolean();

        CompletableFuture<GenerationRequest> requestRead = executors.supplyIo(
                () -> readRequest(requestPath, requestId)
        );
        active.set(requestRead);
        CompletableFuture<LoadedRequestContext> imageRead = requestRead.thenCompose(request -> {
            ensureNotCancelled(cancellationRequested);
            CompletableFuture<LoadedImageInputs> loading = executors.supplyIo(
                    () -> imageProcessor.load(requestPath, request)
            );
            active.set(loading);
            return loading.thenApply(images -> new LoadedRequestContext(request, images));
        });
        CompletableFuture<RequestContext> prepared = imageRead.thenCompose(context -> {
            ensureNotCancelled(cancellationRequested);
            CompletableFuture<PreparedImageInputs> processing = executors.supplyGeneration(token ->
                    imageProcessor.process(context.request(), context.images(), token::isCancellationRequested)
            );
            active.set(processing);
            return processing.thenApply(images -> new RequestContext(context.request(), images));
        });
        CompletableFuture<DesignArtifacts> flow = prepared
                .thenCompose(context -> {
                    ensureNotCancelled(cancellationRequested);
                    return save(jobId, context.request().requestId(), GenerationStage.QUEUED,
                            0.0, "design job queued").thenApply(ignored -> context);
                })
                .thenCompose(context -> {
                    ensureNotCancelled(cancellationRequested);
                    return save(jobId, context.request().requestId(), GenerationStage.VALIDATING_REQUEST,
                            0.1, "request validated").thenApply(ignored -> context);
                })
                .thenCompose(context -> {
                    ensureNotCancelled(cancellationRequested);
                    CompletableFuture<Void> stage = save(jobId, context.request().requestId(),
                            GenerationStage.CALLING_AI, 0.25, "calling provider " + provider.id());
                    active.set(stage);
                    return stage.thenCompose(ignored -> {
                        ensureNotCancelled(cancellationRequested);
                        CompletableFuture<TerrainDesignResult> providerFuture = provider.design(
                                new TerrainDesignRequest(context.request(), context.images().images(), jobId)
                        );
                        active.set(providerFuture);
                        return providerFuture.thenApply(design -> new DesignedContext(
                                context,
                                design,
                                context.images().evidence().withProviderResult(
                                        design.providerId(), design.responseId(), design.promptVersion(),
                                        provider.submitsReferenceImages()
                                )
                        ));
                    });
                })
                .thenCompose(context -> {
                    ensureNotCancelled(cancellationRequested);
                    return save(jobId, context.request().request().requestId(),
                            GenerationStage.VALIDATING_INTENT, 0.65, "validating structured TerrainIntent")
                            .thenApply(ignored -> context);
                })
                .thenCompose(context -> {
                    ensureNotCancelled(cancellationRequested);
                    CompletableFuture<DesignArtifacts> write = executors.supplyIo(() -> publish(
                            requestPath, designsRoot, jobId, startedAt, context
                    ));
                    active.set(write);
                    return write;
                })
                .thenCompose(artifacts -> {
                    ensureNotCancelled(cancellationRequested);
                    return save(jobId, artifacts.audit().requestId(), GenerationStage.READY,
                            1.0, "design artifact ready").thenApply(ignored -> artifacts);
                });

        flow.whenComplete((artifacts, failure) -> {
            if (failure == null) {
                result.complete(artifacts);
                return;
            }
            Throwable cause = unwrap(failure);
            GenerationStage terminal = cause instanceof CancellationException
                    ? GenerationStage.CANCELLED : GenerationStage.FAILED;
            String message = safeFailureMessage(cause);
            jobs.save(snapshot(jobId, requestId.get(), terminal, 1.0, message))
                    .whenComplete((ignored, saveFailure) -> {
                        if (!result.isDone()) {
                            result.completeExceptionally(cause);
                        }
                    });
        });
        result.whenComplete((ignored, failure) -> {
            if (result.isCancelled()) {
                cancellationRequested.set(true);
                CompletableFuture<?> current = active.get();
                if (current != null) {
                    current.cancel(true);
                }
                jobs.save(snapshot(jobId, requestId.get(), GenerationStage.CANCELLED, 1.0,
                        "design job cancelled"));
            }
        });
        return new DesignJobHandle(jobId, result);
    }

    private GenerationRequest readRequest(Path requestPath, AtomicReference<String> requestId) {
        try {
            GenerationRequest request = codec.readGenerationRequest(requestPath);
            requestId.set(request.requestId());
            return request;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private DesignArtifacts publish(
            Path requestPath,
            Path designsRoot,
            UUID jobId,
            Instant startedAt,
            DesignedContext context
    ) {
        try {
            String canonical = codec.writeJsonString(context.design().intent());
            codec.readTerrainIntent(canonical, "provider TerrainIntent");
            return publisher.publish(
                    requestPath, designsRoot, jobId, context.request().request().requestId(),
                    startedAt, context.design(), context.imageEvidence()
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private CompletableFuture<Void> save(
            UUID jobId,
            String requestId,
            GenerationStage stage,
            double progress,
            String message
    ) {
        return jobs.save(snapshot(jobId, requestId, stage, progress, message));
    }

    private GenerationJobSnapshot snapshot(
            UUID jobId,
            String requestId,
            GenerationStage stage,
            double progress,
            String message
    ) {
        return new GenerationJobSnapshot(1, jobId, requestId, stage, progress, clock.instant(), message);
    }

    private static String safeFailureMessage(Throwable failure) {
        if (failure instanceof CancellationException) {
            return "design job cancelled";
        }
        if (failure instanceof TerrainDesignException providerFailure) {
            return "provider failure: " + providerFailure.code();
        }
        if (failure instanceof ImageInputException imageFailure) {
            return "image input failure: " + imageFailure.code();
        }
        return "design job failed validation or artifact publication";
    }

    private static void ensureNotCancelled(AtomicBoolean cancellationRequested) {
        if (cancellationRequested.get() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("design job cancelled");
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof UncheckedIOException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record LoadedRequestContext(GenerationRequest request, LoadedImageInputs images) {
    }

    private record RequestContext(GenerationRequest request, PreparedImageInputs images) {
    }

    private record DesignedContext(
            RequestContext request,
            TerrainDesignResult design,
            com.github.nankotsu029.landformcraft.model.ImageInputEvidence imageEvidence
    ) {
    }
}
