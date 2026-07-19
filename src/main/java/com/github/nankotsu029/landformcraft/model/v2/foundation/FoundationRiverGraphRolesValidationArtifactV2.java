package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-13 river graph roles / waterfall-chain validation evidence. */
public record FoundationRiverGraphRolesValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String riverFeatureId,
        String waterfallChainId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-river-graph-roles-validation-artifact-v1";

    public FoundationRiverGraphRolesValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("river graph roles validation planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown river graph roles validation contract version");
        }
        riverFeatureId = FoundationValidationV2.slug(riverFeatureId, "riverFeatureId");
        waterfallChainId = FoundationValidationV2.optionalSlug(waterfallChainId, "waterfallChainId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationRiverGraphRolesValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationRiverGraphRolesValidationArtifactV2(
                planVersion, contractVersion, riverFeatureId, waterfallChainId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean sourceMouthReachable,
            boolean flowConservationOk,
            boolean elevationContinuityOk,
            boolean modifierOrderOk,
            boolean childOwnershipOk,
            boolean waterfallChainOk,
            boolean graphBudgetOk,
            boolean cycleFree,
            boolean wholeTileOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
