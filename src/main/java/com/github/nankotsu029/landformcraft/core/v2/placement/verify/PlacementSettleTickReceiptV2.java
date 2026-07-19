package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Completion receipt for one settle tick. Outside-envelope updates are hard-fail evidence for the
 * settle service; the gateway must not silently drop them.
 */
public record PlacementSettleTickReceiptV2(
        UUID operationId,
        int tickIndex,
        boolean schedulerAccepted,
        boolean mainThread,
        int updatesInsideEnvelope,
        int updatesOutsideEnvelope,
        List<OutsideUpdateSampleV2> outsideSamples
) {
    public PlacementSettleTickReceiptV2 {
        Objects.requireNonNull(operationId, "operationId");
        if (tickIndex < 0
                || updatesInsideEnvelope < 0
                || updatesOutsideEnvelope < 0) {
            throw new IllegalArgumentException("invalid settle tick receipt counts");
        }
        Objects.requireNonNull(outsideSamples, "outsideSamples");
        outsideSamples = List.copyOf(outsideSamples);
        if (outsideSamples.size() > updatesOutsideEnvelope) {
            throw new IllegalArgumentException("outside sample count exceeds reported updates");
        }
    }

    public void requireMatches(PlacementSettleTickV2 tick) {
        Objects.requireNonNull(tick, "tick");
        if (!operationId.equals(tick.operationId()) || tickIndex != tick.tickIndex()) {
            throw new IllegalArgumentException("settle tick receipt does not match the request");
        }
        if (!schedulerAccepted) {
            throw new IllegalArgumentException("settle tick was not scheduler-accepted");
        }
        if (!mainThread) {
            throw new IllegalArgumentException("settle tick must execute on the Paper main thread");
        }
    }

    public record OutsideUpdateSampleV2(int x, int y, int z, String blockState) {
        public OutsideUpdateSampleV2 {
            blockState = Objects.requireNonNull(blockState, "blockState");
            if (blockState.isBlank() || blockState.length() > 256) {
                throw new IllegalArgumentException("invalid outside update block state");
            }
        }
    }
}
