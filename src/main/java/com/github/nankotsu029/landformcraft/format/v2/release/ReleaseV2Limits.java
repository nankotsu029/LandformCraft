package com.github.nankotsu029.landformcraft.format.v2.release;

/** Bounded directory, archive, decode, and resident-buffer limits for Release format 2 core. */
public record ReleaseV2Limits(
        int maximumFileCount,
        long maximumArtifactBytes,
        long maximumDirectoryBytes,
        long maximumZipBytes,
        long maximumExpandedBytes,
        int copyBufferBytes
) {
    private static final long MEBIBYTE = 1024L * 1024L;

    public ReleaseV2Limits {
        if (maximumFileCount < 1 || maximumFileCount > 257
                || maximumArtifactBytes < 1 || maximumArtifactBytes > 64L * MEBIBYTE
                || maximumDirectoryBytes < maximumArtifactBytes || maximumDirectoryBytes > 128L * MEBIBYTE
                || maximumZipBytes < 1 || maximumZipBytes > 128L * MEBIBYTE
                || maximumExpandedBytes < maximumDirectoryBytes || maximumExpandedBytes > 128L * MEBIBYTE
                || copyBufferBytes < 1024 || copyBufferBytes > MEBIBYTE) {
            throw new IllegalArgumentException("Release format 2 limits are invalid");
        }
    }

    public static ReleaseV2Limits defaults() {
        return new ReleaseV2Limits(257, 64L * MEBIBYTE, 128L * MEBIBYTE,
                128L * MEBIBYTE, 128L * MEBIBYTE, 64 * 1024);
    }
}
