package com.github.nankotsu029.landformcraft.validation.v2.target;

import com.github.nankotsu029.landformcraft.model.v2.ValidationTargetV2;

/**
 * Evaluates one class of compiled {@link ValidationTargetV2} (keyed by its {@code ruleId}) against a
 * finalized field sampler (V2-18-04). Each evaluator pins its measurement contract to a monotonically
 * increasing {@link #evaluatorVersion()} so a measurement-region or method change is an explicit,
 * reviewable version bump rather than a silent behavioural drift.
 */
public interface ValidationTargetEvaluatorV2 {
    /** The {@code ValidationTargetV2.ruleId()} this evaluator consumes (e.g. {@code v2.edge-classification}). */
    String ruleId();

    /** Versioned measurement contract. Bump when the measured region or method changes. */
    int evaluatorVersion();

    /** Measures the target and reports whether it is satisfied. Must be deterministic. */
    TargetEvaluationV2 evaluate(ValidationTargetV2 target, ValidationFieldSamplerV2 fields);
}
