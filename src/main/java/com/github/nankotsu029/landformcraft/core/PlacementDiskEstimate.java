package com.github.nankotsu029.landformcraft.core;

/** Conservative pre-mutation capacity estimate with each component visible to diagnostics. */
public record PlacementDiskEstimate(
        long releaseReadBytes,
        long snapshotWorstCaseBytes,
        long journalBytes,
        long temporaryBytes,
        long rollbackOverheadBytes,
        long safetyMarginBytes,
        long totalBytes
) {
    public PlacementDiskEstimate {
        if (releaseReadBytes < 0 || snapshotWorstCaseBytes < 0 || journalBytes < 0
                || temporaryBytes < 0 || rollbackOverheadBytes < 0 || safetyMarginBytes < 0
                || totalBytes < 0) {
            throw new IllegalArgumentException("disk estimate components must not be negative");
        }
    }
}
