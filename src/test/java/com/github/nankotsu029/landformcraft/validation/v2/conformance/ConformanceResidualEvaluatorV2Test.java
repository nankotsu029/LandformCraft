package com.github.nankotsu029.landformcraft.validation.v2.conformance;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.ValidationTargetV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.ValidationFieldSamplerV2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-18-07 residual evaluator: each target kind maps to its residual shape, an unmeasured or
 * self-derived target becomes an {@code UnconsumedTarget} (never a false zero), a partial desired raster
 * only compares the cells it specifies, and a scalar outside tolerance also emits a tolerance violation.
 */
class ConformanceResidualEvaluatorV2Test {
    private static final String FIELD = ConformanceTargetSetV2.LAND_WATER_FIELD_ID;
    private final ConformanceResidualEvaluatorV2 evaluator = new ConformanceResidualEvaluatorV2();

    @Test
    void rasterResidualCountsMismatchesAgainstAResolvableDesiredReference() {
        ConformanceTargetSetV2 set = rasterSet(ConformanceProvenanceV2.Origin.INPUT_MASK);
        int[][] desired = {{1, 1}, {0, 0}};
        int[][] actual = {{1, 0}, {0, 0}};

        List<ConformanceResidualV2> residuals = evaluator.evaluate(
                set, samplers(desired, actual));

        ConformanceResidualV2.RasterResidual raster =
                assertInstanceOf(ConformanceResidualV2.RasterResidual.class, residuals.getFirst());
        assertEquals(4, raster.comparedCells());
        assertEquals(1, raster.mismatchCells());
        assertFalse(raster.satisfied());
    }

    @Test
    void aFullyMatchingRasterIsSatisfied() {
        ConformanceTargetSetV2 set = rasterSet(ConformanceProvenanceV2.Origin.INPUT_MASK);
        int[][] grid = {{1, 0}, {0, 1}};
        ConformanceResidualV2.RasterResidual raster = assertInstanceOf(
                ConformanceResidualV2.RasterResidual.class,
                evaluator.evaluate(set, samplers(grid, grid)).getFirst());
        assertEquals(0, raster.mismatchCells());
        assertTrue(raster.satisfied());
    }

    @Test
    void aPartialDesiredRasterOnlyComparesTheCellsItSpecifies() {
        ConformanceTargetSetV2 set = rasterSet(ConformanceProvenanceV2.Origin.INPUT_MASK);
        int nd = ConformanceResidualEvaluatorV2.NO_DATA;
        int[][] desired = {{1, nd}, {nd, 0}};
        int[][] actual = {{0, 1}, {1, 0}};

        ConformanceResidualV2.RasterResidual raster = assertInstanceOf(
                ConformanceResidualV2.RasterResidual.class,
                evaluator.evaluate(set, samplers(desired, actual)).getFirst());
        // Only the two specified cells are compared; one of them (1 vs 0) mismatches.
        assertEquals(2, raster.comparedCells());
        assertEquals(1, raster.mismatchCells());
    }

    @Test
    void aSelfDerivedDesiredReferenceIsUnconsumedNotAFalseZero() {
        ConformanceTargetSetV2 set = rasterSet(ConformanceProvenanceV2.Origin.SELF_DERIVED);
        int[][] grid = {{1, 1}, {1, 1}};

        ConformanceResidualV2.UnconsumedTarget unconsumed = assertInstanceOf(
                ConformanceResidualV2.UnconsumedTarget.class,
                evaluator.evaluate(set, samplers(grid, grid)).getFirst());
        assertEquals(ConformanceTargetKindV2.DESIRED_RASTER, unconsumed.kind());
        assertFalse(unconsumed.satisfied());
    }

    @Test
    void aMissingSamplerLeavesTheRasterUnconsumed() {
        ConformanceTargetSetV2 set = rasterSet(ConformanceProvenanceV2.Origin.INPUT_MASK);
        List<ConformanceResidualV2> residuals = evaluator.evaluate(set, ConformanceMeasurementsV2.none());
        assertInstanceOf(ConformanceResidualV2.UnconsumedTarget.class, residuals.getFirst());
    }

    @Test
    void aScalarWithinToleranceIsSatisfiedAndEmitsNoViolation() {
        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(
                List.of(aggregate("t-edge", range(200_000, 800_000), 0)), List.of(), Map.of());

        List<ConformanceResidualV2> residuals = evaluator.evaluate(
                set, ConformanceMeasurementsV2.ofScalars(Map.of("t-edge", 500_000L)));

        assertEquals(1, residuals.size());
        ConformanceResidualV2.ScalarMetricResidual scalar = assertInstanceOf(
                ConformanceResidualV2.ScalarMetricResidual.class, residuals.getFirst());
        assertTrue(scalar.satisfied());
    }

