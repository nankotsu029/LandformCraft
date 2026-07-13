package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Secret-free, prompt-free audit metadata for one successful design artifact. */
public record DesignAudit(
        int schemaVersion,
        UUID jobId,
        String requestId,
        String providerId,
        String modelId,
        String promptVersion,
        String responseId,
        ProviderUsage usage,
        int attempts,
        String requestChecksum,
        String intentChecksum,
        Instant startedAt,
        Instant completedAt
) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public DesignAudit {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        Objects.requireNonNull(jobId, "jobId");
        requestId = ModelValidation.requireSlug(requestId, "requestId");
        providerId = ModelValidation.requireNonBlank(providerId, "providerId", 64);
        modelId = ModelValidation.requireNonBlank(modelId, "modelId", 128);
        promptVersion = ModelValidation.requireNonBlank(promptVersion, "promptVersion", 64);
        responseId = ModelValidation.requireNonBlank(responseId, "responseId", 256);
        Objects.requireNonNull(usage, "usage");
        if (attempts < 1 || attempts > 10) {
            throw new IllegalArgumentException("attempts must be between 1 and 10");
        }
        requestChecksum = requireChecksum(requestChecksum, "requestChecksum");
        intentChecksum = requireChecksum(intentChecksum, "intentChecksum");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
    }

    private static String requireChecksum(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a lowercase SHA-256");
        }
        return value;
    }
}
