package com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile;

import com.github.nankotsu029.landformcraft.model.v2.DeltaPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.FjordPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TidalChannelPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationStateV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compiles V2-3 regional feature plans into one fixed-order reconciliation contract. */
public final class HydrologyReconciliationPlanCompilerV2 {
    private static final long MAXIMUM_ADJUSTMENT_MILLIONTHS = 16L * TerrainIntentV2.FIXED_SCALE;
    private static final long MAXIMUM_VERTICAL_DELTA_MILLIONTHS = 512L * TerrainIntentV2.FIXED_SCALE;

    public HydrologyReconciliationPlanV2 compile(
            String sourceHydrologyPlanChecksum,
            List<MeanderingRiverPlanV2> riverPlans,
            List<LakePlanV2> lakePlans,
            List<DeltaPlanV2> deltaPlans,
            List<TidalChannelPlanV2> tidalPlans,
            List<FjordPlanV2> fjordPlans,
            List<WaterfallPlanV2> waterfallPlans,
            long maximumWorkUnits,
            long maximumWorkingBytes,
            long maximumArtifactBytes
    ) {
        try {
            List<HydrologyReconciliationPlanV2.VariableDescriptor> variables = new ArrayList<>();
            List<HydrologyReconciliationPlanV2.Constraint> constraints = new ArrayList<>();
            compileRivers(riverPlans, variables, constraints);
            compileLakes(lakePlans, variables, constraints);
            compileDeltaConnections(deltaPlans, variables, constraints);
            compileTidalConnections(tidalPlans, variables, constraints);
            compileFjordConnections(fjordPlans, variables, constraints);
            compileWaterfalls(waterfallPlans, variables, constraints);

            long estimatedWork = HydrologyReconciliationPlanV2.estimateWorkUnits(
                    variables.size(), constraints.size());
            long estimatedWorking = HydrologyReconciliationPlanV2.estimateWorkingBytes(
                    variables.size(), constraints.size());
            long estimatedArtifact = HydrologyReconciliationPlanV2.estimateArtifactBytes(
                    variables.size(), constraints.size());
            long workLimit = Math.min(maximumWorkUnits, HydrologyReconciliationPlanV2.MAXIMUM_WORK_UNITS);
            long workingLimit = Math.min(maximumWorkingBytes, HydrologyReconciliationPlanV2.MAXIMUM_WORKING_BYTES);
            long artifactLimit = Math.min(maximumArtifactBytes, HydrologyReconciliationPlanV2.MAXIMUM_ARTIFACT_BYTES);
            if (variables.size() > HydrologyReconciliationPlanV2.MAXIMUM_VARIABLES
                    || constraints.size() > HydrologyReconciliationPlanV2.MAXIMUM_CONSTRAINTS
                    || estimatedWork > workLimit
                    || estimatedWorking > workingLimit
                    || estimatedArtifact > artifactLimit) {
                throw new HydrologyReconciliationException(
                        "v2.hydrology-reconciliation-budget",
                        "hydrology reconciliation exceeds iteration, working-set, or artifact budget");
            }
            return new HydrologyReconciliationPlanV2(
                    HydrologyReconciliationPlanV2.VERSION,
                    HydrologyReconciliationPlanV2.ALGORITHM_VERSION,
                    HydrologyReconciliationPlanV2.SCAN_ORDER_VERSION,
                    HydrologyReconciliationModuleV2.MODULE_ID,
                    HydrologyReconciliationModuleV2.MODULE_VERSION,
                    HydrologyReconciliationModuleV2.STAGE_ID,
                    sourceHydrologyPlanChecksum,
                    HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS,
                    variables,
                    constraints,
                    new HydrologyReconciliationPlanV2.WorkBudget(
                            HydrologyReconciliationPlanV2.BUDGET_VERSION,
                            HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS,
                            variables.size(),
                            constraints.size(),
                            estimatedWork,
                            workLimit,
                            estimatedWorking,
                            workingLimit,
                            estimatedArtifact,
                            artifactLimit),
                    "0".repeat(64));
        } catch (HydrologyReconciliationException exception) {
            throw exception;
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw new HydrologyReconciliationException(
                    "v2.hydrology-reconciliation-budget",
                    "hydrology reconciliation plan is outside trusted bounds",
                    exception);
        }
    }

