package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

/**
 * Half-open integer Y interval {@code [minYInclusive, maxYExclusive)}.
 */
public record VolumeYIntervalV2(int minYInclusive, int maxYExclusive) {
    public VolumeYIntervalV2 {
        if (minYInclusive >= maxYExclusive) {
            throw new VolumeTileCacheExceptionV2(
                    VolumeTileCacheFailureCodeV2.INVALID_INTERVAL,
                    "Y interval must be non-empty half-open");
        }
    }

    public int length() {
        return Math.subtractExact(maxYExclusive, minYInclusive);
    }
}
