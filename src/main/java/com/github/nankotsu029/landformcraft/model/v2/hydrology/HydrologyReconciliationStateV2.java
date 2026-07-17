package com.github.nankotsu029.landformcraft.model.v2.hydrology;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable observations presented to the bounded reconciliation stage. */
public record HydrologyReconciliationStateV2(
        String sourcePlanChecksum,
        List<VariableValue> values
) {
    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public HydrologyReconciliationStateV2 {
        if (sourcePlanChecksum == null || !CHECKSUM.matcher(sourcePlanChecksum).matches()) {
            throw new IllegalArgumentException("sourcePlanChecksum must be a lowercase SHA-256");
        }
        Objects.requireNonNull(values, "values");
        if (values.size() > HydrologyReconciliationPlanV2.MAXIMUM_VARIABLES
                || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("hydrology reconciliation state exceeds its trusted bound");
        }
        values = values.stream().sorted(Comparator.comparing(VariableValue::variableId)).toList();
        Set<String> ids = new HashSet<>();
        if (values.stream().anyMatch(value -> !ids.add(value.variableId()))) {
            throw new IllegalArgumentException("duplicate hydrology reconciliation state variable");
        }
    }

    public record VariableValue(String variableId, long valueMillionths, boolean hardLocked)
            implements HydrologyReconciliationArtifactV2.StateValue {
        public VariableValue {
            if (variableId == null || !QUALIFIED.matcher(variableId).matches()) {
                throw new IllegalArgumentException("hydrology reconciliation variableId is invalid");
            }
        }

        public VariableValue withValue(long value) {
            return new VariableValue(variableId, value, hardLocked);
        }
    }
}
