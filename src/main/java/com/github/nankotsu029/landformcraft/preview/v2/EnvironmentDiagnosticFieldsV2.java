package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Objects;

/** Lazy V2-4-13 environment diagnostic values. Each field is sampled on demand during one PNG render. */
public record EnvironmentDiagnosticFieldsV2(
        int width,
        int length,
        IntField temperature,
        IntField moisture,
        IntField wetness,
        IntField salinity,
        IntField hydroperiod,
        IntField snowCover,
        IntField habitat,
        IntField materialProfile,
        IntField featureMaterial,
        IntField constraintError
) {
    public EnvironmentDiagnosticFieldsV2 {
        if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
            throw new IllegalArgumentException("invalid environment diagnostic dimensions");
        }
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(moisture, "moisture");
        Objects.requireNonNull(wetness, "wetness");
        Objects.requireNonNull(salinity, "salinity");
        Objects.requireNonNull(hydroperiod, "hydroperiod");
        Objects.requireNonNull(snowCover, "snowCover");
        Objects.requireNonNull(habitat, "habitat");
        Objects.requireNonNull(materialProfile, "materialProfile");
        Objects.requireNonNull(featureMaterial, "featureMaterial");
        Objects.requireNonNull(constraintError, "constraintError");
    }

    @FunctionalInterface
    public interface IntField {
        int valueAt(int globalX, int globalZ);
    }
}
