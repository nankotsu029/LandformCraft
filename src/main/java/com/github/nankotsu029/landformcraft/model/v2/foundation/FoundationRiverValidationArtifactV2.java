package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-04 general river foundation slice validation evidence. */
public record FoundationRiverValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String riverFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-river-validation-artifact-v1";

    public FoundationRiverValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("river validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown river validation contract version");
        }
        riverFeatureId = FoundationValidationV2.optionalSlug(riverFeatureId, "riverFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationRiverValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationRiverValidationArtifactV2(
                planVersion, contractVersion, riverFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean sourceMouthReachable,
            boolean bedMonotonic,
            boolean confluenceFlowOk,
            boolean graphBudgetOk,
            boolean cycleFree
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
