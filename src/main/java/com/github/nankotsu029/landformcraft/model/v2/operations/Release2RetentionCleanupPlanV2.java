package com.github.nankotsu029.landformcraft.model.v2.operations;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Actor-bound Release 2 snapshot retention cleanup plan (V2-6-13). Wraps a recovery dry-run
 * checksum with confirmation hash, TTL, and execute flag. Does not auto-delete.
 */
public record Release2RetentionCleanupPlanV2(
        int schemaVersion,
        String contractVersion,
        UUID planId,
        String actorCanonical,
        String createdAt,
        String expiresAt,
        UUID placementId,
        String journalChecksum,
        String recoveryCleanupPlanChecksum,
        int fileCount,
        long totalBytes,
        String confirmationHash,
        boolean executed
) {
    public static final int SCHEMA_VERSION = 1;
    public static final String CONTRACT_VERSION = "release-2-retention-cleanup-plan-v1";

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");
    private static final Pattern ACTOR = Pattern.compile(
            "(CONSOLE|PLAYER|SYSTEM):[A-Za-z0-9._-]{1,128}");

    public Release2RetentionCleanupPlanV2 {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("retention cleanup schemaVersion must be 1");
        }
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown retention cleanup contract");
        }
        Objects.requireNonNull(planId, "planId");
        actorCanonical = Objects.requireNonNull(actorCanonical, "actorCanonical").trim();
        if (!ACTOR.matcher(actorCanonical).matches()) {
            throw new IllegalArgumentException("actorCanonical must be KIND:id");
        }
        createdAt = requireInstant(createdAt, "createdAt");
        expiresAt = requireInstant(expiresAt, "expiresAt");
        Objects.requireNonNull(placementId, "placementId");
        journalChecksum = requireChecksum(journalChecksum, "journalChecksum");
        recoveryCleanupPlanChecksum = requireChecksum(recoveryCleanupPlanChecksum, "recoveryCleanupPlanChecksum");
        if (fileCount < 0 || totalBytes < 0L) {
            throw new IllegalArgumentException("fileCount/totalBytes must be >= 0");
        }
        confirmationHash = requireChecksum(confirmationHash, "confirmationHash");
    }

    public Release2RetentionCleanupPlanV2 withExecuted(boolean value) {
        return new Release2RetentionCleanupPlanV2(
                schemaVersion, contractVersion, planId, actorCanonical, createdAt, expiresAt,
                placementId, journalChecksum, recoveryCleanupPlanChecksum, fileCount, totalBytes,
                confirmationHash, value);
    }

    private static String requireInstant(String value, String field) {
        value = Objects.requireNonNull(value, field);
        if (!INSTANT.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a UTC ISO-8601 instant");
        }
        return value;
    }

    private static String requireChecksum(String value, String field) {
        value = Objects.requireNonNull(value, field);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase sha-256");
        }
        return value;
    }
}
