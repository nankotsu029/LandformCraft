package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticGateContractV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V2-18-02 report-only diagnostic: unconsumed HARD elements, contract-only kinds, and
 * active-contributor / surface-foundation-owner coverage computed from a single run.
 */
class IntentContributionCoverageV2Test {
    private static final Path INTENT = Path.of("examples/v2/diagnostic/azure-coast.terrain-intent-v2.json");

    @Test
    void reportsUnconsumedHardElementsAndCoverageFromASingleRun() throws Exception {
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);
        // 3x2 grid; half the cells carry a nonzero owner index (active coastal contributor), half
        // fall back to zero (no owner today == the audit's "feature non-owned cell" baseline fill).
        CoastalFieldSamplerV2 fields = ownerIndexSampler(3, 2, new int[] {1, 0, 2, 0, 3, 0});

        IntentContributionCoverageV2 report = IntentContributionCoverageV2.compute(
                intent, fields, List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS),
                DiagnosticGateContractV2.builtIn());

        assertEquals(IntentContributionCoverageV2.CONTRACT_VERSION, report.contractVersion());
        assertEquals(6, report.totalCells());
        assertEquals(3, report.activeContributorCells());
        assertEquals(500_000, report.activeContributorCoverageMillionths());
        // V2-18-09: the foundation owner metric reads the foundation.owner-index namespace. This
        // legacy-shaped sampler carries no foundation owner, so the metric honestly reports zero —
        // active coastal contributors are surface modifiers, not foundation owners (ADR 0038 D3).
        assertEquals(0, report.surfaceFoundationOwnerCells());
        assertEquals(0, report.surfaceFoundationOwnerCoverageMillionths());

        assertEquals(List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS), report.contractOnlyKinds());

        // azure-coast declares two HARD constraints (south-is-sea EDGE, beach-width METRIC;
        // cape-irregularity is SOFT). V2-18-04's target-driven framework now evaluates
        // v2.edge-classification, so only the HARD METRIC_RANGE (beach-width) remains unconsumed.
        assertEquals(List.of("beach-width"), report.unconsumedHardConstraints().stream()
                .map(IntentContributionCoverageV2.UnconsumedHardConstraint::constraintId).sorted().toList());
        assertEquals("v2.metric-range", report.unconsumedHardConstraints().getFirst().ruleId());

        // Only backshore-adjoins-beach touches a non-production-connected kind (BACKSHORE_PLAINS);
        // the other HARD relations connect only production-connected coastal kinds.
        assertEquals(1, report.unconsumedHardRelations().size());
        IntentContributionCoverageV2.UnconsumedHardRelation relation = report.unconsumedHardRelations().getFirst();
        assertEquals("backshore-adjoins-beach", relation.relationId());
        assertEquals(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS, relation.unconnectedFeatureKind());

        // Without a consumed-role declaration the LAND_WATER_MASK reference stays reported; the
        // pipeline passes LAND_WATER_MASK as consumed on the V2-18-09 foundation path.
        assertEquals(1, report.unconsumedHardMapReferences().size());
        assertEquals("coast-mask-binding", report.unconsumedHardMapReferences().getFirst().mapReferenceId());
        assertEquals(TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                report.unconsumedHardMapReferences().getFirst().role());
    }

    @Test
    void fullOwnerCoverageReportsOneMillion() throws Exception {
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);
        CoastalFieldSamplerV2 fields = ownerIndexSampler(2, 2, new int[] {1, 1, 1, 1});

        IntentContributionCoverageV2 report = IntentContributionCoverageV2.compute(
                intent, fields, List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS),
                DiagnosticGateContractV2.builtIn());

        assertEquals(4, report.totalCells());
        assertEquals(4, report.activeContributorCells());
        assertEquals(1_000_000, report.activeContributorCoverageMillionths());
        assertEquals(0, report.surfaceFoundationOwnerCells());
        assertEquals(0, report.surfaceFoundationOwnerCoverageMillionths());
    }

    @Test
    void computeRejectsNullArguments() throws Exception {
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);
        CoastalFieldSamplerV2 fields = ownerIndexSampler(1, 1, new int[] {0});
        List<TerrainIntentV2.FeatureKind> contractOnly = List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS);
        DiagnosticGateContractV2 gateContract = DiagnosticGateContractV2.builtIn();

        assertThrows(NullPointerException.class,
                () -> IntentContributionCoverageV2.compute(null, fields, contractOnly, gateContract));
        assertThrows(NullPointerException.class,
                () -> IntentContributionCoverageV2.compute(intent, null, contractOnly, gateContract));
        assertThrows(NullPointerException.class,
                () -> IntentContributionCoverageV2.compute(intent, fields, null, gateContract));
        assertThrows(NullPointerException.class,
                () -> IntentContributionCoverageV2.compute(intent, fields, contractOnly, null));
    }

    @Test
    void toSummaryMapNeverThrowsAndCarriesEveryField() throws Exception {
        TerrainIntentV2 intent = new LandformV2DataCodec().readTerrainIntent(INTENT);
        CoastalFieldSamplerV2 fields = ownerIndexSampler(2, 1, new int[] {1, 0});

        IntentContributionCoverageV2 report = IntentContributionCoverageV2.compute(
                intent, fields, List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS),
                DiagnosticGateContractV2.builtIn());

        var summary = report.toSummaryMap();
        assertEquals(IntentContributionCoverageV2.CONTRACT_VERSION, summary.get("contractVersion"));
        assertEquals(2, summary.get("totalCells"));
        assertEquals(List.of("BACKSHORE_PLAINS"), summary.get("contractOnlyKinds"));
    }

    private static CoastalFieldSamplerV2 ownerIndexSampler(int width, int length, int[] ownerIndexByCell) {
        return new CoastalFieldSamplerV2() {
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
                // V2-18-09: the report also scans the foundation owner-index namespace; this
                // legacy-shaped sampler carries no foundation owner, like the baseline path.
                if (SurfaceFoundationPlanV2.OWNER_INDEX_FIELD_ID.equals(fieldId)) {
                    return 0;
                }
                assertEquals(CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, fieldId);
                return ownerIndexByCell[globalZ * width + globalX];
            }
        };
    }
}
