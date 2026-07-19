package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-08 ContinentalSlope foundation slice validation evidence. */
public record FoundationContinentalSlopeValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String slopeFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-continental-slope-validation-artifact-v1";

    public FoundationContinentalSlopeValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("ContinentalSlope validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown ContinentalSlope validation contract version");
        }
        slopeFeatureId = FoundationValidationV2.optionalSlug(slopeFeatureId, "slopeFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationContinentalSlopeValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationContinentalSlopeValidationArtifactV2(
                planVersion, contractVersion, slopeFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean depthFinite, boolean monotoneOk, boolean widthOk, boolean fluidSolidConflictFree, boolean budgetOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
