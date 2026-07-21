package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Objects;

/**
 * Lazy, bounded views used by the V2-1 constraint diagnostic renderer.
 * Values are sampled on demand so rendering does not retain duplicate full-size fields.
 */
public record ConstraintDiagnosticFieldsV2(
        int width,
        int length,
        int minimumHeightMillionths,
        int maximumHeightMillionths,
        IntField desiredLandWater,
        IntField actualLandWater,
        IntField residualLandWater,
        IntField desiredHeight,
        IntField actualHeight,
        IntField residualHeight,
        IntField zoneLabelMap,
        IntField constraintErrors
) {
    public static final int NO_DATA = Integer.MIN_VALUE;

    public ConstraintDiagnosticFieldsV2 {
        if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
            throw new IllegalArgumentException("diagnostic field dimensions outside 1.." + ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING);
        }
        if (minimumHeightMillionths >= maximumHeightMillionths) {
            throw new IllegalArgumentException("diagnostic height range is empty");
        }
        Objects.requireNonNull(desiredLandWater, "desiredLandWater");
        Objects.requireNonNull(actualLandWater, "actualLandWater");
        Objects.requireNonNull(residualLandWater, "residualLandWater");
        Objects.requireNonNull(desiredHeight, "desiredHeight");
        Objects.requireNonNull(actualHeight, "actualHeight");
        Objects.requireNonNull(residualHeight, "residualHeight");
        Objects.requireNonNull(zoneLabelMap, "zoneLabelMap");
        Objects.requireNonNull(constraintErrors, "constraintErrors");
    }

    @FunctionalInterface
    public interface IntField {
        int valueAt(int x, int z);
    }
}
