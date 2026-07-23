package com.github.nankotsu029.landformcraft.validation.v2.target;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Objects;

/**
 * One evaluated {@code ValidationTargetV2} (V2-18-04): the measured value, the versioned evaluator
 * that produced it, and whether the target is satisfied. HARD dissatisfaction gates export; SOFT
 * dissatisfaction is advisory.
 */
public record TargetEvaluationV2(
        String targetId,
        String sourceConstraintId,
        String ruleId,
        int evaluatorVersion,
        TerrainIntentV2.Strength hardness,
        long measuredMillionths,
        long expectedMinimumMillionths,
        boolean satisfied,
        String detail
) {
    public TargetEvaluationV2 {
        targetId = Objects.requireNonNull(targetId, "targetId");
        sourceConstraintId = Objects.requireNonNull(sourceConstraintId, "sourceConstraintId");
        ruleId = Objects.requireNonNull(ruleId, "ruleId");
        hardness = Objects.requireNonNull(hardness, "hardness");
        detail = Objects.requireNonNull(detail, "detail");
        if (evaluatorVersion < 1) {
            throw new IllegalArgumentException("evaluatorVersion must be positive");
        }
    }

    public boolean hardViolation() {
        return !satisfied && hardness == TerrainIntentV2.Strength.HARD;
    }

    public boolean softViolation() {
        return !satisfied && hardness == TerrainIntentV2.Strength.SOFT;
    }
}
