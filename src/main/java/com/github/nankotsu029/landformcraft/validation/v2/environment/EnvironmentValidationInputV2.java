package com.github.nankotsu029.landformcraft.validation.v2.environment;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Objects;

/** Input envelope for one independent environment validation pass. */
public record EnvironmentValidationInputV2(
        int width,
        int length,
        String sourcePlanChecksum,
        EnvironmentFieldSamplerV2 fields
) {
    public static final int MAX_DIMENSION = ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING;

    public EnvironmentValidationInputV2 {
        if (width < 1 || width > MAX_DIMENSION || length < 1 || length > MAX_DIMENSION) {
            throw new IllegalArgumentException("environment validation dimensions must be within 1.." + ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING);
        }
        Objects.requireNonNull(sourcePlanChecksum, "sourcePlanChecksum");
        if (!sourcePlanChecksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourcePlanChecksum must be a lowercase SHA-256");
        }
        Objects.requireNonNull(fields, "fields");
    }
}
