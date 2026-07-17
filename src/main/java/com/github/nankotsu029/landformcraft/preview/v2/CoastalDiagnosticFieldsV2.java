package com.github.nankotsu029.landformcraft.preview.v2;

import java.util.Objects;

/** Lazy V2-2 coastal diagnostic values. Each field is sampled on demand during one PNG render. */
public record CoastalDiagnosticFieldsV2(
        int width,
        int length,
        int minimumHeightMillionths,
        int maximumHeightMillionths,
        IntField beachOverlay,
        IntField harborOverlay,
        IntField breakwaterOverlay,
        IntField capeOverlay,
        IntField desiredLandWater,
        IntField actualLandWater,
        IntField residualLandWater,
        IntField desiredHeight,
        IntField actualHeight,
        IntField residualHeight,
        IntField constraintErrors
) {
    public static final int NO_DATA = Integer.MIN_VALUE;

    public CoastalDiagnosticFieldsV2 {
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000
                || minimumHeightMillionths >= maximumHeightMillionths) {
            throw new IllegalArgumentException("invalid coastal diagnostic dimensions or height range");
        }
        Objects.requireNonNull(beachOverlay, "beachOverlay");
        Objects.requireNonNull(harborOverlay, "harborOverlay");
        Objects.requireNonNull(breakwaterOverlay, "breakwaterOverlay");
        Objects.requireNonNull(capeOverlay, "capeOverlay");
        Objects.requireNonNull(desiredLandWater, "desiredLandWater");
        Objects.requireNonNull(actualLandWater, "actualLandWater");
        Objects.requireNonNull(residualLandWater, "residualLandWater");
        Objects.requireNonNull(desiredHeight, "desiredHeight");
        Objects.requireNonNull(actualHeight, "actualHeight");
        Objects.requireNonNull(residualHeight, "residualHeight");
        Objects.requireNonNull(constraintErrors, "constraintErrors");
    }

    @FunctionalInterface
    public interface IntField { int valueAt(int globalX, int globalZ); }
}
