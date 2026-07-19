package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-10 CaveEntrance foundation slice validation evidence. */
public record FoundationCaveEntranceValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String entranceFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-cave-entrance-validation-artifact-v1";

    public FoundationCaveEntranceValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("CaveEntrance validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown CaveEntrance validation contract version");
        }
        entranceFeatureId = FoundationValidationV2.optionalSlug(entranceFeatureId, "entranceFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationCaveEntranceValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationCaveEntranceValidationArtifactV2(
                planVersion, contractVersion, entranceFeatureId, metrics, issues, checksum);
    }

    public record Metrics(
            boolean singleSurfaceHost,
            boolean singleCaveTarget,
            boolean reachable,
            boolean roofOk,
            boolean floodLeakFree,
            boolean ownerConflictFree,
            boolean aabbBudgetOk,
            boolean seamlessExportOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
