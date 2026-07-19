package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-10-10 surface spring foundation slice validation evidence. */
public record FoundationSpringValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String springFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-spring-validation-artifact-v1";

    public FoundationSpringValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("spring validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown spring validation contract version");
        }
        springFeatureId = FoundationValidationV2.optionalSlug(springFeatureId, "springFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationSpringValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationSpringValidationArtifactV2(
                planVersion, contractVersion, springFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean sourceOwnershipOk,
            boolean riverSourceBindOk,
            boolean outflowContinuityOk,
            boolean hydrologySpringNodeOk,
            boolean graphReachableOk,
            boolean budgetOk,
            boolean wholeTileOk,
            boolean exportOk,
            boolean orphanFree
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
