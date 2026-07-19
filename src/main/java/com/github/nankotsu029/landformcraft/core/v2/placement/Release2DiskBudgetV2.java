package com.github.nankotsu029.landformcraft.core.v2.placement;

import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;

/**
 * Release 2 view of the operator disk settings (V2-11-02).
 *
 * <p>Before V2-11-02 the Release 2 path only honoured {@code disk.maximum-snapshot-bytes} and
 * used a fixed 1 MiB floor for every reservation, snapshot and Undo admission, so
 * {@code disk.minimum-free-bytes} and {@code disk.safety-margin-bytes} silently did not apply.
 * This record carries all three settings into the Release 2 services. The reservation floor is
 * the sum of the minimum free space and the safety margin, so a stricter configuration always
 * tightens admission and never widens it.
 */
public record Release2DiskBudgetV2(
        long minimumFreeBytes,
        long maximumSnapshotBytes,
        long safetyMarginBytes
) {
    public Release2DiskBudgetV2 {
        if (minimumFreeBytes < 0L) {
            throw new IllegalArgumentException("disk.minimum-free-bytes must be non-negative");
        }
        if (maximumSnapshotBytes < 1L) {
            throw new IllegalArgumentException("disk.maximum-snapshot-bytes must be positive");
        }
        if (safetyMarginBytes < 0L) {
            throw new IllegalArgumentException("disk.safety-margin-bytes must be non-negative");
        }
        try {
            Math.addExact(minimumFreeBytes, safetyMarginBytes);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("disk reservation floor overflows");
        }
    }

    /**
     * Legacy floor for callers that predate the configured disk settings (tests and offline
     * fixtures). Keeps the historical 1 MiB behaviour instead of inventing a new default.
     */
    public static Release2DiskBudgetV2 legacy(long maximumSnapshotBytes) {
        return new Release2DiskBudgetV2(
                FilePlacementSafetyStoreV2.MINIMUM_FREE_BYTES, maximumSnapshotBytes, 0L);
    }

    /** Free bytes that must remain unreserved after every Release 2 admission. */
    public long reservationFloorBytes() {
        return minimumFreeBytes + safetyMarginBytes;
    }
}
