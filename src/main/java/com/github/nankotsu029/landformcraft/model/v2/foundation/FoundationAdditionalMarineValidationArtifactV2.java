package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-10-04 additional-marine foundation slice validation evidence. */
public record FoundationAdditionalMarineValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String marineFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-additional-marine-validation-artifact-v1";

    public FoundationAdditionalMarineValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("additional marine validation planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown additional marine validation contract version");
        }
        marineFeatureId = FoundationValidationV2.optionalSlug(marineFeatureId, "marineFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationAdditionalMarineValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationAdditionalMarineValidationArtifactV2(
                planVersion, contractVersion, marineFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean basinContainmentOk,
            boolean depthReliefOk,
            boolean slopeOk,
            boolean transitionOk,
            boolean wholeTileOk,
            boolean budgetOk,
            boolean exportOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
