package com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationStateV2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Integer-only, fixed-three-pass reconciliation over a frozen global scalar state. */
public final class BoundedHydrologyReconcilerV2 {

    public HydrologyReconciliationArtifactV2 reconcile(
            String sourceBlueprintChecksum,
            HydrologyReconciliationPlanV2 plan,
            HydrologyReconciliationStateV2 state,
            CancellationToken token
    ) {
        Objects.requireNonNull(sourceBlueprintChecksum, "sourceBlueprintChecksum");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(token, "token");
        token.throwIfCancellationRequested();
        validateInput(plan, state);

        Map<String, HydrologyReconciliationPlanV2.VariableDescriptor> descriptors = new HashMap<>();
        plan.variables().forEach(variable -> descriptors.put(variable.variableId(), variable));
        Map<String, MutableValue> values = new HashMap<>();
        state.values().forEach(value -> values.put(
                value.variableId(), new MutableValue(value.valueMillionths(), value.hardLocked())));
        Map<String, Integer> correctionCounts = new HashMap<>();
        Map<String, HydrologyReconciliationArtifactV2.FailureReason> blockedReasons = new HashMap<>();

        int iterations = plan.constraints().isEmpty() ? 0 : HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS;
        for (int iteration = 0; iteration < iterations; iteration++) {
            token.throwIfCancellationRequested();
            for (HydrologyReconciliationPlanV2.Constraint constraint : plan.constraints()) {
                token.throwIfCancellationRequested();
                long actual = delta(constraint, values);
                if (HydrologyReconciliationArtifactV2.residual(
                        actual, constraint.minimumDeltaMillionths(), constraint.maximumDeltaMillionths()) == 0L) {
                    blockedReasons.remove(constraint.constraintId());
                    continue;
                }
                if (constraint.correctionPolicy() == HydrologyReconciliationPlanV2.CorrectionPolicy.VERIFY_ONLY) {
                    blockedReasons.put(constraint.constraintId(),
                            HydrologyReconciliationArtifactV2.FailureReason.UNRECOVERABLE_CONNECTION);
                    continue;
                }
                MutableValue right = values.get(constraint.rightVariableId());
                if (right.hardLocked) {
                    blockedReasons.put(constraint.constraintId(),
                            HydrologyReconciliationArtifactV2.FailureReason.HARD_CONFLICT);
                    continue;
                }
                long desiredDelta = actual < constraint.minimumDeltaMillionths()
                        ? constraint.minimumDeltaMillionths()
                        : constraint.maximumDeltaMillionths();
                long left = values.get(constraint.leftVariableId()).value;
                long desiredRight;
                long adjustment;
                try {
                    desiredRight = Math.addExact(left, desiredDelta);
                    adjustment = absoluteDifference(desiredRight, right.value);
                } catch (ArithmeticException exception) {
                    blockedReasons.put(constraint.constraintId(),
                            HydrologyReconciliationArtifactV2.FailureReason.HARD_CONFLICT);
                    continue;
                }
                if (adjustment > constraint.maximumAdjustmentMillionths()) {
                    blockedReasons.put(constraint.constraintId(),
                            HydrologyReconciliationArtifactV2.FailureReason.ADJUSTMENT_LIMIT);
                    continue;
                }
                HydrologyReconciliationPlanV2.VariableDescriptor descriptor =
                        descriptors.get(constraint.rightVariableId());
                if (desiredRight < descriptor.minimumValueMillionths()
                        || desiredRight > descriptor.maximumValueMillionths()) {
                    blockedReasons.put(constraint.constraintId(),
                            HydrologyReconciliationArtifactV2.FailureReason.HARD_CONFLICT);
                    continue;
                }
                right.value = desiredRight;
                correctionCounts.merge(constraint.constraintId(), 1, Math::addExact);
                blockedReasons.remove(constraint.constraintId());
            }
        }
        token.throwIfCancellationRequested();

        List<HydrologyReconciliationArtifactV2.FinalValue> finalValues = plan.variables().stream()
                .map(variable -> {
                    MutableValue value = values.get(variable.variableId());
                    return new HydrologyReconciliationArtifactV2.FinalValue(
                            variable.variableId(), value.value, value.hardLocked);
                })
                .toList();
        List<HydrologyReconciliationArtifactV2.Residual> residuals = new ArrayList<>();
        for (HydrologyReconciliationPlanV2.Constraint constraint : plan.constraints()) {
            long actual = delta(constraint, values);
            long residual = HydrologyReconciliationArtifactV2.residual(
                    actual, constraint.minimumDeltaMillionths(), constraint.maximumDeltaMillionths());
            boolean satisfied = residual == 0L;
            HydrologyReconciliationArtifactV2.FailureReason reason = satisfied
                    ? HydrologyReconciliationArtifactV2.FailureReason.NONE
                    : blockedReasons.getOrDefault(
                            constraint.constraintId(),
                            HydrologyReconciliationArtifactV2.FailureReason.NON_CONVERGENCE);
            residuals.add(new HydrologyReconciliationArtifactV2.Residual(
                    constraint.constraintId(), constraint.kind(), constraint.featureId(),
                    constraint.leftVariableId(), constraint.rightVariableId(),
                    constraint.minimumDeltaMillionths(), constraint.maximumDeltaMillionths(), actual,
                    residual, satisfied, correctionCounts.getOrDefault(constraint.constraintId(), 0), reason));
        }
        HydrologyReconciliationArtifactV2.Status status = residuals.stream().allMatch(
                HydrologyReconciliationArtifactV2.Residual::satisfied)
                ? HydrologyReconciliationArtifactV2.Status.SATISFIED
                : HydrologyReconciliationArtifactV2.Status.FAILED;
        HydrologyReconciliationPlanV2.WorkBudget budget = plan.budget();
        HydrologyReconciliationArtifactV2.ResourceUsage resources =
                new HydrologyReconciliationArtifactV2.ResourceUsage(
                        HydrologyReconciliationArtifactV2.RESOURCE_VERSION,
                        finalValues.size(), residuals.size(), iterations,
                        budget.estimatedWorkUnits(), budget.maximumWorkUnits(),
                        budget.estimatedWorkingBytes(), budget.maximumWorkingBytes(),
                        budget.estimatedArtifactBytes(), budget.maximumArtifactBytes());
        String sourceStateChecksum = HydrologyReconciliationArtifactV2.computeStateChecksum(state.values());
        String finalStateChecksum = HydrologyReconciliationArtifactV2.computeStateChecksum(finalValues.stream()
                .map(value -> new StateAdapter(value.variableId(), value.valueMillionths(), value.hardLocked()))
                .toList());
        String resultChecksum = HydrologyReconciliationArtifactV2.computeResultChecksum(
                sourceBlueprintChecksum, plan.canonicalChecksum(), sourceStateChecksum, status, iterations,
                finalValues, residuals, resources, finalStateChecksum);
        token.throwIfCancellationRequested();
        return new HydrologyReconciliationArtifactV2(
                HydrologyReconciliationArtifactV2.VERSION,
                HydrologyReconciliationPlanV2.ALGORITHM_VERSION,
                HydrologyReconciliationPlanV2.SCAN_ORDER_VERSION,
                sourceBlueprintChecksum,
                plan.canonicalChecksum(),
                sourceStateChecksum,
                status,
                iterations,
                finalValues,
                residuals,
                resources,
                finalStateChecksum,
                resultChecksum,
                "0".repeat(64));
    }

