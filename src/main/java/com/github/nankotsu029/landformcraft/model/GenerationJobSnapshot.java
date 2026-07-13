package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GenerationJobSnapshot(
        int schemaVersion,
        UUID jobId,
        String requestId,
        GenerationStage stage,
        double progress,
        Instant updatedAt,
        String message
) {
    public GenerationJobSnapshot {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        Objects.requireNonNull(jobId, "jobId");
        requestId = ModelValidation.requireSlug(requestId, "requestId");
        Objects.requireNonNull(stage, "stage");
        progress = ModelValidation.requireUnitInterval(progress, "progress");
        Objects.requireNonNull(updatedAt, "updatedAt");
        message = ModelValidation.requireNonBlank(message, "message", 512);
    }
}
