package com.github.nankotsu029.landformcraft.core.v2.recovery;

/**
 * Hard admission limits for recovery evidence scanning and snapshot cleanup retention. Scan and
 * retention work is admitted against these bounds before the first gateway read or file walk.
 */
public record PlacementRecoveryLimitsV2(
        String limitsVersion,
        long maximumScannedBlocks,
        int maximumFindings,
        int maximumRetentionFiles,
        long maximumRetentionBytes
) {
    public static final String VERSION = "release-2-placement-recovery-limits-v1";

    public PlacementRecoveryLimitsV2 {
        if (!VERSION.equals(limitsVersion)) {
            throw new IllegalArgumentException("unknown placement recovery limits version");
        }
        if (maximumScannedBlocks < 1 || maximumScannedBlocks > 1_000_000_000L
                || maximumFindings < 1 || maximumFindings > 1_024
                || maximumRetentionFiles < 1 || maximumRetentionFiles > 1_000_000
                || maximumRetentionBytes < 1 || maximumRetentionBytes > 1_000_000_000_000L) {
            throw new IllegalArgumentException("invalid placement recovery limits");
        }
    }

    public static PlacementRecoveryLimitsV2 defaults() {
        return new PlacementRecoveryLimitsV2(VERSION, 1_000_000L, 64, 4_096, 1_000_000_000L);
    }
}
