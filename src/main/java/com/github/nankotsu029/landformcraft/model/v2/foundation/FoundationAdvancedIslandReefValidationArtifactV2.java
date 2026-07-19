package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-10-08 advanced island/reef composition validation evidence. */
public record FoundationAdvancedIslandReefValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String presetFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-advanced-island-reef-validation-artifact-v1";

    public FoundationAdvancedIslandReefValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("advanced island/reef validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown advanced island/reef validation contract version");
        }
        presetFeatureId = FoundationValidationV2.optionalSlug(presetFeatureId, "presetFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationAdvancedIslandReefValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationAdvancedIslandReefValidationArtifactV2(
                planVersion, contractVersion, presetFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean shoreParallelRidgeOk,
            boolean lagoonPassMarineOk,
            boolean isletOwnershipOk,
            boolean transitionOk,
            boolean budgetOk,
            boolean exportOk,
            boolean childContractsReused,
            boolean legacyFixturesUnchanged
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
