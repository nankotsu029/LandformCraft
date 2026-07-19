package com.github.nankotsu029.landformcraft.core.v2.placement;

import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementDiskSpaceProbeV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V2-11-02: the Release 2 path must honour every operator disk setting, not only
 * {@code disk.maximum-snapshot-bytes}.
 */
class Release2DiskBudgetV2Test {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void reservationFloorCombinesMinimumFreeSpaceAndSafetyMargin() {
        Release2DiskBudgetV2 budget = new Release2DiskBudgetV2(
                536_870_912L, 8_589_934_592L, 268_435_456L);
        assertEquals(536_870_912L + 268_435_456L, budget.reservationFloorBytes());
        assertEquals(8_589_934_592L, budget.maximumSnapshotBytes());
    }

    @Test
    void legacyBudgetKeepsTheHistoricalFloorForCallersWithoutConfiguredSettings() {
        Release2DiskBudgetV2 legacy = Release2DiskBudgetV2.legacy(4_096L);
        assertEquals(FilePlacementSafetyStoreV2.MINIMUM_FREE_BYTES, legacy.reservationFloorBytes());
        assertEquals(4_096L, legacy.maximumSnapshotBytes());
    }

    @Test
    void invalidDiskSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Release2DiskBudgetV2(-1L, 4_096L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Release2DiskBudgetV2(0L, 0L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Release2DiskBudgetV2(0L, 4_096L, -1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Release2DiskBudgetV2(Long.MAX_VALUE, 4_096L, 1L));
    }

    @Test
    void safetyStoreAdoptsTheConfiguredFloorAndNeverWidensTheDefault(@TempDir Path directory)
            throws IOException {
        PlacementDiskSpaceProbeV2 probe = new PlacementDiskSpaceProbeV2() {
            @Override
            public long usableBytes(Path root) {
                return 64L * 1024L * 1024L * 1024L;
            }

            @Override
            public String fileStoreKey(Path root) {
                return "test|test";
            }
        };
        Release2DiskBudgetV2 budget = new Release2DiskBudgetV2(
                536_870_912L, 8_589_934_592L, 268_435_456L);
        FilePlacementSafetyStoreV2 configured = new FilePlacementSafetyStoreV2(
                directory.resolve("configured.json"), directory.resolve("snapshots"), CLOCK,
                probe, budget.reservationFloorBytes());
        assertEquals(budget.reservationFloorBytes(), configured.reservationFloorBytes());

        // Callers that do not pass a budget keep the historical floor rather than dropping to 0.
        FilePlacementSafetyStoreV2 legacy = new FilePlacementSafetyStoreV2(
                directory.resolve("legacy.json"), directory.resolve("snapshots"), CLOCK, probe);
        assertEquals(FilePlacementSafetyStoreV2.MINIMUM_FREE_BYTES, legacy.reservationFloorBytes());

        assertThrows(IllegalArgumentException.class, () -> new FilePlacementSafetyStoreV2(
                directory.resolve("negative.json"), directory.resolve("snapshots"), CLOCK,
                probe, -1L));
    }
}