    @Test
    void aScalarOutOfToleranceAlsoEmitsAToleranceViolation() {
        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(
                List.of(aggregate("t-edge", range(400_000, 800_000), 10_000)), List.of(), Map.of());

        List<ConformanceResidualV2> residuals = evaluator.evaluate(
                set, ConformanceMeasurementsV2.ofScalars(Map.of("t-edge", 100_000L)));

        assertEquals(2, residuals.size());
        ConformanceResidualV2.ScalarMetricResidual scalar = assertInstanceOf(
                ConformanceResidualV2.ScalarMetricResidual.class, residuals.get(0));
        assertFalse(scalar.satisfied());
        ConformanceResidualV2.ToleranceViolation violation = assertInstanceOf(
                ConformanceResidualV2.ToleranceViolation.class, residuals.get(1));
        assertEquals("t-edge", violation.targetId());
        assertEquals(400_000, violation.nearestBoundMillionths());
        assertEquals(100_000, violation.measuredMillionths());
    }

    @Test
    void aScalarWithNoMeasurementIsUnconsumed() {
        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(
                List.of(aggregate("t-edge", range(0, 1_000_000), 0)), List.of(), Map.of());
        assertInstanceOf(ConformanceResidualV2.UnconsumedTarget.class,
                evaluator.evaluate(set, ConformanceMeasurementsV2.none()).getFirst());
    }

    @Test
    void aTopologyTargetReflectsItsBooleanResult() {
        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(
                List.of(target("t-river", "hydrology.river.reachability",
                        "hydrology.river.source-mouth-reachable", range(1, 1))),
                List.of(), Map.of());

        ConformanceResidualV2.TopologyPassFail pass = assertInstanceOf(
                ConformanceResidualV2.TopologyPassFail.class,
                evaluator.evaluate(set, topology("t-river", true)).getFirst());
        assertTrue(pass.satisfied());

        ConformanceResidualV2.TopologyPassFail fail = assertInstanceOf(
                ConformanceResidualV2.TopologyPassFail.class,
                evaluator.evaluate(set, topology("t-river", false)).getFirst());
        assertFalse(fail.satisfied());
    }

    @Test
    void aTopologyTargetWithNoResultIsUnconsumed() {
        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(
                List.of(target("t-river", "hydrology.river.reachability",
                        "hydrology.river.source-mouth-reachable", range(1, 1))),
                List.of(), Map.of());
        assertInstanceOf(ConformanceResidualV2.UnconsumedTarget.class,
                evaluator.evaluate(set, ConformanceMeasurementsV2.none()).getFirst());
    }

    @Test
    void evaluationIsDeterministicInOrderAndValue() {
        ConformanceTargetSetV2 set = ConformanceTargetSetV2.from(
                List.of(aggregate("t-a", range(0, 100), 0), aggregate("t-b", range(0, 100), 0)),
                List.of(), Map.of());
        ConformanceMeasurementsV2 measurements =
                ConformanceMeasurementsV2.ofScalars(Map.of("t-a", 50L, "t-b", 500L));
        assertEquals(evaluator.evaluate(set, measurements), evaluator.evaluate(set, measurements));
    }

    private static ConformanceTargetSetV2 rasterSet(ConformanceProvenanceV2.Origin origin) {
        ConformanceProvenanceV2 provenance = new ConformanceProvenanceV2(
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK, "constraint-source:coast-mask",
                "abc123", origin);
        return new ConformanceTargetSetV2(ConformanceTargetSetV2.CONTRACT_VERSION, List.of(
                new ConformanceTargetV2.DesiredRaster(
                        "conformance-desired-raster-coast", FIELD, TerrainIntentV2.Strength.HARD, provenance)));
    }

    private static ConformanceMeasurementsV2 samplers(int[][] desired, int[][] actual) {
        return new ConformanceMeasurementsV2(
                Optional.of(new GridSampler(desired)), Optional.of(new GridSampler(actual)),
                Map.of(), Map.of());
    }

    private static ConformanceMeasurementsV2 topology(String targetId, boolean passed) {
        return new ConformanceMeasurementsV2(
                Optional.empty(), Optional.empty(), Map.of(), Map.of(targetId, passed));
    }

    private static ValidationTargetV2 aggregate(
            String targetId, TerrainIntentV2.FixedRange expected, long tolerance) {
        return target(targetId, "v2.edge-classification", "edge.north.land", expected, tolerance);
    }

    private static ValidationTargetV2 target(
            String targetId, String ruleId, String metric, TerrainIntentV2.FixedRange expected) {
        return target(targetId, ruleId, metric, expected, 0);
    }

    private static ValidationTargetV2 target(
            String targetId, String ruleId, String metric, TerrainIntentV2.FixedRange expected, long tolerance) {
        return new ValidationTargetV2(
                targetId, "constraint-" + targetId, List.of(), ruleId, 1, TerrainIntentV2.Strength.HARD, 0,
                metric, expected, tolerance, List.of("intent.land-water-mask"), "diagnostic.validation");
    }

    private static TerrainIntentV2.FixedRange range(long min, long max) {
        return new TerrainIntentV2.FixedRange(min, max);
    }

    /** Row-major grid ({@code grid[z][x]}) exposed through the generic field sampler boundary. */
    private record GridSampler(int[][] grid) implements ValidationFieldSamplerV2 {
        @Override
        public int width() {
            return grid[0].length;
        }

        @Override
        public int length() {
            return grid.length;
        }

        @Override
        public int valueAt(String fieldId, int globalX, int globalZ) {
            return grid[globalZ][globalX];
        }
    }
}
