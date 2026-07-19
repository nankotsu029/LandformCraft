package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

import java.util.List;
import java.util.Objects;

/**
 * Per-column solid/fluid interval lists inside one cached chunk.
 */
public record VolumeColumnIntervalsV2(
        List<VolumeYIntervalV2> solidIntervals,
        List<VolumeFluidIntervalV2> fluidIntervals
) {
    public VolumeColumnIntervalsV2 {
        solidIntervals = List.copyOf(Objects.requireNonNull(solidIntervals, "solidIntervals"));
        fluidIntervals = List.copyOf(Objects.requireNonNull(fluidIntervals, "fluidIntervals"));
    }

    public static VolumeColumnIntervalsV2 empty() {
        return new VolumeColumnIntervalsV2(List.of(), List.of());
    }

    public long estimatedBytes() {
        return 16L
                + Math.multiplyExact((long) solidIntervals.size(), 16L)
                + Math.multiplyExact((long) fluidIntervals.size(), 32L);
    }
}
