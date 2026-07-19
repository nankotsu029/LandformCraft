package com.github.nankotsu029.landformcraft.validation.v2.volume;

import java.util.Objects;

/** Bounded input envelope for one volume validation pass. */
public record VolumeValidationInputV2(
        int width,
        int length,
        String sourcePlanChecksum,
        VolumeFeatureSamplerV2 features
) {
    public static final int MAXIMUM_FEATURES = 64;

    public VolumeValidationInputV2 {
        if (width < 1 || width > 256 || length < 1 || length > 256) {
            throw new IllegalArgumentException("volume validation window must be within 1..256");
        }
        Objects.requireNonNull(sourcePlanChecksum, "sourcePlanChecksum");
        if (!sourcePlanChecksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourcePlanChecksum must be a lowercase SHA-256");
        }
        Objects.requireNonNull(features, "features");
    }
}
