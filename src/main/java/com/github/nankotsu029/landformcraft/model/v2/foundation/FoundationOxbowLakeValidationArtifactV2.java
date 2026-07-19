package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-10-11 oxbow lake foundation slice validation evidence. */
public record FoundationOxbowLakeValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String oxbowFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-oxbow-lake-validation-artifact-v1";

    public FoundationOxbowLakeValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("oxbow validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown oxbow validation contract version");
        }
        oxbowFeatureId = FoundationValidationV2.optionalSlug(oxbowFeatureId, "oxbowFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationOxbowLakeValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationOxbowLakeValidationArtifactV2(
                planVersion, contractVersion, oxbowFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean cutoffOwnershipOk,
            boolean parentRiverBindOk,
            boolean stagnantLevelOk,
            boolean rimClosedOk,
            boolean wetlandHandoffOk,
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
