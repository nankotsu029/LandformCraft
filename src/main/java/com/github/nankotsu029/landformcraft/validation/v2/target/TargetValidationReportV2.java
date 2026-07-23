package com.github.nankotsu029.landformcraft.validation.v2.target;

import java.util.List;
import java.util.Objects;

/**
 * V2-18-04 result of running the target-driven validation framework over a blueprint's
 * {@code ValidationTargetV2} list: every target that had a registered evaluator, in blueprint order.
 * The export path rejects on {@link #hasHardViolation()}; {@link #softViolations()} are advisory.
 */
public record TargetValidationReportV2(List<TargetEvaluationV2> evaluations) {
    public TargetValidationReportV2 {
        evaluations = List.copyOf(Objects.requireNonNull(evaluations, "evaluations"));
    }

    public List<TargetEvaluationV2> hardViolations() {
        return evaluations.stream().filter(TargetEvaluationV2::hardViolation).toList();
    }

    public List<TargetEvaluationV2> softViolations() {
        return evaluations.stream().filter(TargetEvaluationV2::softViolation).toList();
    }

    public boolean hasHardViolation() {
        return evaluations.stream().anyMatch(TargetEvaluationV2::hardViolation);
    }
}
