package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-10-01 glacial-ice foundation slice validation evidence. */
public record FoundationGlacialIceValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String iceFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-glacial-ice-validation-artifact-v1";

    public FoundationGlacialIceValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("glacial ice validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown glacial ice validation contract version");
        }
        iceFeatureId = FoundationValidationV2.optionalSlug(iceFeatureId, "iceFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationGlacialIceValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationGlacialIceValidationArtifactV2(
                planVersion, contractVersion, iceFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean coldClimateOk,
            boolean bedContactOk,
            boolean flowTerminusOk,
            boolean sparseIceOk,
            boolean meltwaterHandoffOk,
            boolean leakFree,
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