    public HydrologyReconciliationStateV2 baselineState(HydrologyReconciliationPlanV2 plan) {
        return new HydrologyReconciliationStateV2(
                plan.canonicalChecksum(),
                plan.variables().stream()
                        .map(variable -> new HydrologyReconciliationStateV2.VariableValue(
                                variable.variableId(), variable.baselineValueMillionths(), false))
                        .toList());
    }

    private static void compileRivers(
            List<MeanderingRiverPlanV2> plans,
            List<HydrologyReconciliationPlanV2.VariableDescriptor> variables,
            List<HydrologyReconciliationPlanV2.Constraint> constraints
    ) {
        for (MeanderingRiverPlanV2 plan : plans) {
            long minimum = Math.multiplyExact((long) plan.minY(), TerrainIntentV2.FIXED_SCALE);
            long maximum = Math.multiplyExact((long) plan.maxY(), TerrainIntentV2.FIXED_SCALE);
            for (int index = 0; index < plan.centerline().size(); index++) {
                MeanderingRiverPlanV2.CenterlineSample sample = plan.centerline().get(index);
                variables.add(variable(
                        riverVariable(plan.featureId(), index), plan.featureId(),
                        HydrologyReconciliationPlanV2.VariableKind.REACH_BED,
                        sample.bedYMillionths(), minimum, maximum));
                if (index == 0) continue;
                constraints.add(new HydrologyReconciliationPlanV2.Constraint(
                        "reconcile.reach." + plan.featureId() + "." + sequence(index),
                        HydrologyReconciliationPlanV2.ConstraintKind.REACH_BED,
                        plan.featureId(),
                        riverVariable(plan.featureId(), index - 1),
                        riverVariable(plan.featureId(), index),
                        -MAXIMUM_VERTICAL_DELTA_MILLIONTHS,
                        0L,
                        HydrologyReconciliationPlanV2.CorrectionPolicy.ADJUST_RIGHT,
                        MAXIMUM_ADJUSTMENT_MILLIONTHS));
            }
        }
    }

    private static void compileLakes(
            List<LakePlanV2> plans,
            List<HydrologyReconciliationPlanV2.VariableDescriptor> variables,
            List<HydrologyReconciliationPlanV2.Constraint> constraints
    ) {
        for (LakePlanV2 plan : plans) {
            if (plan.terminalPolicy() == TerrainIntentV2.LakeTerminalPolicy.CLOSED) continue;
            long minimum = Math.multiplyExact((long) plan.minY(), TerrainIntentV2.FIXED_SCALE);
            long maximum = Math.multiplyExact((long) plan.maxY(), TerrainIntentV2.FIXED_SCALE);
            String surface = "lake." + plan.featureId() + ".surface";
            String spill = "lake." + plan.featureId() + ".spill";
            variables.add(variable(surface, plan.featureId(),
                    HydrologyReconciliationPlanV2.VariableKind.LAKE_SURFACE,
                    plan.waterSurfaceYMillionths(), minimum, maximum));
            variables.add(variable(spill, plan.featureId(),
                    HydrologyReconciliationPlanV2.VariableKind.LAKE_SPILL,
                    plan.rimMinimumYMillionths(), minimum, maximum));
            constraints.add(new HydrologyReconciliationPlanV2.Constraint(
                    "reconcile.lake." + plan.featureId() + ".spill",
                    HydrologyReconciliationPlanV2.ConstraintKind.LAKE_SPILL,
                    plan.featureId(), surface, spill,
                    0L, 0L,
                    HydrologyReconciliationPlanV2.CorrectionPolicy.ADJUST_RIGHT,
                    MAXIMUM_ADJUSTMENT_MILLIONTHS));
        }
    }

    private static void compileDeltaConnections(
            List<DeltaPlanV2> plans,
            List<HydrologyReconciliationPlanV2.VariableDescriptor> variables,
            List<HydrologyReconciliationPlanV2.Constraint> constraints
    ) {
        for (DeltaPlanV2 plan : plans) {
            for (int index = 0; index < plan.branches().size(); index++) {
                String variable = "delta." + plan.featureId() + ".mouth." + sequence(index);
                variables.add(connection(variable, plan.featureId()));
                constraints.add(connectionConstraint(
                        "reconcile.delta." + plan.featureId() + ".mouth." + sequence(index),
                        HydrologyReconciliationPlanV2.ConstraintKind.DELTA_MOUTH,
                        plan.featureId(), variable));
            }
        }
    }

