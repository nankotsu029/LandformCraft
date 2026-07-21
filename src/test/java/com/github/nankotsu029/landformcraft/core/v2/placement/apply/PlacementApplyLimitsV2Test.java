package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlacementApplyLimitsV2Test {
    @Test
    void calibrationCandidatesAreClosedAndShareOnePlanWorkingBudget() {
        assertEquals(List.of(32, 128, 256, 512, 1_024),
                PlacementApplyLimitsV2.CALIBRATION_SLICE_CANDIDATES);
        assertEquals(1_024,
                PlacementApplyLimitsV2.defaults().maximumMutationsPerSchedulerSlice());
        assertEquals(655_360L, PlacementApplyLimitsV2.maximumCalibrationSliceWorkingBytes());

        for (int candidate : PlacementApplyLimitsV2.CALIBRATION_SLICE_CANDIDATES) {
            PlacementApplyLimitsV2 limits = PlacementApplyLimitsV2.withSliceSize(candidate);
            assertEquals(candidate, limits.maximumMutationsPerSchedulerSlice());
            assertEquals(Math.multiplyExact((long) candidate, 640L),
                    limits.maximumSliceWorkingBytes());
            assertEquals(Math.multiplyExact(18L, limits.maximumSliceWorkingBytes()),
                    limits.maximumConcurrentSliceWorkingBytes());
        }

        assertThrows(IllegalArgumentException.class,
                () -> PlacementApplyLimitsV2.withSliceSize(31));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementApplyLimitsV2.withSliceSize(64));
        assertThrows(IllegalArgumentException.class,
                () -> PlacementApplyLimitsV2.withSliceSize(2_048));
    }

    @Test
    void versionedGatewayBoundsRemainFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new PlacementApplyLimitsV2(
                PlacementApplyLimitsV2.VERSION, 2, 16, 0, 32, 1_000_000_000L, 640));
        assertThrows(IllegalArgumentException.class, () -> new PlacementApplyLimitsV2(
                PlacementApplyLimitsV2.VERSION, 2, 16, 4_097, 32, 1_000_000_000L, 640));
        assertThrows(IllegalArgumentException.class, () -> new PlacementApplyLimitsV2(
                "future-limits", 2, 16, 32, 32, 1_000_000_000L, 640));
    }
}
