package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-10-03 karst hydrology foundation slice validation evidence. */
public record FoundationKarstHydrologyValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String graphFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-karst-hydrology-validation-artifact-v1";

    public FoundationKarstHydrologyValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("karst hydrology validation planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown karst hydrology validation contract version");
        }
        graphFeatureId = FoundationValidationV2.optionalSlug(graphFeatureId, "graphFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationKarstHydrologyValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationKarstHydrologyValidationArtifactV2(
                planVersion, contractVersion, graphFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean drainageReachable,
            boolean lossSpringBalanced,
            boolean collapseRoofOk,
            boolean fluidOwnerOk,
            boolean graphAcyclic,
            boolean csgBudgetOk,
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
