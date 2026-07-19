package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-07 volcanic-cone foundation slice validation evidence. */
public record FoundationVolcanicConeValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String coneFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-volcanic-cone-validation-artifact-v1";

    public FoundationVolcanicConeValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("volcanic cone validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown volcanic cone validation contract version");
        }
        coneFeatureId = FoundationValidationV2.optionalSlug(coneFeatureId, "coneFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationVolcanicConeValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationVolcanicConeValidationArtifactV2(
                planVersion, contractVersion, coneFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean coneMassPresent,
            boolean craterContained,
            boolean radialDrainagePresent,
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
