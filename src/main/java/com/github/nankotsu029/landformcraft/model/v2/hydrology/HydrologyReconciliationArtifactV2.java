package com.github.nankotsu029.landformcraft.model.v2.hydrology;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Canonical V2-3-12 result containing final scalar state, residuals, and stable failure reasons. */
public record HydrologyReconciliationArtifactV2(
        int artifactVersion,
        String algorithmVersion,
        String scanOrderVersion,
        String sourceBlueprintChecksum,
        String sourcePlanChecksum,
        String sourceStateChecksum,
        Status status,
        int iterationsExecuted,
        List<FinalValue> finalValues,
        List<Residual> residuals,
        ResourceUsage resources,
        String finalStateChecksum,
        String resultChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String RESOURCE_VERSION = "hydrology-reconciliation-resource-v1";

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final byte[] STATE_DOMAIN = "HYDROLOGY_RECONCILIATION_STATE_V1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RESULT_DOMAIN = "HYDROLOGY_RECONCILIATION_RESULT_V1".getBytes(StandardCharsets.US_ASCII);

    public HydrologyReconciliationArtifactV2 {
        if (artifactVersion != VERSION) {
            throw new IllegalArgumentException("hydrology reconciliation artifactVersion must be 1");
        }
        if (!HydrologyReconciliationPlanV2.ALGORITHM_VERSION.equals(algorithmVersion)
                || !HydrologyReconciliationPlanV2.SCAN_ORDER_VERSION.equals(scanOrderVersion)) {
            throw new IllegalArgumentException("unsupported hydrology reconciliation artifact algorithm");
        }
        sourceBlueprintChecksum = checksum(sourceBlueprintChecksum, "sourceBlueprintChecksum");
        sourcePlanChecksum = checksum(sourcePlanChecksum, "sourcePlanChecksum");
        sourceStateChecksum = checksum(sourceStateChecksum, "sourceStateChecksum");
        Objects.requireNonNull(status, "status");
        if (iterationsExecuted < 0 || iterationsExecuted > HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS) {
            throw new IllegalArgumentException("hydrology reconciliation iteration count is invalid");
        }
        finalValues = sorted(finalValues, "finalValues", HydrologyReconciliationPlanV2.MAXIMUM_VARIABLES,
                Comparator.comparing(FinalValue::variableId));
        residuals = sorted(residuals, "residuals", HydrologyReconciliationPlanV2.MAXIMUM_CONSTRAINTS,
                Comparator.comparingInt((Residual residual) -> kindOrder(residual.kind()))
                        .thenComparing(Residual::featureId)
                        .thenComparing(Residual::constraintId));
        Objects.requireNonNull(resources, "resources");
        finalStateChecksum = checksum(finalStateChecksum, "finalStateChecksum");
        resultChecksum = checksum(resultChecksum, "resultChecksum");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validate(status, iterationsExecuted, finalValues, residuals, resources, finalStateChecksum);
        String expectedResult = computeResultChecksum(
                sourceBlueprintChecksum, sourcePlanChecksum, sourceStateChecksum, status, iterationsExecuted,
                finalValues, residuals, resources, finalStateChecksum);
        if (!expectedResult.equals(resultChecksum)) {
            throw new IllegalArgumentException("hydrology reconciliation result checksum mismatch");
        }
    }

    public HydrologyReconciliationArtifactV2 withCanonicalChecksum(String checksum) {
        return new HydrologyReconciliationArtifactV2(
                artifactVersion, algorithmVersion, scanOrderVersion, sourceBlueprintChecksum,
                sourcePlanChecksum, sourceStateChecksum, status, iterationsExecuted, finalValues,
                residuals, resources, finalStateChecksum, resultChecksum, checksum);
    }

    public enum Status { SATISFIED, FAILED }

    public enum FailureReason {
        NONE,
        HARD_CONFLICT,
        ADJUSTMENT_LIMIT,
        UNRECOVERABLE_CONNECTION,
        NON_CONVERGENCE
    }

    public record FinalValue(String variableId, long valueMillionths, boolean hardLocked) {
        public FinalValue {
            variableId = qualified(variableId, "variableId");
            if (valueMillionths < HydrologyReconciliationPlanV2.MINIMUM_FIXED_VALUE
                    || valueMillionths > HydrologyReconciliationPlanV2.MAXIMUM_FIXED_VALUE) {
                throw new IllegalArgumentException("hydrology reconciliation final value is outside trusted bounds");
            }
        }
    }

    public record Residual(
            String constraintId,
            HydrologyReconciliationPlanV2.ConstraintKind kind,
            String featureId,
            String leftVariableId,
            String rightVariableId,
            long minimumDeltaMillionths,
            long maximumDeltaMillionths,
            long actualDeltaMillionths,
            long residualMillionths,
            boolean satisfied,
            int correctionCount,
            FailureReason failureReason
    ) {
        public Residual {
            constraintId = qualified(constraintId, "constraintId");
            Objects.requireNonNull(kind, "kind");
            featureId = slug(featureId, "featureId");
            if (leftVariableId != null) leftVariableId = qualified(leftVariableId, "leftVariableId");
            rightVariableId = qualified(rightVariableId, "rightVariableId");
            if (minimumDeltaMillionths > maximumDeltaMillionths
                    || minimumDeltaMillionths < -HydrologyReconciliationPlanV2.MAXIMUM_FIXED_DELTA
                    || maximumDeltaMillionths > HydrologyReconciliationPlanV2.MAXIMUM_FIXED_DELTA
                    || actualDeltaMillionths < -HydrologyReconciliationPlanV2.MAXIMUM_FIXED_DELTA
                    || actualDeltaMillionths > HydrologyReconciliationPlanV2.MAXIMUM_FIXED_DELTA
                    || residualMillionths < 0L
                    || residualMillionths > 2L * HydrologyReconciliationPlanV2.MAXIMUM_FIXED_DELTA
                    || correctionCount < 0 || correctionCount > HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS) {
                throw new IllegalArgumentException("hydrology reconciliation residual is invalid");
            }
            long expectedResidual = residual(actualDeltaMillionths, minimumDeltaMillionths, maximumDeltaMillionths);
            if (residualMillionths != expectedResidual || satisfied != (expectedResidual == 0L)) {
                throw new IllegalArgumentException("hydrology reconciliation residual evidence is inconsistent");
            }
            Objects.requireNonNull(failureReason, "failureReason");
            if (satisfied != (failureReason == FailureReason.NONE)) {
                throw new IllegalArgumentException("hydrology reconciliation failure reason is inconsistent");
            }
        }
    }

    public record ResourceUsage(
            String resourceVersion,
            int variableCount,
            int constraintCount,
            int iterationsExecuted,
            long workUnits,
            long maximumWorkUnits,
            long peakWorkingBytes,
            long maximumWorkingBytes,
            long estimatedArtifactBytes,
            long maximumArtifactBytes
    ) {
        public ResourceUsage {
            if (!RESOURCE_VERSION.equals(resourceVersion)
                    || variableCount < 0 || variableCount > HydrologyReconciliationPlanV2.MAXIMUM_VARIABLES
                    || constraintCount < 0 || constraintCount > HydrologyReconciliationPlanV2.MAXIMUM_CONSTRAINTS
                    || iterationsExecuted < 0
                    || iterationsExecuted > HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS
                    || workUnits < 0 || workUnits > maximumWorkUnits
                    || maximumWorkUnits < 1
                    || maximumWorkUnits > HydrologyReconciliationPlanV2.MAXIMUM_WORK_UNITS
                    || peakWorkingBytes < 1 || peakWorkingBytes > maximumWorkingBytes
                    || maximumWorkingBytes < 1
                    || maximumWorkingBytes > HydrologyReconciliationPlanV2.MAXIMUM_WORKING_BYTES
                    || estimatedArtifactBytes < 1 || estimatedArtifactBytes > maximumArtifactBytes
                    || maximumArtifactBytes < 1
                    || maximumArtifactBytes > HydrologyReconciliationPlanV2.MAXIMUM_ARTIFACT_BYTES) {
                throw new IllegalArgumentException("hydrology reconciliation resource usage is invalid");
            }
        }
    }

    public static long residual(long actual, long minimum, long maximum) {
        if (actual < minimum) return Math.subtractExact(minimum, actual);
        if (actual > maximum) return Math.subtractExact(actual, maximum);
        return 0L;
    }

    public static String computeStateChecksum(List<? extends StateValue> values) {
        MessageDigest digest = sha256();
        digest.update(STATE_DOMAIN);
        List<? extends StateValue> ordered = values.stream()
                .sorted(Comparator.comparing(StateValue::variableId)).toList();
        updateInt(digest, ordered.size());
        for (StateValue value : ordered) {
            updateString(digest, value.variableId());
            updateLong(digest, value.valueMillionths());
            digest.update((byte) (value.hardLocked() ? 1 : 0));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String computeResultChecksum(
            String sourceBlueprintChecksum,
            String sourcePlanChecksum,
            String sourceStateChecksum,
            Status status,
            int iterationsExecuted,
            List<FinalValue> finalValues,
            List<Residual> residuals,
            ResourceUsage resources,
            String finalStateChecksum
    ) {
        MessageDigest digest = sha256();
        digest.update(RESULT_DOMAIN);
        updateString(digest, HydrologyReconciliationPlanV2.ALGORITHM_VERSION);
        updateString(digest, HydrologyReconciliationPlanV2.SCAN_ORDER_VERSION);
        updateString(digest, sourceBlueprintChecksum);
        updateString(digest, sourcePlanChecksum);
        updateString(digest, sourceStateChecksum);
        updateString(digest, status.name());
        updateInt(digest, iterationsExecuted);
        List<FinalValue> orderedValues = finalValues.stream()
                .sorted(Comparator.comparing(FinalValue::variableId)).toList();
        updateInt(digest, orderedValues.size());
        for (FinalValue value : orderedValues) {
            updateString(digest, value.variableId());
            updateLong(digest, value.valueMillionths());
            digest.update((byte) (value.hardLocked() ? 1 : 0));
        }
        List<Residual> orderedResiduals = residuals.stream()
                .sorted(Comparator.comparingInt((Residual residual) -> kindOrder(residual.kind()))
                        .thenComparing(Residual::featureId)
                        .thenComparing(Residual::constraintId))
                .toList();
        updateInt(digest, orderedResiduals.size());
        for (Residual residual : orderedResiduals) {
            updateString(digest, residual.constraintId());
            updateString(digest, residual.kind().name());
            updateString(digest, residual.featureId());
            updateString(digest, residual.leftVariableId() == null ? "" : residual.leftVariableId());
            updateString(digest, residual.rightVariableId());
            updateLong(digest, residual.minimumDeltaMillionths());
            updateLong(digest, residual.maximumDeltaMillionths());
            updateLong(digest, residual.actualDeltaMillionths());
            updateLong(digest, residual.residualMillionths());
            digest.update((byte) (residual.satisfied() ? 1 : 0));
            updateInt(digest, residual.correctionCount());
            updateString(digest, residual.failureReason().name());
        }
        updateString(digest, resources.resourceVersion());
        updateInt(digest, resources.variableCount());
        updateInt(digest, resources.constraintCount());
        updateInt(digest, resources.iterationsExecuted());
        updateLong(digest, resources.workUnits());
        updateLong(digest, resources.maximumWorkUnits());
        updateLong(digest, resources.peakWorkingBytes());
        updateLong(digest, resources.maximumWorkingBytes());
        updateLong(digest, resources.estimatedArtifactBytes());
        updateLong(digest, resources.maximumArtifactBytes());
        updateString(digest, finalStateChecksum);
        return HexFormat.of().formatHex(digest.digest());
    }

    public interface StateValue {
        String variableId();

        long valueMillionths();

        boolean hardLocked();
    }

    private static void validate(
            Status status,
            int iterations,
            List<FinalValue> finalValues,
            List<Residual> residuals,
            ResourceUsage resources,
            String finalStateChecksum
    ) {
        Set<String> valueIds = new HashSet<>();
        Map<String, FinalValue> byId = new HashMap<>();
        for (FinalValue value : finalValues) {
            if (!valueIds.add(value.variableId())) {
                throw new IllegalArgumentException("duplicate hydrology reconciliation final variable");
            }
            byId.put(value.variableId(), value);
        }
        Set<String> residualIds = new HashSet<>();
        for (Residual residual : residuals) {
            if (!residualIds.add(residual.constraintId())) {
                throw new IllegalArgumentException("duplicate hydrology reconciliation residual");
            }
            FinalValue right = byId.get(residual.rightVariableId());
            FinalValue left = residual.leftVariableId() == null ? null : byId.get(residual.leftVariableId());
            if (right == null || (residual.leftVariableId() != null && left == null)) {
                throw new IllegalArgumentException("hydrology reconciliation residual references unknown final state");
            }
            long actual = left == null
                    ? right.valueMillionths()
                    : Math.subtractExact(right.valueMillionths(), left.valueMillionths());
            if (actual != residual.actualDeltaMillionths()) {
                throw new IllegalArgumentException("hydrology reconciliation actual delta is inconsistent");
            }
        }
        boolean allSatisfied = residuals.stream().allMatch(Residual::satisfied);
        if ((status == Status.SATISFIED) != allSatisfied
                || (residuals.isEmpty() ? iterations != 0 : iterations != HydrologyReconciliationPlanV2.MAXIMUM_ITERATIONS)
                || resources.variableCount() != finalValues.size()
                || resources.constraintCount() != residuals.size()
                || resources.iterationsExecuted() != iterations) {
            throw new IllegalArgumentException("hydrology reconciliation artifact status or resources are inconsistent");
        }
        String expectedState = computeStateChecksum(finalValues.stream()
                .map(value -> new StateValueAdapter(value.variableId(), value.valueMillionths(), value.hardLocked()))
                .toList());
        if (!expectedState.equals(finalStateChecksum)) {
            throw new IllegalArgumentException("hydrology reconciliation final state checksum mismatch");
        }
    }

    private record StateValueAdapter(String variableId, long valueMillionths, boolean hardLocked)
            implements StateValue {
    }

    private static int kindOrder(HydrologyReconciliationPlanV2.ConstraintKind kind) {
        return switch (kind) {
            case REACH_BED -> 0;
            case LAKE_SPILL -> 1;
            case DELTA_MOUTH -> 2;
            case TIDAL_CONNECTION -> 3;
            case FJORD_CONNECTION -> 4;
            case WATERFALL_LIP_BASE -> 5;
        };
    }

    private static <T> List<T> sorted(List<T> values, String field, int maximum, Comparator<T> comparator) {
        Objects.requireNonNull(values, field);
        if (values.size() > maximum || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " exceeds its trusted bound");
        }
        return values.stream().sorted(comparator).toList();
    }

    private static String qualified(String value, String field) {
        if (value == null || !QUALIFIED.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String slug(String value, String field) {
        if (value == null || !SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String checksum(String value, String field) {
        if (value == null || !CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateLong(MessageDigest digest, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (value >>> shift));
        }
    }
}