    private static void validateInput(
            HydrologyReconciliationPlanV2 plan,
            HydrologyReconciliationStateV2 state
    ) {
        if (!plan.canonicalChecksum().equals(state.sourcePlanChecksum())
                || state.values().size() != plan.variables().size()) {
            throw new HydrologyReconciliationException(
                    "v2.hydrology-reconciliation-input",
                    "hydrology reconciliation state does not match its frozen plan");
        }
        Map<String, HydrologyReconciliationStateV2.VariableValue> byId = new HashMap<>();
        state.values().forEach(value -> byId.put(value.variableId(), value));
        for (HydrologyReconciliationPlanV2.VariableDescriptor descriptor : plan.variables()) {
            HydrologyReconciliationStateV2.VariableValue value = byId.get(descriptor.variableId());
            if (value == null || value.valueMillionths() < descriptor.minimumValueMillionths()
                    || value.valueMillionths() > descriptor.maximumValueMillionths()) {
                throw new HydrologyReconciliationException(
                        "v2.hydrology-reconciliation-input",
                        "hydrology reconciliation state contains a missing or out-of-range variable");
            }
        }
    }

    private static long delta(
            HydrologyReconciliationPlanV2.Constraint constraint,
            Map<String, MutableValue> values
    ) {
        long right = values.get(constraint.rightVariableId()).value;
        if (constraint.leftVariableId() == null) return right;
        return Math.subtractExact(right, values.get(constraint.leftVariableId()).value);
    }

    private static long absoluteDifference(long first, long second) {
        long difference = Math.subtractExact(first, second);
        if (difference == Long.MIN_VALUE) throw new ArithmeticException("absolute difference overflow");
        return Math.abs(difference);
    }

    private static final class MutableValue {
        private long value;
        private final boolean hardLocked;

        private MutableValue(long value, boolean hardLocked) {
            this.value = value;
            this.hardLocked = hardLocked;
        }
    }

    private record StateAdapter(String variableId, long valueMillionths, boolean hardLocked)
            implements HydrologyReconciliationArtifactV2.StateValue {
    }
}
