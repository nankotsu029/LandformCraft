package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SnapshotCleanupEntry(
        UUID placementId,
        PlacementState placementState,
        Instant journalUpdatedAt,
        List<SnapshotCleanupFile> files
) {
    public SnapshotCleanupEntry {
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(placementState, "placementState");
        Objects.requireNonNull(journalUpdatedAt, "journalUpdatedAt");
        files = ModelValidation.immutableList(files, "files", 1_024);
        if (files.isEmpty() || placementState == PlacementState.RECOVERY_REQUIRED
                || placementState == PlacementState.APPLYING
                || placementState == PlacementState.ROLLING_BACK
                || placementState == PlacementState.UNDOING
                || placementState == PlacementState.PLANNED) {
            throw new IllegalArgumentException("cleanup entry must reference an eligible terminal placement");
        }
    }

    public long totalBytes() {
        return files.stream().mapToLong(SnapshotCleanupFile::bytes).sum();
    }
}
