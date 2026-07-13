package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GenerationJobSnapshot(
        UUID jobId,
        String requestId,
        GenerationStage stage,
        double progress,
        Instant updatedAt,
        String message
) {
    public GenerationJobSnapshot {
        Objects.requireNonNull(jobId, "jobId");
        requestId = ModelValidation.requireNonBlank(requestId, "requestId");
        Objects.requireNonNull(stage, "stage");
        progress = ModelValidation.requireUnitInterval(progress, "progress");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(message, "message");
    }
}
