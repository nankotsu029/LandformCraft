package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-10-06 escarpment/plateau foundation slice validation evidence. */
public record FoundationEscarpmentPlateauValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String escarpmentFeatureId,
        String plateauFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-escarpment-plateau-validation-artifact-v1";

    public FoundationEscarpmentPlateauValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("escarpment/plateau validation planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown escarpment/plateau validation contract version");
        }
        escarpmentFeatureId = FoundationValidationV2.optionalSlug(escarpmentFeatureId, "escarpmentFeatureId");
        plateauFeatureId = FoundationValidationV2.optionalSlug(plateauFeatureId, "plateauFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationEscarpmentPlateauValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationEscarpmentPlateauValidationArtifactV2(
                planVersion, contractVersion, escarpmentFeatureId, plateauFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean longScarpOwnerOk,
            boolean capFloorTalusOk,
            boolean transitionOk,
            boolean materialHandoffOk,
            boolean wholeTileOk,
            boolean budgetOk,
            boolean exportOk,
            boolean dryLandModifiersSeparated
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