    private static void compileTidalConnections(
            List<TidalChannelPlanV2> plans,
            List<HydrologyReconciliationPlanV2.VariableDescriptor> variables,
            List<HydrologyReconciliationPlanV2.Constraint> constraints
    ) {
        for (TidalChannelPlanV2 plan : plans) {
            String variable = "tidal." + plan.featureId() + ".marine-connection";
            variables.add(connection(variable, plan.featureId()));
            constraints.add(connectionConstraint(
                    "reconcile.tidal." + plan.featureId() + ".marine-connection",
                    HydrologyReconciliationPlanV2.ConstraintKind.TIDAL_CONNECTION,
                    plan.featureId(), variable));
        }
    }

    private static void compileFjordConnections(
            List<FjordPlanV2> plans,
            List<HydrologyReconciliationPlanV2.VariableDescriptor> variables,
            List<HydrologyReconciliationPlanV2.Constraint> constraints
    ) {
        for (FjordPlanV2 plan : plans) {
            String variable = "fjord." + plan.featureId() + ".marine-connection";
            variables.add(connection(variable, plan.featureId()));
            constraints.add(connectionConstraint(
                    "reconcile.fjord." + plan.featureId() + ".marine-connection",
                    HydrologyReconciliationPlanV2.ConstraintKind.FJORD_CONNECTION,
                    plan.featureId(), variable));
        }
    }

    private static void compileWaterfalls(
            List<WaterfallPlanV2> plans,
            List<HydrologyReconciliationPlanV2.VariableDescriptor> variables,
            List<HydrologyReconciliationPlanV2.Constraint> constraints
    ) {
        for (WaterfallPlanV2 plan : plans) {
            long minimum = Math.multiplyExact((long) plan.minY(), TerrainIntentV2.FIXED_SCALE);
            long maximum = Math.multiplyExact((long) plan.maxY(), TerrainIntentV2.FIXED_SCALE);
            String lip = "waterfall." + plan.featureId() + ".lip";
            String base = "waterfall." + plan.featureId() + ".base";
            variables.add(variable(lip, plan.featureId(),
                    HydrologyReconciliationPlanV2.VariableKind.WATERFALL_LIP,
                    plan.lipBedYMillionths(), minimum, maximum));
            variables.add(variable(base, plan.featureId(),
                    HydrologyReconciliationPlanV2.VariableKind.WATERFALL_BASE,
                    plan.baseBedYMillionths(), minimum, maximum));
            long expected = Math.negateExact(Math.multiplyExact(
                    (long) plan.selectedDropBlocks(), TerrainIntentV2.FIXED_SCALE));
            constraints.add(new HydrologyReconciliationPlanV2.Constraint(
                    "reconcile.waterfall." + plan.featureId() + ".lip-base",
                    HydrologyReconciliationPlanV2.ConstraintKind.WATERFALL_LIP_BASE,
                    plan.featureId(), lip, base, expected, expected,
                    HydrologyReconciliationPlanV2.CorrectionPolicy.ADJUST_RIGHT,
                    MAXIMUM_ADJUSTMENT_MILLIONTHS));
        }
    }

    private static HydrologyReconciliationPlanV2.VariableDescriptor variable(
            String variableId,
            String featureId,
            HydrologyReconciliationPlanV2.VariableKind kind,
            long baseline,
            long minimum,
            long maximum
    ) {
        return new HydrologyReconciliationPlanV2.VariableDescriptor(
                variableId, featureId, kind, baseline, minimum, maximum);
    }

    private static HydrologyReconciliationPlanV2.VariableDescriptor connection(
            String variableId,
            String featureId
    ) {
        return variable(variableId, featureId,
                HydrologyReconciliationPlanV2.VariableKind.MARINE_CONNECTION, 1L, 0L, 1L);
    }

    private static HydrologyReconciliationPlanV2.Constraint connectionConstraint(
            String constraintId,
            HydrologyReconciliationPlanV2.ConstraintKind kind,
            String featureId,
            String variableId
    ) {
        return new HydrologyReconciliationPlanV2.Constraint(
                constraintId, kind, featureId, null, variableId, 1L, 1L,
                HydrologyReconciliationPlanV2.CorrectionPolicy.VERIFY_ONLY, 0L);
    }

    private static String riverVariable(String featureId, int index) {
        return "river." + featureId + ".bed." + sequence(index);
    }

    private static String sequence(int value) {
        return String.format(Locale.ROOT, "%04d", value);
    }
}
