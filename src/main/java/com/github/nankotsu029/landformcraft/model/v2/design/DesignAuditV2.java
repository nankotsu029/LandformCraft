package com.github.nankotsu029.landformcraft.model.v2.design;

import com.github.nankotsu029.landformcraft.model.ProviderUsage;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/** Secret-free, prompt-free audit metadata for one successful Release 2 design package. */
public record DesignAuditV2(
        int schemaVersion,
        UUID jobId,
        String requestId,
        DesignPathKindV2 pathKind,
        String providerId,
        String modelId,
        String promptVersion,
        String responseId,
        ProviderUsage usage,
        int attempts,
        String requestChecksum,
        String intentChecksum,
        Set<DesignCapabilityV2> negotiatedCapabilities,
        String capabilityCatalogVersion,
        Instant startedAt,
        Instant completedAt
) {
    public static final int VERSION = 1;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public DesignAuditV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("schemaVersion must be 1");
        }
        Objects.requireNonNull(jobId, "jobId");
        requestId = requireSlug(requestId, "requestId");
        Objects.requireNonNull(pathKind, "pathKind");
        providerId = requireNonBlank(providerId, "providerId", 64);
        modelId = requireNonBlank(modelId, "modelId", 128);
        promptVersion = requireNonBlank(promptVersion, "promptVersion", 64);
        responseId = requireNonBlank(responseId, "responseId", 256);
        Objects.requireNonNull(usage, "usage");
        if (attempts < 1 || attempts > 10) {
            throw new IllegalArgumentException("attempts must be between 1 and 10");
        }
        requestChecksum = requireChecksum(requestChecksum, "requestChecksum");
        intentChecksum = requireChecksum(intentChecksum, "intentChecksum");
        Objects.requireNonNull(negotiatedCapabilities, "negotiatedCapabilities");
        if (negotiatedCapabilities.isEmpty()) {
            throw new IllegalArgumentException("negotiatedCapabilities must not be empty");
        }
        TreeSet<DesignCapabilityV2> sorted = new TreeSet<>(Comparator.comparing(DesignCapabilityV2::name));
        sorted.addAll(negotiatedCapabilities);
        negotiatedCapabilities = Set.copyOf(sorted);
        capabilityCatalogVersion = requireNonBlank(capabilityCatalogVersion, "capabilityCatalogVersion", 64);
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
    }

    private static String requireSlug(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must match " + SLUG);
        }
        return value;
    }

    private static String requireNonBlank(String value, String fieldName, int maximumLength) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must be non-blank and <= " + maximumLength);
        }
        return value;
    }

    private static String requireChecksum(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a lowercase SHA-256");
        }
        return value;
    }
}
