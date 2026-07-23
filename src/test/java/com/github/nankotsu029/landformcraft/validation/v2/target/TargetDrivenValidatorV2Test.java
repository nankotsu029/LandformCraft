package com.github.nankotsu029.landformcraft.validation.v2.target;

import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.ValidationTargetV2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-18-04 target-driven validation framework + EDGE evaluator. Land is 1, water is 0 in the
 * {@code intent.land-water-mask} field; the edge band is {@code max(1, perpendicular/32)} deep.
 */
class TargetDrivenValidatorV2Test {
    private static final String LAND_WATER = BuiltInLandformModuleCatalogV2.CONTRACT_FIELD_ID;
    private static final TargetDrivenValidatorV2 VALIDATOR = TargetDrivenValidatorV2.builtIn();

    // 32 wide x 64 long => NORTH/SOUTH band depth 2, EAST/WEST band depth 1. Top two rows are land,
    // everything else is water.
    private static final ValidationFieldSamplerV2 NORTH_LAND_SOUTH_SEA = grid(32, 64, (x, z) -> z < 2 ? 1 : 0);

    @Test
    void aSatisfiedHardEdgeTargetPasses() {
        TargetEvaluationV2 north = only(VALIDATOR.validate(
                List.of(edgeTarget("north-land", TerrainIntentV2.Strength.HARD, "edge.north.land", 900_000, 0)),
                NORTH_LAND_SOUTH_SEA));
        assertTrue(north.satisfied(), north.detail());
        assertEquals(1_000_000, north.measuredMillionths());
        assertEquals(EdgeClassificationEvaluatorV2.EVALUATOR_VERSION, north.evaluatorVersion());
        assertFalse(north.hardViolation());
    }

    @Test
    void aViolatedHardEdgeTargetIsAHardViolation() {
        // The south band is all water, so "south is land >= 0.85" fails.
        TargetValidationReportV2 report = VALIDATOR.validate(
                List.of(edgeTarget("south-land", TerrainIntentV2.Strength.HARD, "edge.south.land", 850_000, 0)),
                NORTH_LAND_SOUTH_SEA);
        assertTrue(report.hasHardViolation());
        assertEquals(0, only(report).measuredMillionths());
    }

    @Test
    void theSameViolationIsOnlyAdvisoryWhenSoft() {
        TargetValidationReportV2 report = VALIDATOR.validate(
                List.of(edgeTarget("south-land", TerrainIntentV2.Strength.SOFT, "edge.south.land", 850_000, 400_000)),
                NORTH_LAND_SOUTH_SEA);
        assertFalse(report.hasHardViolation());
        assertEquals(1, report.softViolations().size());
    }

    @Test
    void classificationSeaMatchesWaterCells() {
        // The south band is all water, so "south is sea >= 0.9" is satisfied.
        TargetEvaluationV2 south = only(VALIDATOR.validate(
                List.of(edgeTarget("south-sea", TerrainIntentV2.Strength.HARD, "edge.south.sea", 900_000, 0)),
                NORTH_LAND_SOUTH_SEA));
        assertTrue(south.satisfied(), south.detail());
        assertEquals(1_000_000, south.measuredMillionths());
    }

    @Test
    void theBandDepthScalesWithThePerpendicularExtent() {
        // 96 long => north band depth 3. Row 0-1 land, row 2 water => share 2/3 over the band.
        ValidationFieldSamplerV2 fields = grid(4, 96, (x, z) -> z < 2 ? 1 : 0);
        TargetEvaluationV2 north = only(VALIDATOR.validate(
                List.of(edgeTarget("north-land", TerrainIntentV2.Strength.HARD, "edge.north.land", 600_000, 0)),
                fields));
        assertEquals(666_666, north.measuredMillionths());
        assertTrue(north.satisfied());
    }

    @Test
    void toleranceCanRescueAMarginalMiss() {
        // Top half (z<4) is land, so the west band (all z) is 0.5 land. Minimum 0.6 fails, +0.1 passes.
        ValidationFieldSamplerV2 fields = grid(64, 8, (x, z) -> z < 4 ? 1 : 0);
        assertEquals(500_000, only(VALIDATOR.validate(
                List.of(edgeTarget("west-land", TerrainIntentV2.Strength.HARD, "edge.west.land", 600_000, 0)),
                fields)).measuredMillionths());
        assertFalse(only(VALIDATOR.validate(
                List.of(edgeTarget("west-land", TerrainIntentV2.Strength.HARD, "edge.west.land", 600_000, 0)),
                fields)).satisfied());
        assertTrue(only(VALIDATOR.validate(
                List.of(edgeTarget("west-land", TerrainIntentV2.Strength.SOFT, "edge.west.land", 600_000, 100_000)),
                fields)).satisfied());
    }

    @Test
    void targetsWithoutARegisteredEvaluatorAreLeftUntouched() {
        ValidationTargetV2 metricRange = new ValidationTargetV2(
                "beach-width", "beach-width", List.of("main-beach"), "v2.metric-range", 1,
                TerrainIntentV2.Strength.HARD, 0, "beach.width.blocks.p50",
                new TerrainIntentV2.FixedRange(20_000_000, 55_000_000), 0,
                List.of(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_FIELD_ID), "diagnostic.validation");

        assertEquals(0, VALIDATOR.validate(List.of(metricRange), NORTH_LAND_SOUTH_SEA).evaluations().size());
        assertEquals(java.util.Set.of("v2.edge-classification"), VALIDATOR.evaluatedRuleIds());
    }

    private static TargetEvaluationV2 only(TargetValidationReportV2 report) {
        assertEquals(1, report.evaluations().size());
        return report.evaluations().getFirst();
    }

    private static ValidationTargetV2 edgeTarget(
            String id, TerrainIntentV2.Strength strength, String metric, long minimumShare, long tolerance) {
        int weight = strength == TerrainIntentV2.Strength.SOFT ? 500_000 : 0;
        return new ValidationTargetV2(
                id, id, List.of(), EdgeClassificationEvaluatorV2.RULE_ID, 1, strength, weight, metric,
                new TerrainIntentV2.FixedRange(minimumShare, TerrainIntentV2.FIXED_SCALE),
                tolerance, List.of(LAND_WATER), "diagnostic.validation");
    }

    private interface CellValue {
        int at(int x, int z);
    }

    private static ValidationFieldSamplerV2 grid(int width, int length, CellValue cell) {
        return new ValidationFieldSamplerV2() {
            @Override
            public int width() {
                return width;
            }

            @Override
            public int length() {
                return length;
            }

            @Override
            public int valueAt(String fieldId, int globalX, int globalZ) {
                assertEquals(LAND_WATER, fieldId);
                return cell.at(globalX, globalZ);
            }
        };
    }
}
