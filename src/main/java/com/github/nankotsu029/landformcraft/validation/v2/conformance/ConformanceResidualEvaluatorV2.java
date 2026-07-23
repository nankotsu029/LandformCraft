package com.github.nankotsu029.landformcraft.validation.v2.conformance;

import com.github.nankotsu029.landformcraft.validation.v2.target.ValidationFieldSamplerV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a {@link ConformanceTargetSetV2} plus {@link ConformanceMeasurementsV2} into a residual per
 * target (V2-18-07), each in the shape its {@link ConformanceTargetKindV2} requires.
 *
 * <p>The evaluator is a pure classification/aggregation layer: it never measures a field itself except
 * to count raster mismatches, and it emits an {@link ConformanceResidualV2.UnconsumedTarget} whenever a
 * target's desired reference or measurement is missing — the honest replacement for the pre-V2-18
 * self-referential residual that was identically zero and read as "conforming" (audit item 3). It is
 * deterministic: residuals follow the target set's order, and it does not depend on map iteration,
 * locale, timezone, or thread.</p>
 */
public final class ConformanceResidualEvaluatorV2 {
    /** Desired-cell sentinel meaning "no desired value here"; such cells are not compared. */
    public static final int NO_DATA = Integer.MIN_VALUE;

    /** Evaluates every target in set order. */
    public List<ConformanceResidualV2> evaluate(
            ConformanceTargetSetV2 set,
            ConformanceMeasurementsV2 measurements
    ) {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(measurements, "measurements");
        List<ConformanceResidualV2> residuals = new ArrayList<>(set.targets().size());
        for (ConformanceTargetV2 target : set.targets()) {
            residuals.add(switch (target) {
                case ConformanceTargetV2.DesiredRaster raster -> rasterResidual(raster, measurements);
                case ConformanceTargetV2.AggregateMetric metric ->
                        scalarResidual(metric.targetId(), ConformanceTargetKindV2.AGGREGATE_METRIC,
                                metric.expected().minimumMillionths(), metric.expected().maximumMillionths(),
                                metric.toleranceMillionths(), measurements);
                case ConformanceTargetV2.Geometric metric ->
                        scalarResidual(metric.targetId(), ConformanceTargetKindV2.GEOMETRIC,
                                metric.expected().minimumMillionths(), metric.expected().maximumMillionths(),
                                metric.toleranceMillionths(), measurements);
                case ConformanceTargetV2.Topology topology -> topologyResidual(topology, measurements);
            });
            // A scalar target that is measured but out of tolerance also emits a ToleranceViolation,
            // appended right after its ScalarMetricResidual so the pair is adjacent and ordered.
            ConformanceResidualV2 last = residuals.getLast();
            if (last instanceof ConformanceResidualV2.ScalarMetricResidual scalar && !scalar.satisfied()) {
                residuals.add(toleranceViolation(scalar));
            }
        }
        return List.copyOf(residuals);
    }

    private static ConformanceResidualV2 rasterResidual(
            ConformanceTargetV2.DesiredRaster raster,
            ConformanceMeasurementsV2 measurements
    ) {
        if (!raster.provenance().resolvable()) {
            return new ConformanceResidualV2.UnconsumedTarget(
                    raster.targetId(), ConformanceTargetKindV2.DESIRED_RASTER,
                    "desired reference is self-derived; no external mask resolved to check the actual field");
        }
        Optional<ValidationFieldSamplerV2> desired = measurements.desiredSampler();
        Optional<ValidationFieldSamplerV2> actual = measurements.actualSampler();
        if (desired.isEmpty() || actual.isEmpty()) {
            return new ConformanceResidualV2.UnconsumedTarget(
                    raster.targetId(), ConformanceTargetKindV2.DESIRED_RASTER,
                    "no desired/actual raster sampler resolved for the land-water mask");
        }
        ValidationFieldSamplerV2 desiredFields = desired.orElseThrow();
        ValidationFieldSamplerV2 actualFields = actual.orElseThrow();
        int width = Math.min(desiredFields.width(), actualFields.width());
        int length = Math.min(desiredFields.length(), actualFields.length());
        long compared = 0;
        long mismatch = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int desiredValue = desiredFields.valueAt(raster.fieldId(), x, z);
                if (desiredValue == NO_DATA) {
                    // A partial desired raster only constrains the cells it actually specifies.
                    continue;
                }
                compared++;
                if (actualFields.valueAt(raster.fieldId(), x, z) != desiredValue) {
                    mismatch++;
                }
            }
        }
        return new ConformanceResidualV2.RasterResidual(raster.targetId(), mismatch, compared);
    }

    private static ConformanceResidualV2 scalarResidual(
            String targetId,
            ConformanceTargetKindV2 kind,
            long expectedMinimum,
            long expectedMaximum,
            long tolerance,
            ConformanceMeasurementsV2 measurements
    ) {
        Long measured = measurements.scalarMeasurements().get(targetId);
        if (measured == null) {
            return new ConformanceResidualV2.UnconsumedTarget(
                    targetId, kind, "no evaluator produced a scalar measurement for this target");
        }
        return new ConformanceResidualV2.ScalarMetricResidual(
                targetId, measured, expectedMinimum, expectedMaximum, tolerance);
    }

    private static ConformanceResidualV2 topologyResidual(
            ConformanceTargetV2.Topology topology,
            ConformanceMeasurementsV2 measurements
    ) {
        Boolean passed = measurements.topologyResults().get(topology.targetId());
        if (passed == null) {
            return new ConformanceResidualV2.UnconsumedTarget(
                    topology.targetId(), ConformanceTargetKindV2.TOPOLOGY,
                    "no evaluator produced a topology result for this target");
        }
        String detail = topology.metric() + (passed ? " holds" : " does not hold");
        return new ConformanceResidualV2.TopologyPassFail(topology.targetId(), passed, detail);
    }

    private static ConformanceResidualV2.ToleranceViolation toleranceViolation(
            ConformanceResidualV2.ScalarMetricResidual scalar
    ) {
        long nearestBound = scalar.measuredMillionths() < scalar.expectedMinimumMillionths()
                ? scalar.expectedMinimumMillionths()
                : scalar.expectedMaximumMillionths();
        return new ConformanceResidualV2.ToleranceViolation(
                scalar.targetId(), scalar.measuredMillionths(), nearestBound, scalar.toleranceMillionths());
    }
}
