package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.List;

/** Compiled validation target. An empty featureIds list denotes world scope. */
public record ValidationTargetV2(
        String targetId,
        String sourceConstraintId,
        List<String> featureIds,
        String ruleId,
        int ruleVersion,
        TerrainIntentV2.Strength hardness,
        int weightMillionths,
        String metric,
        TerrainIntentV2.FixedRange expected,
        long toleranceMillionths,
        List<String> requiredFields,
        String diagnosticLayerId
) {
    public ValidationTargetV2 {
        targetId = V2Validation.slug(targetId, "targetId");
        sourceConstraintId = V2Validation.slug(sourceConstraintId, "sourceConstraintId");
        featureIds = V2Validation.sorted(featureIds, "featureIds", 16, Comparator.naturalOrder()).stream()
                .map(value -> V2Validation.slug(value, "featureId")).toList();
        ruleId = V2Validation.qualifiedId(ruleId, "ruleId");
        if (ruleVersion < 1) throw new IllegalArgumentException("ruleVersion must be positive");
        if (hardness == null || expected == null) throw new NullPointerException("target hardness and expected are required");
        if (hardness == TerrainIntentV2.Strength.HARD && weightMillionths != 0) throw new IllegalArgumentException("HARD target must not have weight");
        if (hardness == TerrainIntentV2.Strength.SOFT && (weightMillionths < 1 || weightMillionths > TerrainIntentV2.FIXED_SCALE)) throw new IllegalArgumentException("SOFT target requires weight");
        metric = V2Validation.qualifiedId(metric, "metric");
        if (toleranceMillionths < 0) throw new IllegalArgumentException("tolerance must be non-negative");
        requiredFields = V2Validation.sorted(requiredFields, "requiredFields", 32, Comparator.naturalOrder()).stream()
                .map(value -> V2Validation.qualifiedId(value, "required field")).toList();
        diagnosticLayerId = V2Validation.qualifiedId(diagnosticLayerId, "diagnosticLayerId");
    }
}
