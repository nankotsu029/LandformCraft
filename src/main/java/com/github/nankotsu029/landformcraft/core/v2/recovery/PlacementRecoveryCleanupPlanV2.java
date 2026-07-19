package com.github.nankotsu029.landformcraft.core.v2.recovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic dry-run cleanup plan for the retained snapshot evidence of one terminal
 * ({@code ROLLED_BACK}／{@code UNDONE}) Release 2 placement. Execution is bound to this exact
 * plan: the journal checksum and the exact file set must still match, otherwise cleanup refuses.
 */
public record PlacementRecoveryCleanupPlanV2(
        UUID placementId,
        String journalChecksum,
        List<SnapshotFileEntryV2> files,
        long totalBytes,
        String planChecksum
) {
    public PlacementRecoveryCleanupPlanV2 {
        Objects.requireNonNull(placementId, "placementId");
        journalChecksum = Objects.requireNonNull(journalChecksum, "journalChecksum");
        files = List.copyOf(Objects.requireNonNull(files, "files"));
        if (totalBytes < 0) {
            throw new IllegalArgumentException("totalBytes must be >= 0");
        }
        planChecksum = Objects.requireNonNull(planChecksum, "planChecksum");
        if (!planChecksum.equals(checksumOf(placementId, journalChecksum, files, totalBytes))) {
            throw new IllegalArgumentException("cleanup plan checksum mismatch");
        }
    }

    public static PlacementRecoveryCleanupPlanV2 sealed(
            UUID placementId,
            String journalChecksum,
            List<SnapshotFileEntryV2> files,
            long totalBytes
    ) {
        return new PlacementRecoveryCleanupPlanV2(
                placementId,
                journalChecksum,
                files,
                totalBytes,
                checksumOf(placementId, journalChecksum, files, totalBytes));
    }

    static String checksumOf(
            UUID placementId,
            String journalChecksum,
            List<SnapshotFileEntryV2> files,
            long totalBytes
    ) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        digest.update((placementId + "\n" + journalChecksum + "\n" + totalBytes + "\n")
                .getBytes(StandardCharsets.UTF_8));
        for (SnapshotFileEntryV2 file : files) {
            digest.update((file.fileName() + "\n" + file.sizeBytes() + "\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public record SnapshotFileEntryV2(String fileName, long sizeBytes) {
        public SnapshotFileEntryV2 {
            fileName = Objects.requireNonNull(fileName, "fileName");
            if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")
                    || fileName.contains("..")) {
                throw new IllegalArgumentException("cleanup file name must be a plain file name");
            }
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("sizeBytes must be >= 0");
            }
        }
    }
}
