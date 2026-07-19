package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-07 archipelago foundation slice validation evidence. */
public record FoundationArchipelagoValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String archipelagoFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-archipelago-validation-artifact-v1";

    public FoundationArchipelagoValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("archipelago validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown archipelago validation contract version");
        }
        archipelagoFeatureId = FoundationValidationV2.optionalSlug(archipelagoFeatureId, "archipelagoFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationArchipelagoValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationArchipelagoValidationArtifactV2(
                planVersion, contractVersion, archipelagoFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean componentCountOk,
            boolean dryLandGapOk,
            boolean noOverlap,
            boolean saddlesPresent,
            boolean dominanceOk,
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
