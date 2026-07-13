package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;

public record ImageConsistencyCheck(
        String sourceFile,
        CardinalDirection side,
        ImageSideExpectation expected,
        double observedWaterRatio,
        ImageConsistencyStatus status
) {
    public ImageConsistencyCheck {
        sourceFile = ModelValidation.requireSafeRelativePath(sourceFile, "sourceFile");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(expected, "expected");
        observedWaterRatio = ModelValidation.requireUnitInterval(observedWaterRatio, "observedWaterRatio");
        Objects.requireNonNull(status, "status");
    }
}
