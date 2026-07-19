package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-10-02 glacial-deposition foundation slice validation evidence. */
public record FoundationGlacialDepositionValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String depositionFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-glacial-deposition-validation-artifact-v1";

    public FoundationGlacialDepositionValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("glacial deposition validation planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown glacial deposition validation contract version");
        }
        depositionFeatureId = FoundationValidationV2.optionalSlug(depositionFeatureId, "depositionFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationGlacialDepositionValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationGlacialDepositionValidationArtifactV2(
                planVersion, contractVersion, depositionFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean glacialParentOk,
            boolean sedimentOwnerOk,
            boolean ridgeOrFlowOk,
            boolean profileKindSeparated,
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
