package com.github.nankotsu029.landformcraft.model.v2.job;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One durable v2 export job record (V2-12-09).
 *
 * <p>This is a v2 contract in its own right: the v1 {@code generation-job.schema.json} is frozen and
 * is not reused or extended. A snapshot is written on every state transition, so {@code v2 job
 * status} answers from disk after a restart rather than from an in-memory future.</p>
 *
 * <p>{@code requestId} is what makes {@code v2 candidate list &lt;request-id&gt;} possible without
 * touching Release format 2, whose manifest carries no request identity.</p>
 */
public record ExportJobSnapshotV2(
        int jobVersion,
        String jobId,
        String requestId,
        String releaseId,
        ExportJobKindV2 kind,
        ExportJobStateV2 state,
        int progressMillionths,
        String updatedAt,
        String message
) {
    public static final int VERSION = 2;
    public static final int MAX_MESSAGE_LENGTH = 512;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public ExportJobSnapshotV2 {
        if (jobVersion != VERSION) {
            throw new IllegalArgumentException("jobVersion must be exactly 2");
        }
        jobId = requireUuid(jobId);
        requestId = requireSlug(requestId, "requestId");
        releaseId = requireSlug(releaseId, "releaseId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        if (progressMillionths < 0 || progressMillionths > 1_000_000) {
            throw new IllegalArgumentException("progressMillionths must be in 0..1000000");
        }
        updatedAt = requireInstant(updatedAt);
        message = requireMessage(message);
    }

    public UUID jobUuid() {
        return UUID.fromString(jobId);
    }

    /** Transitions to {@code next} at {@code at}, refusing to move a job out of a terminal state. */
    public ExportJobSnapshotV2 transitionedTo(
            ExportJobStateV2 next,
            int progressMillionths,
            Instant at,
            String message
    ) {
        Objects.requireNonNull(next, "next");
        if (state.terminal()) {
            throw new IllegalStateException(
                    "v2 export job " + jobId + " is already " + state + " and cannot become " + next);
        }
        return new ExportJobSnapshotV2(jobVersion, jobId, requestId, releaseId, kind, next,
                progressMillionths, Objects.requireNonNull(at, "at").toString(), message);
    }

    private static String requireUuid(String value) {
        Objects.requireNonNull(value, "jobId");
        try {
            // Round-tripping rejects the shortened and mixed-case forms UUID.fromString tolerates,
            // so a job id can never resolve to two different file names.
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException("jobId must be a canonical lowercase UUID: " + value);
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("jobId must be a canonical lowercase UUID: " + value);
        }
        return value;
    }

    private static String requireSlug(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase portable slug: " + value);
        }
        return value;
    }

    private static String requireInstant(String value) {
        Objects.requireNonNull(value, "updatedAt");
        try {
            if (!Instant.parse(value).toString().equals(value)) {
                throw new IllegalArgumentException("updatedAt must be a canonical UTC instant: " + value);
            }
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("updatedAt must be a canonical UTC instant: " + value);
        }
        return value;
    }

    private static String requireMessage(String value) {
        Objects.requireNonNull(value, "message");
        if (value.isBlank() || value.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must be 1.." + MAX_MESSAGE_LENGTH + " characters");
        }
        return value;
    }
}
