package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-07 single-island foundation slice validation evidence. */
public record FoundationSingleIslandValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String islandFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-single-island-validation-artifact-v1";

    public FoundationSingleIslandValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("single island validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown single island validation contract version");
        }
        islandFeatureId = FoundationValidationV2.optionalSlug(islandFeatureId, "islandFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationSingleIslandValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationSingleIslandValidationArtifactV2(
                planVersion, contractVersion, islandFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean islandMassPresent,
            boolean shoreBandPresent,
            boolean radialDrainagePresent,
            boolean apronPresent,
            boolean supportBudgetOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
