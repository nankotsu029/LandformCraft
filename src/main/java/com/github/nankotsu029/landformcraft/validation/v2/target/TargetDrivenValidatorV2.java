package com.github.nankotsu029.landformcraft.validation.v2.target;

import com.github.nankotsu029.landformcraft.model.v2.ValidationTargetV2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * V2-18-04 common path that consumes a blueprint's compiled {@link ValidationTargetV2} list.
 *
 * <p>The V2-18 macro-foundation audit found that map-level HARD input compiles to validation targets
 * no validator ever reads. This framework closes that gap in a rule-driven, extensible way: each
 * target is dispatched to the evaluator registered for its {@code ruleId}; targets with no registered
 * evaluator are left untouched (they are handled elsewhere — coastal/hydrology geometry targets by
 * {@code CoastalValidatorV2} and the hydrology validators — or are still awaiting a later Task). The
 * built-in registry ships only the EDGE evaluator ({@code v2.edge-classification}); other user-authored
 * rules such as {@code v2.metric-range} remain unevaluated until their own Task.</p>
 *
 * <p>{@link #BUILT_IN_EVALUATED_CONSTRAINT_RULES} is the single authority for "which user-constraint
 * rule ids now have an evaluator", so the HARD preflight gate stops rejecting them pre-generation and
 * the report-only contribution diagnostic stops listing them as unconsumed, in lock-step with this
 * framework gaining the evaluator.</p>
 */
public final class TargetDrivenValidatorV2 {
    /** User-constraint rule ids the built-in framework can evaluate. Moves as one unit with the registry. */
    public static final Set<String> BUILT_IN_EVALUATED_CONSTRAINT_RULES =
            Set.of(EdgeClassificationEvaluatorV2.RULE_ID);

    private final Map<String, ValidationTargetEvaluatorV2> evaluators;

    /** The built-in registry: EDGE only in V2-18-04. Later Tasks add more evaluators. */
    public static TargetDrivenValidatorV2 builtIn() {
        return new TargetDrivenValidatorV2(List.of(new EdgeClassificationEvaluatorV2()));
    }

    TargetDrivenValidatorV2(List<ValidationTargetEvaluatorV2> evaluators) {
        Objects.requireNonNull(evaluators, "evaluators");
        Map<String, ValidationTargetEvaluatorV2> byRule = new LinkedHashMap<>();
        for (ValidationTargetEvaluatorV2 evaluator : evaluators) {
            Objects.requireNonNull(evaluator, "evaluator");
            if (byRule.put(evaluator.ruleId(), evaluator) != null) {
                throw new IllegalArgumentException("duplicate evaluator for rule " + evaluator.ruleId());
            }
        }
        this.evaluators = Map.copyOf(byRule);
    }

    /** Evaluates every target that has a registered evaluator, in blueprint order. Deterministic. */
    public TargetValidationReportV2 validate(
            List<ValidationTargetV2> targets,
            ValidationFieldSamplerV2 fields
    ) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(fields, "fields");
        List<TargetEvaluationV2> evaluations = targets.stream()
                .filter(target -> evaluators.containsKey(target.ruleId()))
                .map(target -> evaluators.get(target.ruleId()).evaluate(target, fields))
                .toList();
        return new TargetValidationReportV2(evaluations);
    }

    /** Rule ids this instance can evaluate, deterministically ordered. */
    public Set<String> evaluatedRuleIds() {
        return new TreeSet<>(evaluators.keySet());
    }
}
