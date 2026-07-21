package com.github.nankotsu029.landformcraft.preview.v2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentCellSnapshotV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentFieldSamplerV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentValidatorV2;

import java.util.Objects;

/** Builds lazy diagnostic fields from a public environment sampler without materializing dense arrays. */
public final class EnvironmentDiagnosticFieldFactoryV2 {
    private EnvironmentDiagnosticFieldFactoryV2() {
    }

    public static EnvironmentDiagnosticFieldsV2 create(int width, int length, EnvironmentFieldSamplerV2 sampler) {
        Objects.requireNonNull(sampler, "sampler");
        if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
            throw new IllegalArgumentException("invalid environment diagnostic dimensions");
        }
        return new EnvironmentDiagnosticFieldsV2(
                width,
                length,
                (x, z) -> cell(sampler, x, z).temperatureRaw(),
                (x, z) -> cell(sampler, x, z).moistureRaw(),
                (x, z) -> cell(sampler, x, z).wetnessRaw(),
                (x, z) -> cell(sampler, x, z).salinityRaw(),
                (x, z) -> cell(sampler, x, z).hydroperiodRaw(),
                (x, z) -> cell(sampler, x, z).snowCoverRaw(),
                (x, z) -> cell(sampler, x, z).habitatCode(),
                (x, z) -> cell(sampler, x, z).materialClassCode(),
                (x, z) -> cell(sampler, x, z).featureMaterialClassCode(),
                (x, z) -> EnvironmentValidatorV2.hasConstraintError(cell(sampler, x, z)) ? 1 : 0);
    }

    private static EnvironmentCellSnapshotV2 cell(EnvironmentFieldSamplerV2 sampler, int x, int z) {
        return Objects.requireNonNull(sampler.at(x, z), "environment cell");
    }
}
