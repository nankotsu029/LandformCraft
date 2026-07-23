package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-18-10 unit evidence for the surface foundation owner gate (ADR 0038 D7-2): 100% coverage passes,
 * anything below it fails closed with the stable rule id, and the gate's subject is the foundation
 * owner metric only — never the modifier (active contributor) metric.
 */
class SurfaceFoundationOwnerGateV2Test {
    private static final String PIPELINE = "v2.production.surface-2_5d.coastal";

    private final SurfaceFoundationOwnerGateV2 gate = new SurfaceFoundationOwnerGateV2();

    @Test
    void fullCoveragePasses() {
        assertDoesNotThrow(() -> gate.requireFullCoverage(PIPELINE, coverage(4096, 4096), "detail"));
        assertDoesNotThrow(() -> gate.requireFullCoverage(PIPELINE, 4096, 4096, "detail"));
    }

    @Test
    void aSingleUnownedCellFailsClosedWithTheStableRuleId() {
        SurfaceFoundationOwnerRejectedV2 rejected = assertThrows(SurfaceFoundationOwnerRejectedV2.class,
                () -> gate.requireFullCoverage(PIPELINE, coverage(4095, 4096), "one cell has no owner"));

        assertEquals(SurfaceFoundationOwnerGateV2.RULE_FOUNDATION_OWNER_COVERAGE_INCOMPLETE, rejected.ruleId());
        assertEquals(PIPELINE, rejected.pipelineId());
        assertEquals(4095, rejected.ownedCells());
        assertEquals(4096, rejected.totalCells());
        assertEquals(999_755, rejected.coverageMillionths());
        assertTrue(rejected.getMessage().contains("4095/4096"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("one cell has no owner"), rejected.getMessage());
    }

    @Test
    void theLegacyBaselinePathHasNoFoundationOwnerAtAll() {
        // The audited defect: a SurfaceBaselineV2 fill is not an owner, so the metric is 0 and the
        // export is rejected. There is no override — the baseline argument cannot buy coverage back
        // (ADR 0038 D8-2).
        SurfaceFoundationOwnerRejectedV2 rejected = assertThrows(SurfaceFoundationOwnerRejectedV2.class,
                () -> gate.requireFullCoverage(PIPELINE, coverage(0, 160_000), "no foundation input"));

        assertEquals(0, rejected.coverageMillionths());
    }

    @Test
    void modifierCoverageIsNotTheGateSubject() {
        // Active contributors legitimately claim only their footprints (the audit's 27%), so a run
        // with full foundation coverage and partial modifier coverage must pass.
        IntentContributionCoverageV2 coverage = new IntentContributionCoverageV2(
                IntentContributionCoverageV2.CONTRACT_VERSION,
                160_000, 43_200, 270_000, 160_000, 1_000_000,
                List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS), List.of(), List.of(), List.of());

        assertDoesNotThrow(() -> gate.requireFullCoverage(PIPELINE, coverage, "detail"));
    }

    @Test
    void invalidCountsAreRejectedRatherThanSilentlyPassing() {
        assertThrows(IllegalArgumentException.class,
                () -> gate.requireFullCoverage(PIPELINE, 1, 0, "empty domain"));
        assertThrows(IllegalArgumentException.class,
                () -> gate.requireFullCoverage(PIPELINE, 5, 4, "more owners than cells"));
    }

    @Test
    void coverageMillionthsFloorsLikeTheV21802Metric() {
        assertEquals(0, SurfaceFoundationOwnerGateV2.coverageMillionths(0, 160_000));
        assertEquals(1_000_000, SurfaceFoundationOwnerGateV2.coverageMillionths(160_000, 160_000));
        assertEquals(333_333, SurfaceFoundationOwnerGateV2.coverageMillionths(1, 3));
    }

    private static IntentContributionCoverageV2 coverage(int foundationOwnedCells, int totalCells) {
        return new IntentContributionCoverageV2(
                IntentContributionCoverageV2.CONTRACT_VERSION,
                totalCells, 0, 0, foundationOwnedCells,
                SurfaceFoundationOwnerGateV2.coverageMillionths(foundationOwnedCells, totalCells),
                List.of(), List.of(), List.of(), List.of());
    }
}
