package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-08 OceanBasin foundation slice validation evidence. */
public record FoundationOceanBasinValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String oceanFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-ocean-basin-validation-artifact-v1";

    public FoundationOceanBasinValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("OceanBasin validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown OceanBasin validation contract version");
        }
        oceanFeatureId = FoundationValidationV2.optionalSlug(oceanFeatureId, "oceanFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationOceanBasinValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationOceanBasinValidationArtifactV2(
                planVersion, contractVersion, oceanFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean depthFinite, boolean widthOk, boolean fluidSolidConflictFree, boolean budgetOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
