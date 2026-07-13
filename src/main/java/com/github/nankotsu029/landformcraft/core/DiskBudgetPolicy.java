package com.github.nankotsu029.landformcraft.core;

/** Hard limits applied before any world mutation or snapshot write. */
public record DiskBudgetPolicy(
        long minimumFreeBytes,
        long maximumSnapshotBytes,
        long safetyMarginBytes
) {
    public DiskBudgetPolicy {
        if (minimumFreeBytes < 0 || maximumSnapshotBytes < 1 || safetyMarginBytes < 0) {
            throw new IllegalArgumentException("invalid disk budget policy");
        }
    }

    public static DiskBudgetPolicy defaults() {
        return new DiskBudgetPolicy(512L * 1024 * 1024, 8L * 1024 * 1024 * 1024,
                256L * 1024 * 1024);
    }
}
