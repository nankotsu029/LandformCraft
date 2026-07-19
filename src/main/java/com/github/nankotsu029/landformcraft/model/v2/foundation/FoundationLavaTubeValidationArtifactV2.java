package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-10-07 LavaTube foundation slice validation evidence. */
public record FoundationLavaTubeValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String tubeFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-lava-tube-validation-artifact-v1";

    public FoundationLavaTubeValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("LavaTube validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown LavaTube validation contract version");
        }
        tubeFeatureId = FoundationValidationV2.optionalSlug(tubeFeatureId, "tubeFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationLavaTubeValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationLavaTubeValidationArtifactV2(
                planVersion, contractVersion, tubeFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean hostRelationOk,
            boolean tubeContinuityOk,
            boolean roofSupportOk,
            boolean fluidConflictFree,
            boolean aabbBudgetOk,
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
