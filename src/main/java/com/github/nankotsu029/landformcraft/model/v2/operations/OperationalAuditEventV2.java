package com.github.nankotsu029.landformcraft.model.v2.operations;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Append-only operational audit event (V2-6-13). Carries correlation identity and operator-safe
 * fields only — never absolute paths, secrets, tokens, or raw payloads.
 */
public record OperationalAuditEventV2(
        int schemaVersion,
        String contractVersion,
        UUID correlationId,
        OperationalAuditEventKindV2 eventKind,
        String actorCanonical,
        String operation,
        String stage,
        String decision,
        UUID placementId,
        String resourceId,
        long bytesAffected,
        String recordedAt,
        String detail
) {
    public static final int SCHEMA_VERSION = 1;
    public static final String CONTRACT_VERSION = "operational-audit-event-v1";
    public static final int MAXIMUM_DETAIL_CHARS = 256;

    private static final Pattern INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");
    private static final Pattern ACTOR = Pattern.compile(
            "(CONSOLE|PLAYER|SYSTEM):[A-Za-z0-9._-]{1,128}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public OperationalAuditEventV2 {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("operational audit schemaVersion must be 1");
        }
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown operational audit contract");
        }
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(eventKind, "eventKind");
        actorCanonical = requireActor(actorCanonical);
        operation = requireSlug(operation, "operation");
        stage = requireSlug(stage, "stage");
        decision = requireSlug(decision, "decision");
        resourceId = resourceId == null || resourceId.isBlank() ? "" : requireSlug(resourceId, "resourceId");
        if (bytesAffected < 0L) {
            throw new IllegalArgumentException("bytesAffected must be >= 0");
        }
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        if (!INSTANT.matcher(recordedAt).matches()) {
            throw new IllegalArgumentException("recordedAt must be a UTC ISO-8601 instant");
        }
        detail = sanitizeDetail(detail);
    }

    private static String requireActor(String value) {
        value = Objects.requireNonNull(value, "actorCanonical").trim();
        if (!ACTOR.matcher(value).matches()) {
            throw new IllegalArgumentException("actorCanonical must be KIND:id");
        }
        return value;
    }

    private static String requireSlug(String value, String field) {
        value = Objects.requireNonNull(value, field).trim().toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a slug");
        }
        return value;
    }

    private static String sanitizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        String trimmed = detail.trim();
        if (trimmed.length() > MAXIMUM_DETAIL_CHARS) {
            trimmed = trimmed.substring(0, MAXIMUM_DETAIL_CHARS);
        }
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("://")
                || trimmed.toLowerCase(Locale.ROOT).contains("authorization")
                || trimmed.toLowerCase(Locale.ROOT).contains("api_key")
                || trimmed.toLowerCase(Locale.ROOT).contains("api-key")) {
            throw new IllegalArgumentException("operational audit detail must not contain paths or secrets");
        }
        return trimmed;
    }
}
