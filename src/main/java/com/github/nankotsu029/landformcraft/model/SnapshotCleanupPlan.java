package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record SnapshotCleanupPlan(
        int schemaVersion,
        UUID planId,
        ActorIdentity actor,
        Instant createdAt,
        Instant expiresAt,
        int retentionDays,
        List<SnapshotCleanupEntry> entries,
        String confirmationHash,
        boolean executed
) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public SnapshotCleanupPlan {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        entries = ModelValidation.immutableList(entries, "entries", 4_096);
        confirmationHash = ModelValidation.requireNonBlank(confirmationHash, "confirmationHash");
        if (retentionDays < 1 || !expiresAt.isAfter(createdAt)
                || !SHA_256.matcher(confirmationHash).matches()) {
            throw new IllegalArgumentException("invalid snapshot cleanup plan");
        }
    }

    public long totalBytes() {
        return entries.stream().mapToLong(SnapshotCleanupEntry::totalBytes).sum();
    }
}
