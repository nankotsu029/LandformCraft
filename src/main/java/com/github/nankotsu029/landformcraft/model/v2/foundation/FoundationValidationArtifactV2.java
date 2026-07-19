package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-02 foundation slice validation evidence; separate from WorldBlueprint. */
public record FoundationValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String plainFeatureId,
        String hillFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-validation-artifact-v1";

    public FoundationValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("foundation validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown foundation validation contract version");
        }
        plainFeatureId = FoundationValidationV2.optionalSlug(plainFeatureId, "plainFeatureId");
        hillFeatureId = FoundationValidationV2.optionalSlug(hillFeatureId, "hillFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationValidationArtifactV2(
                planVersion, contractVersion, plainFeatureId, hillFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean microReliefPresent,
            boolean ridgeContinuous,
            boolean saddleBudgetOk,
            boolean groundwaterHandoffPresent,
            boolean plainHillTransitionOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
