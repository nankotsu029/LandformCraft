package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-12 macro land-water topology validation evidence. */
public record FoundationMacroLandWaterTopologyValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String topologyId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-macro-land-water-topology-validation-artifact-v1";

    public FoundationMacroLandWaterTopologyValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("macro topology validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown macro topology validation contract version");
        }
        topologyId = FoundationValidationV2.optionalSlug(topologyId, "topologyId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationMacroLandWaterTopologyValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationMacroLandWaterTopologyValidationArtifactV2(
                planVersion, contractVersion, topologyId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean connectivityOk,
            boolean containmentOk,
            boolean minWidthOk,
            boolean zoneSemanticOk,
            boolean graphBudgetOk,
            boolean rasterBudgetOk,
            boolean wholeTileOk,
            boolean orderIndependent,
            boolean threadIndependent,
            boolean freezeReady
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
