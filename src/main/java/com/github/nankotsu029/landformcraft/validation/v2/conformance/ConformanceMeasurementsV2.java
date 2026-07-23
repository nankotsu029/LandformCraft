package com.github.nankotsu029.landformcraft.validation.v2.conformance;

import com.github.nankotsu029.landformcraft.validation.v2.target.ValidationFieldSamplerV2;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The measurements {@link ConformanceResidualEvaluatorV2} consumes to turn a {@link ConformanceTargetSetV2}
 * into residuals (V2-18-07).
 *
 * <p>This is deliberately a passive input, not a second measurement engine: scalar and topology results
 * are produced by the existing evaluators ({@code TargetDrivenValidatorV2}, {@code CoastalValidatorV2},
 * the hydrology validators) and handed in here, so the conformance layer classifies and represents
 * residuals without duplicating any measurement logic. Anything a caller does not supply — no desired
 * sampler, no scalar for a target, no topology result — makes that target's residual an
 * {@link ConformanceResidualV2.UnconsumedTarget}, never a fabricated pass.</p>
 *
 * <p>The desired sampler may be absent (no mask resolved) and, when present, may be partial: a cell whose
 * desired value is {@link ConformanceResidualEvaluatorV2#NO_DATA} is simply not compared, which is how a
 * request that supplies only a partial desired raster is honoured (V2-18-07 non-scope: a complete desired
 * raster is never required).</p>
 */
public record ConformanceMeasurementsV2(
        Optional<ValidationFieldSamplerV2> desiredSampler,
        Optional<ValidationFieldSamplerV2> actualSampler,
        Map<String, Long> scalarMeasurements,
        Map<String, Boolean> topologyResults
) {
    public ConformanceMeasurementsV2 {
        desiredSampler = Objects.requireNonNull(desiredSampler, "desiredSampler");
        actualSampler = Objects.requireNonNull(actualSampler, "actualSampler");
        scalarMeasurements = Map.copyOf(Objects.requireNonNull(scalarMeasurements, "scalarMeasurements"));
        topologyResults = Map.copyOf(Objects.requireNonNull(topologyResults, "topologyResults"));
    }

    /** No measurements at all — every target becomes unconsumed. Useful as a baseline in tests and callers. */
    public static ConformanceMeasurementsV2 none() {
        return new ConformanceMeasurementsV2(Optional.empty(), Optional.empty(), Map.of(), Map.of());
    }

    /** Scalar (aggregate/geometric) measurements only. */
    public static ConformanceMeasurementsV2 ofScalars(Map<String, Long> scalarMeasurements) {
        return new ConformanceMeasurementsV2(Optional.empty(), Optional.empty(), scalarMeasurements, Map.of());
    }
}
