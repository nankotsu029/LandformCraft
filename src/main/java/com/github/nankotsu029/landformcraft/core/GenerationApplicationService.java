package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.generator.TerrainGenerator;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.GenerationMetrics;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.ValidationResult;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;
import com.github.nankotsu029.landformcraft.preview.PreviewArtifacts;
import com.github.nankotsu029.landformcraft.preview.TerrainPreviewRenderer;
import com.github.nankotsu029.landformcraft.validation.TerrainValidator;
import com.github.nankotsu029.landformcraft.validation.TerrainPerformanceValidator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Shared async application entry point used by CLI now and Paper later. */
public final class GenerationApplicationService {
    private final GenerationExecutors executors;
    private final LandformDataCodec codec;
    private final BlueprintCompiler compiler;
    private final TerrainGenerator generator;
    private final TerrainValidator terrainValidator;
    private final TerrainPerformanceValidator performanceValidator;
    private final TerrainPreviewRenderer previewRenderer;

    public GenerationApplicationService(GenerationExecutors executors) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.codec = new LandformDataCodec();
        this.compiler = new BlueprintCompiler();
        this.generator = new TerrainGenerator();
        this.terrainValidator = new TerrainValidator();
        this.performanceValidator = new TerrainPerformanceValidator();
        this.previewRenderer = new TerrainPreviewRenderer();
    }

    public CompletableFuture<GenerationOutcome> generatePreview(
            Path requestPath,
            Path intentPath,
            Path outputDirectory,
            int candidateIndex
    ) {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(intentPath, "intentPath");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        return executors.supplyIo(() -> readInputs(requestPath, intentPath))
                .thenApply(inputs -> compiler.compile(inputs.request(), inputs.intent(), candidateIndex))
                .thenCompose(blueprint -> generate(blueprint, outputDirectory));
    }

    private CompletableFuture<GenerationOutcome> generate(WorldBlueprint blueprint, Path outputDirectory) {
        return executors.supplyGeneration(token -> {
            long startedAt = System.nanoTime();
            TerrainPlan plan = generator.generate(blueprint, token);
            return new TimedPlan(plan, Duration.ofNanos(System.nanoTime() - startedAt));
        }).thenCompose(timedPlan -> executors.supplyGeneration(
                token -> render(timedPlan, outputDirectory, token)
        ));
    }

    private GenerationOutcome render(TimedPlan timedPlan, Path outputDirectory, CancellationToken token) {
        TerrainPlan plan = timedPlan.plan();
        ValidationResult terrainValidation = terrainValidator.validate(plan);
        ValidationResult performanceValidation = performanceValidator.validate(plan, timedPlan.duration());
        var combinedIssues = new ArrayList<>(terrainValidation.issues());
        combinedIssues.addAll(performanceValidation.issues());
        ValidationResult validation = new ValidationResult(combinedIssues);
        GenerationMetrics metrics = performanceValidator.metrics(plan, timedPlan.duration());
        try {
            Path normalizedOutput = outputDirectory.toAbsolutePath().normalize();
            PreviewArtifacts previews = previewRenderer.render(plan, validation, normalizedOutput, token);
            codec.writeWorldBlueprint(normalizedOutput.resolve("world-blueprint.json"), plan.blueprint());
            codec.writeJson(normalizedOutput.resolve("validation.json"), validation);
            codec.writeJson(normalizedOutput.resolve("generation-summary.json"), new GenerationSummary(
                    plan.checksum(),
                    plan.blueprint().generatorVersion(),
                    plan.blueprint().seed(),
                    plan.tiles().size(),
                    metrics.generationMillis(),
                    metrics.estimatedRetainedBytes(),
                    metrics.estimatedPeakWorkingBytes(),
                    validation.isValid()
            ));
            return new GenerationOutcome(plan, validation, metrics, previews, normalizedOutput);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private InputContracts readInputs(Path requestPath, Path intentPath) {
        try {
            GenerationRequest request = codec.readGenerationRequest(requestPath);
            TerrainIntent intent = codec.readTerrainIntent(intentPath);
            return new InputContracts(request, intent);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record InputContracts(GenerationRequest request, TerrainIntent intent) {
    }

    private record TimedPlan(TerrainPlan plan, Duration duration) {
    }

    private record GenerationSummary(
            String checksum,
            String generatorVersion,
            long seed,
            int tileCount,
            long generationMillis,
            long estimatedRetainedBytes,
            long estimatedPeakWorkingBytes,
            boolean valid
    ) {
    }
}
