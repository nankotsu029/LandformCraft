package com.github.nankotsu029.landformcraft.model.v2.design;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * V2-19-08: the design-time support lint recorded on a published design package.
 *
 * <p>It holds the reachable set presented before the provider call ({@link DesignSupportSurfaceV2})
 * and the outcome of the dispatch dry-run performed on the provider's intent before publication. The
 * dry-run selects nothing and generates nothing; it only asks the production dispatch registry which
 * pipelines would accept the intent, and which declared kinds have no route.</p>
 *
 * <p>Every finding is {@link GateClassV2#NON_GATING} in this contract version, matching the fact
 * that the design path rejects nothing on lint grounds — an intent naming unreachable kinds is still
 * designed, verified, and published. The vocabulary matches {@code diagnostic-gate-contract-v1}, and
 * turning any rule into a gate requires separate human approval (Task Index §19.2).</p>
 */
public record DesignSupportLintV2(
        DesignSupportSurfaceV2 surface,
        DispatchDryRunV2 dispatchDryRun,
        List<String> selectablePipelineIds,
        List<String> declaredKinds,
        List<FindingV2> findings
) {
    /** How the export spine would treat a lint finding. Every current rule is {@code NON_GATING}. */
    public enum GateClassV2 { GATING, NON_GATING }

    /** Whether the designed intent would select at least one production pipeline. */
    public enum DispatchDryRunV2 { SELECTABLE, NOT_SELECTABLE }

    /** Declared kind has no production route and is not an accepted contract-only diagnostic input. */
    public static final String RULE_KIND_NOT_DISPATCHABLE = "v2.design.kind-not-publicly-dispatchable";
    /** Declared kind is accepted only as a contract-only companion; it selects no pipeline itself. */
    public static final String RULE_KIND_CONTRACT_ONLY = "v2.design.kind-contract-only";
    /** Declared kind is routed, but its route changes no block in the final canonical stream. */
    public static final String RULE_KIND_PLAN_ONLY = "v2.design.kind-plan-only";
    /** No registered capability set would accept the intent as a whole. */
    public static final String RULE_DISPATCH_UNSELECTABLE = "v2.design.dispatch-unselectable";
    /** A pipeline runtime requires companion kinds the intent does not declare (coastal four today). */
    public static final String RULE_COMPANION_MISSING = "v2.design.route-companion-missing";

    private static final Pattern RULE_ID = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern PIPELINE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final int MAXIMUM_FINDINGS = 64;
    private static final int MAXIMUM_KINDS = 64;
    private static final int MAXIMUM_PIPELINES = 16;

    public DesignSupportLintV2 {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(dispatchDryRun, "dispatchDryRun");
        selectablePipelineIds = requireSorted(
                selectablePipelineIds, "selectablePipelineIds", PIPELINE_ID, MAXIMUM_PIPELINES);
        declaredKinds = requireSorted(declaredKinds, "declaredKinds", IDENTIFIER, MAXIMUM_KINDS);
        Objects.requireNonNull(findings, "findings");
        findings = List.copyOf(findings);
        if (findings.size() > MAXIMUM_FINDINGS) {
            throw new IllegalArgumentException("findings must hold at most " + MAXIMUM_FINDINGS + " entries");
        }
        String previous = null;
        for (FindingV2 finding : findings) {
            Objects.requireNonNull(finding, "finding");
            String key = finding.ruleId() + "|" + String.join(",", finding.featureKinds());
            if (previous != null && previous.compareTo(key) >= 0) {
                throw new IllegalArgumentException("findings must be sorted and unique");
            }
            previous = key;
        }
        if (selectablePipelineIds.isEmpty()
                != (dispatchDryRun == DispatchDryRunV2.NOT_SELECTABLE)) {
            throw new IllegalArgumentException(
                    "selectablePipelineIds must be empty exactly when the dry-run is NOT_SELECTABLE");
        }
    }

    /** True when no finding would block export even if the contract were made fail-closed. */
    public boolean allFindingsAreNonGating() {
        return findings.stream().allMatch(finding -> finding.gateClass() == GateClassV2.NON_GATING);
    }

    /** Declared kinds carrying {@code ruleId}, in finding order. */
    public List<String> kindsWith(String ruleId) {
        Objects.requireNonNull(ruleId, "ruleId");
        return findings.stream()
                .filter(finding -> finding.ruleId().equals(ruleId))
                .flatMap(finding -> finding.featureKinds().stream())
                .sorted()
                .distinct()
                .toList();
    }

    /**
     * One lint observation. {@code featureKinds} is empty for intent-level rules and holds the
     * subject kinds otherwise; {@code detail} is operator-facing text with no path or payload in it.
     */
    public record FindingV2(
            String ruleId,
            GateClassV2 gateClass,
            List<String> featureKinds,
            String detail
    ) {
        public FindingV2 {
            Objects.requireNonNull(ruleId, "ruleId");
            if (!RULE_ID.matcher(ruleId).matches()) {
                throw new IllegalArgumentException("ruleId must match " + RULE_ID + ": " + ruleId);
            }
            Objects.requireNonNull(gateClass, "gateClass");
            featureKinds = requireSorted(featureKinds, "featureKinds", IDENTIFIER, MAXIMUM_KINDS);
            Objects.requireNonNull(detail, "detail");
            if (detail.isBlank() || detail.length() > 256) {
                throw new IllegalArgumentException("detail must be non-blank and <= 256");
            }
        }
    }

    private static List<String> requireSorted(
            List<String> values,
            String fieldName,
            Pattern pattern,
            int maximumSize
    ) {
        Objects.requireNonNull(values, fieldName);
        List<String> copied = List.copyOf(values);
        if (copied.size() > maximumSize) {
            throw new IllegalArgumentException(fieldName + " must hold at most " + maximumSize + " entries");
        }
        String previous = null;
        for (String value : copied) {
            Objects.requireNonNull(value, fieldName);
            if (!pattern.matcher(value).matches()) {
                throw new IllegalArgumentException(fieldName + " must match " + pattern + ": " + value);
            }
            if (previous != null && previous.compareTo(value) >= 0) {
                throw new IllegalArgumentException(fieldName + " must be sorted and unique");
            }
            previous = value;
        }
        return copied;
    }
}
