package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-03 mountain/valley foundation slice validation evidence. */
public record FoundationRangeValleyValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String mountainFeatureId,
        String valleyFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-range-valley-validation-artifact-v1";

    public FoundationRangeValleyValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("range/valley validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown range/valley validation contract version");
        }
        mountainFeatureId = FoundationValidationV2.optionalSlug(mountainFeatureId, "mountainFeatureId");
        valleyFeatureId = FoundationValidationV2.optionalSlug(valleyFeatureId, "valleyFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationRangeValleyValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationRangeValleyValidationArtifactV2(
                planVersion, contractVersion, mountainFeatureId, valleyFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean ridgeGraphOk,
            boolean peakPassBudgetOk,
            boolean floorOwnerConflictFree,
            boolean valleyMountainTransitionOk,
            boolean valleyConnectionOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
