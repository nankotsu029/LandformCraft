package com.github.nankotsu029.landformcraft.model.v2.hydrology;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Frozen V2-3-12 contract for bounded post-regional hydrology reconciliation.
 * The plan contains scalar descriptors only; full-resolution fields remain outside Blueprint JSON.
 */
public record HydrologyReconciliationPlanV2(
        int planVersion,
        String algorithmVersion,
        String scanOrderVersion,
        String moduleId,
        String moduleVersion,
        String stageId,
        String sourceHydrologyPlanChecksum,
        int maximumIterations,
        List<VariableDescriptor> variables,
        List<Constraint> constraints,
        WorkBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String ALGORITHM_VERSION = "hydrology-reconcile-fixed-v1";
    public static final String SCAN_ORDER_VERSION = "kind-feature-constraint-v3";
    public static final String BUDGET_VERSION = "hydrology-reconciliation-budget-v1";
    public static final int MAXIMUM_ITERATIONS = 3;
    public static final int MAXIMUM_VARIABLES = 8_192;
    public static final int MAXIMUM_CONSTRAINTS = 8_192;
    public static final long MAXIMUM_WORK_UNITS = 100_000_000L;
    public static final long MAXIMUM_WORKING_BYTES = 256L * 1024L * 1024L;
    public static final long MAXIMUM_ARTIFACT_BYTES = 4L * 1024L * 1024L;
    public static final long MINIMUM_FIXED_VALUE = -512_000_000L;
    public static final long MAXIMUM_FIXED_VALUE = 1_024_000_000L;
    public static final long MAXIMUM_FIXED_DELTA = 1_536_000_000L;
    public static final long MAXIMUM_ADJUSTMENT = 512_000_000L;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public HydrologyReconciliationPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("hydrology reconciliation planVersion must be 1");
        }
        algorithmVersion = exact(algorithmVersion, ALGORITHM_VERSION, "algorithmVersion");
        scanOrderVersion = exact(scanOrderVersion, SCAN_ORDER_VERSION, "scanOrderVersion");
        moduleId = qualified(moduleId, "moduleId");
        moduleVersion = nonBlank(moduleVersion, "moduleVersion", 64);
        stageId = qualified(stageId, "stageId");
        sourceHydrologyPlanChecksum = checksum(sourceHydrologyPlanChecksum, "sourceHydrologyPlanChecksum");
        if (maximumIterations != MAXIMUM_ITERATIONS) {
            throw new IllegalArgumentException("hydrology reconciliation iteration count must be 3");
        }
        variables = immutableSorted(
                variables, "variables", MAXIMUM_VARIABLES, Comparator.comparing(VariableDescriptor::variableId));
        constraints = immutableSorted(constraints, "constraints", MAXIMUM_CONSTRAINTS, CONSTRAINT_ORDER);
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validate(variables, constraints, budget);
    }

    public HydrologyReconciliationPlanV2 withCanonicalChecksum(String checksum) {
        return new HydrologyReconciliationPlanV2(
                planVersion, algorithmVersion, scanOrderVersion, moduleId, moduleVersion, stageId,
                sourceHydrologyPlanChecksum, maximumIterations, variables, constraints, budget, checksum);
    }

    public static long estimateWorkUnits(int variableCount, int constraintCount) {
        return Math.addExact(variableCount,
                Math.multiplyExact((long) constraintCount, MAXIMUM_ITERATIONS + 1L));
    }

    public static long estimateWorkingBytes(int variableCount, int constraintCount) {
        return Math.addExact(8_192L,
                Math.addExact(Math.multiplyExact((long) variableCount, 384L),
                        Math.multiplyExact((long) constraintCount, 512L)));
    }

    public static long estimateArtifactBytes(int variableCount, int constraintCount) {
        return Math.addExact(4_096L,
                Math.addExact(Math.multiplyExact((long) variableCount, 640L),
                        Math.multiplyExact((long) constraintCount, 1_024L)));
    }

    private static void validate(
            List<VariableDescriptor> variables,
            List<Constraint> constraints,
            WorkBudget budget
    ) {
        Map<String, VariableDescriptor> byId = new HashMap<>();
        for (VariableDescriptor variable : variables) {
            if (byId.put(variable.variableId(), variable) != null) {
                throw new IllegalArgumentException("duplicate hydrology reconciliation variable id");
            }
        }
        Set<String> constraintIds = new HashSet<>();
        Set<String> referencedVariableIds = new HashSet<>();
        for (Constraint constraint : constraints) {
            if (!constraintIds.add(constraint.constraintId())) {
                throw new IllegalArgumentException("duplicate hydrology reconciliation constraint id");
            }
            if (!byId.containsKey(constraint.rightVariableId())
                    || (constraint.leftVariableId() != null && !byId.containsKey(constraint.leftVariableId()))) {
                throw new IllegalArgumentException("hydrology reconciliation constraint references an unknown variable");
            }
            VariableDescriptor right = byId.get(constraint.rightVariableId());
            VariableDescriptor left = constraint.leftVariableId() == null
                    ? null : byId.get(constraint.leftVariableId());
            if (!right.featureId().equals(constraint.featureId())) {
                throw new IllegalArgumentException("hydrology reconciliation target feature mismatch");
            }
            if (left != null && !left.featureId().equals(constraint.featureId())) {
                throw new IllegalArgumentException("hydrology reconciliation source feature mismatch");
            }
            if (!constraintKindsMatchVariables(constraint.kind(), left, right)) {
                throw new IllegalArgumentException("hydrology reconciliation target kind mismatch");
            }
            if (left != null) referencedVariableIds.add(left.variableId());
            referencedVariableIds.add(right.variableId());
        }
        if (referencedVariableIds.size() != variables.size()) {
            throw new IllegalArgumentException("hydrology reconciliation plan contains an unused variable");
        }
        long expectedWork = estimateWorkUnits(variables.size(), constraints.size());
        long expectedWorkingBytes = estimateWorkingBytes(variables.size(), constraints.size());
        long expectedArtifactBytes = estimateArtifactBytes(variables.size(), constraints.size());
        if (budget.maximumIterations() != MAXIMUM_ITERATIONS
                || budget.variableCount() != variables.size()
                || budget.constraintCount() != constraints.size()
                || budget.estimatedWorkUnits() != expectedWork
                || budget.estimatedWorkingBytes() != expectedWorkingBytes
                || budget.estimatedArtifactBytes() != expectedArtifactBytes) {
            throw new IllegalArgumentException("hydrology reconciliation budget does not match plan cardinality");
        }
    }

    private static final Comparator<Constraint> CONSTRAINT_ORDER = Comparator
            .comparingInt((Constraint constraint) -> kindOrder(constraint.kind()))
            .thenComparing(Constraint::featureId)
            .thenComparing(Constraint::constraintId);

    private static int kindOrder(ConstraintKind kind) {
        return switch (kind) {
            case REACH_BED -> 0;
            case LAKE_SPILL -> 1;
            case DELTA_MOUTH -> 2;
            case TIDAL_CONNECTION -> 3;
            case MANGROVE_TIDAL_LINK -> 4;
            case REEF_LAGOON_PASS -> 5;
            case FJORD_CONNECTION -> 6;
            case WATERFALL_LIP_BASE -> 7;
        };
    }

    private static boolean constraintKindsMatchVariables(
            ConstraintKind kind,
            VariableDescriptor left,
            VariableDescriptor right
    ) {
        return switch (kind) {
            case REACH_BED -> left != null
                    && left.kind() == VariableKind.REACH_BED
                    && right.kind() == VariableKind.REACH_BED;
            case LAKE_SPILL -> left != null
                    && left.kind() == VariableKind.LAKE_SURFACE
                    && right.kind() == VariableKind.LAKE_SPILL;
            case DELTA_MOUTH, TIDAL_CONNECTION, FJORD_CONNECTION, MANGROVE_TIDAL_LINK, REEF_LAGOON_PASS -> left == null
                    && right.kind() == VariableKind.MARINE_CONNECTION;
            case WATERFALL_LIP_BASE -> left != null
                    && left.kind() == VariableKind.WATERFALL_LIP
                    && right.kind() == VariableKind.WATERFALL_BASE;
        };
    }

    public enum VariableKind {
        REACH_BED,
        LAKE_SURFACE,
        LAKE_SPILL,
        MARINE_CONNECTION,
        WATERFALL_LIP,
        WATERFALL_BASE
    }

    public enum ConstraintKind {
        REACH_BED,
        LAKE_SPILL,
        DELTA_MOUTH,
        TIDAL_CONNECTION,
        MANGROVE_TIDAL_LINK,
        REEF_LAGOON_PASS,
        FJORD_CONNECTION,
        WATERFALL_LIP_BASE
    }

    public enum CorrectionPolicy { ADJUST_RIGHT, VERIFY_ONLY }

    public record VariableDescriptor(
            String variableId,
            String featureId,
            VariableKind kind,
            long baselineValueMillionths,
            long minimumValueMillionths,
            long maximumValueMillionths
    ) {
        public VariableDescriptor {
            variableId = qualified(variableId, "variableId");
            featureId = slug(featureId, "featureId");
            Objects.requireNonNull(kind, "kind");
            if (minimumValueMillionths > baselineValueMillionths
                    || baselineValueMillionths > maximumValueMillionths
                    || minimumValueMillionths < MINIMUM_FIXED_VALUE
                    || maximumValueMillionths > MAXIMUM_FIXED_VALUE) {
                throw new IllegalArgumentException("hydrology reconciliation baseline is outside variable bounds");
            }
            if (kind == VariableKind.MARINE_CONNECTION
                    && (minimumValueMillionths != 0L || maximumValueMillionths != 1L)) {
                throw new IllegalArgumentException("marine connection variable must use the 0..1 domain");
            }
        }
    }

    public record Constraint(
            String constraintId,
            ConstraintKind kind,
            String featureId,
            String leftVariableId,
            String rightVariableId,
            long minimumDeltaMillionths,
            long maximumDeltaMillionths,
            CorrectionPolicy correctionPolicy,
            long maximumAdjustmentMillionths
    ) {
        public Constraint {
            constraintId = qualified(constraintId, "constraintId");
            Objects.requireNonNull(kind, "kind");
            featureId = slug(featureId, "featureId");
            if (leftVariableId != null) leftVariableId = qualified(leftVariableId, "leftVariableId");
            rightVariableId = qualified(rightVariableId, "rightVariableId");
            if (minimumDeltaMillionths > maximumDeltaMillionths) {
                throw new IllegalArgumentException("hydrology reconciliation delta range is inverted");
            }
            if (minimumDeltaMillionths < -MAXIMUM_FIXED_DELTA
                    || maximumDeltaMillionths > MAXIMUM_FIXED_DELTA
                    || maximumAdjustmentMillionths < 0L
                    || maximumAdjustmentMillionths > MAXIMUM_ADJUSTMENT) {
                throw new IllegalArgumentException("hydrology reconciliation constraint exceeds trusted bounds");
            }
            Objects.requireNonNull(correctionPolicy, "correctionPolicy");
            if (correctionPolicy == CorrectionPolicy.ADJUST_RIGHT) {
                if (leftVariableId == null || maximumAdjustmentMillionths < 1L) {
                    throw new IllegalArgumentException("adjustable reconciliation target needs a left variable and limit");
                }
            } else if (leftVariableId != null || maximumAdjustmentMillionths != 0L) {
                throw new IllegalArgumentException("verify-only reconciliation target must be unary and immutable");
            }
            boolean connectivity = switch (kind) {
                case DELTA_MOUTH, TIDAL_CONNECTION, FJORD_CONNECTION, MANGROVE_TIDAL_LINK, REEF_LAGOON_PASS -> true;
                default -> false;
            };
            if (connectivity != (correctionPolicy == CorrectionPolicy.VERIFY_ONLY)
                    || (connectivity && (minimumDeltaMillionths != 1L || maximumDeltaMillionths != 1L))) {
                throw new IllegalArgumentException("hydrology reconciliation correction policy does not match target kind");
            }
        }
    }

    public record WorkBudget(
            String budgetVersion,
            int maximumIterations,
            int variableCount,
            int constraintCount,
            long estimatedWorkUnits,
            long maximumWorkUnits,
            long estimatedWorkingBytes,
            long maximumWorkingBytes,
            long estimatedArtifactBytes,
            long maximumArtifactBytes
    ) {
        public WorkBudget {
            budgetVersion = exact(budgetVersion, BUDGET_VERSION, "budgetVersion");
            if (maximumIterations != MAXIMUM_ITERATIONS
                    || variableCount < 0 || variableCount > MAXIMUM_VARIABLES
                    || constraintCount < 0 || constraintCount > MAXIMUM_CONSTRAINTS
                    || estimatedWorkUnits < 0 || estimatedWorkUnits > maximumWorkUnits
                    || maximumWorkUnits < 1 || maximumWorkUnits > MAXIMUM_WORK_UNITS
                    || estimatedWorkingBytes < 1 || estimatedWorkingBytes > maximumWorkingBytes
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > MAXIMUM_WORKING_BYTES
                    || estimatedArtifactBytes < 1 || estimatedArtifactBytes > maximumArtifactBytes
                    || maximumArtifactBytes < 1 || maximumArtifactBytes > MAXIMUM_ARTIFACT_BYTES) {
                throw new IllegalArgumentException("hydrology reconciliation budget exceeds trusted bounds");
            }
        }
    }

    private static <T> List<T> immutableSorted(
            List<T> values,
            String field,
            int maximumSize,
            Comparator<T> comparator
    ) {
        Objects.requireNonNull(values, field);
        if (values.size() > maximumSize || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " exceeds its trusted bound");
        }
        return values.stream().sorted(comparator).toList();
    }

    private static String exact(String value, String expected, String field) {
        if (!expected.equals(value)) throw new IllegalArgumentException(field + " is unsupported");
        return value;
    }

    private static String nonBlank(String value, String field, int maximumLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String qualified(String value, String field) {
        value = nonBlank(value, field, 128);
        if (!QUALIFIED.matcher(value).matches()) throw new IllegalArgumentException(field + " is invalid");
        return value;
    }

    private static String slug(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!SLUG.matcher(value).matches()) throw new IllegalArgumentException(field + " is invalid");
        return value;
    }

    private static String checksum(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!CHECKSUM.matcher(value).matches()) throw new IllegalArgumentException(field + " is invalid");
        return value;
    }
}
