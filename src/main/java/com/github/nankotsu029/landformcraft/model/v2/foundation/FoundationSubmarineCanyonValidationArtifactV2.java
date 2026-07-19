package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-09 SubmarineCanyon foundation slice validation evidence. */
public record FoundationSubmarineCanyonValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String canyonFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-submarine-canyon-validation-artifact-v1";

    public FoundationSubmarineCanyonValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("SubmarineCanyon validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown SubmarineCanyon validation contract version");
        }
        canyonFeatureId = FoundationValidationV2.optionalSlug(canyonFeatureId, "canyonFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationSubmarineCanyonValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationSubmarineCanyonValidationArtifactV2(
                planVersion, contractVersion, canyonFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean headContained,
            boolean outletContained,
            boolean slopeCrossingOk,
            boolean downGradientOk,
            boolean floorDepthOk,
            boolean fluidSolidConflictFree,
            boolean wholeTileOk,
            boolean budgetOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
