package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

import java.util.Objects;

/**
 * Half-open fluid Y interval carrying the owning fluid body id.
 */
public record VolumeFluidIntervalV2(int minYInclusive, int maxYExclusive, String fluidBodyId) {
    public VolumeFluidIntervalV2 {
        if (minYInclusive >= maxYExclusive) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.INVALID_INTERVAL,
                    "fluid Y interval must be non-empty half-open");
        }
        Objects.requireNonNull(fluidBodyId, "fluidBodyId");
        if (fluidBodyId.isBlank()) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.INVALID_INTERVAL, "fluidBodyId blank");
        }
    }
}
