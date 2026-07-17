package com.github.nankotsu029.landformcraft.validation.v2.coast;

import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.Objects;

/** Frozen Blueprint plus independently sampled desired and actual coastal fields. */
public record CoastalValidationInputV2(
        WorldBlueprintV2 blueprint,
        CoastalFieldSamplerV2 desiredFields,
        CoastalFieldSamplerV2 actualFields
) {
    public static final int NO_DATA = Integer.MIN_VALUE;

    public CoastalValidationInputV2 {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(desiredFields, "desiredFields");
        Objects.requireNonNull(actualFields, "actualFields");
        int width = blueprint.space().bounds().width();
        int length = blueprint.space().bounds().length();
        if (desiredFields.width() != width || desiredFields.length() != length
                || actualFields.width() != width || actualFields.length() != length) {
            throw new IllegalArgumentException("coastal validation field dimensions do not match Blueprint bounds");
        }
    }
}
