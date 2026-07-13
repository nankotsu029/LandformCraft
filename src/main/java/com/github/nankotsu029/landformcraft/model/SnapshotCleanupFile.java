package com.github.nankotsu029.landformcraft.model;

import java.util.regex.Pattern;

public record SnapshotCleanupFile(String relativePath, String checksum, long bytes) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public SnapshotCleanupFile {
        relativePath = ModelValidation.requireSafeRelativePath(relativePath, "relativePath");
        checksum = ModelValidation.requireNonBlank(checksum, "checksum");
        if (!SHA_256.matcher(checksum).matches() || bytes < 1) {
            throw new IllegalArgumentException("invalid snapshot cleanup file identity");
        }
    }
}
