package com.github.nankotsu029.landformcraft.format.v2;

import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.BoundedHydrologyReconcilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dev-only helper for refreshing static hydrology reconciliation fixtures. */
class RegenerateHydrologyExampleChecksumsTest {
    @Test
    @Disabled("manual fixture regeneration helper")
    void rewriteHydrologyReconciliationExamples() throws Exception {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        HydrologyReconciliationPlanV2 plan = codec.sealHydrologyReconciliationPlan(new HydrologyReconciliationPlanV2(
                HydrologyReconciliationPlanV2.VERSION,
                HydrologyReconciliationPlanV2.ALGORITHM_VERSION,
                HydrologyReconciliationPlanV2.SCAN_ORDER_VERSION,
                com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationModuleV2.MODULE_ID,
                com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationModuleV2.MODULE_VERSION,
                com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationModuleV2.STAGE_ID,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS,
                List.of(new HydrologyReconciliationPlanV2.VariableDescriptor(
                        "delta.example.mouth.0000", "delta-example",
                        HydrologyReconciliationPlanV2.VariableKind.MARINE_CONNECTION, 1L, 0L, 1L)),
                List.of(new HydrologyReconciliationPlanV2.Constraint(
                        "reconcile.delta.delta-example.mouth.0000",
                        HydrologyReconciliationPlanV2.ConstraintKind.DELTA_MOUTH,
                        "delta-example", null, "delta.example.mouth.0000", 1L, 1L,
                        HydrologyReconciliationPlanV2.CorrectionPolicy.VERIFY_ONLY, 0L)),
                new HydrologyReconciliationPlanV2.WorkBudget(
                        HydrologyReconciliationPlanV2.BUDGET_VERSION,
                        HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS, 1, 1,
                        HydrologyReconciliationPlanV2.estimateWorkUnits(1, 1),
                        HydrologyReconciliationPlanV2.MAXIMUM_WORK_UNITS,
                        HydrologyReconciliationPlanV2.estimateWorkingBytes(1, 1),
                        HydrologyReconciliationPlanV2.MAXIMUM_WORKING_BYTES,
                        HydrologyReconciliationPlanV2.estimateArtifactBytes(1, 1),
                        HydrologyReconciliationPlanV2.MAXIMUM_ARTIFACT_BYTES),
                "0".repeat(64)));
        Path planPath = Path.of("examples/v2/hydrology/hydrology-reconciliation-plan-v2.json");
        codec.writeHydrologyReconciliationPlan(planPath, plan);

        HydrologyReconciliationPlanCompilerV2 compiler = new HydrologyReconciliationPlanCompilerV2();
        var baseline = compiler.baselineState(plan);
        var failedState = new com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationStateV2(
                baseline.sourcePlanChecksum(),
                List.of(new com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationStateV2.VariableValue(
                        "delta.example.mouth.0000", 0L, true)));
        var artifact = new HydrologyReconciliationArtifactCodecV2().seal(new BoundedHydrologyReconcilerV2().reconcile(
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                plan,
                failedState,
                () -> false));
        Path artifactPath = Path.of("examples/v2/hydrology/hydrology-reconciliation-artifact-v2.json");
        new HydrologyReconciliationArtifactCodecV2().write(artifactPath, artifact);
        Files.readString(planPath);
    }
}
