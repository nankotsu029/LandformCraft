package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-05 floodplain/marsh foundation slice validation evidence. */
public record FoundationFloodplainMarshValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String riverFeatureId,
        String floodplainFeatureId,
        String marshFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-floodplain-marsh-validation-artifact-v1";

    public FoundationFloodplainMarshValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("floodplain/marsh validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown floodplain/marsh validation contract version");
        }
        riverFeatureId = FoundationValidationV2.optionalSlug(riverFeatureId, "riverFeatureId");
        floodplainFeatureId = FoundationValidationV2.optionalSlug(floodplainFeatureId, "floodplainFeatureId");
        marshFeatureId = FoundationValidationV2.optionalSlug(marshFeatureId, "marshFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationFloodplainMarshValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationFloodplainMarshValidationArtifactV2(
                planVersion, contractVersion, riverFeatureId, floodplainFeatureId, marshFeatureId,
                metrics, issues, checksum);
    }

    public record Metrics(
            boolean riverAdjacencyOk,
            boolean microReliefPresent,
            boolean marshWetnessOk,
            boolean openWaterTransitionOk,
            boolean groundwaterHydroperiodOk,
            boolean fluidSolidOwnershipOk,
            boolean floodplainMarshTransitionOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
