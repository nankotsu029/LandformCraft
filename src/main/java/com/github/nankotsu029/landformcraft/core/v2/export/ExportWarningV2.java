package com.github.nankotsu029.landformcraft.core.v2.export;

import java.util.Objects;

/**
 * One NON_GATING export warning surfaced to the operator through the CLI summary (V2-18-09).
 * Never written into a sealed Blueprint, diagnostic artifact, or Release manifest, so it affects
 * no checksum. The rule id must be registered in {@code DiagnosticGateContractV2}.
 */
public record ExportWarningV2(String ruleId, String message) {
    /** ADR 0038 D8-1: the surface-baseline argument is ignored on an explicit-foundation request. */
    public static final String RULE_SURFACE_BASELINE_DEPRECATED = "v2.cli.surface-baseline-deprecated";

    public ExportWarningV2 {
        ruleId = Objects.requireNonNull(ruleId, "ruleId");
        message = Objects.requireNonNull(message, "message");
    }

    static ExportWarningV2 surfaceBaselineDeprecated() {
        return new ExportWarningV2(RULE_SURFACE_BASELINE_DEPRECATED,
                "the surface-baseline argument is deprecated and ignored: this request declares an "
                        + "explicit macro foundation input (HARD LAND_WATER_MASK + foundationBaseLevels)");
    }
}
