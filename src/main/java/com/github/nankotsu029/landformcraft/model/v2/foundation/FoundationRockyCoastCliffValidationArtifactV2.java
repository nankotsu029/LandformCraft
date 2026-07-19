package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Sealed V2-9-06 rocky-coast/sea-cliff foundation slice validation evidence. */
public record FoundationRockyCoastCliffValidationArtifactV2(
        int planVersion,
        String contractVersion,
        String rockyCoastFeatureId,
        String seaCliffFeatureId,
        Metrics metrics,
        List<Issue> issues,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "foundation-rocky-coast-cliff-validation-artifact-v1";

    public FoundationRockyCoastCliffValidationArtifactV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("rocky-coast/cliff validation artifact planVersion must be 1");
        }
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown rocky-coast/cliff validation contract version");
        }
        rockyCoastFeatureId = FoundationValidationV2.optionalSlug(rockyCoastFeatureId, "rockyCoastFeatureId");
        seaCliffFeatureId = FoundationValidationV2.optionalSlug(seaCliffFeatureId, "seaCliffFeatureId");
        Objects.requireNonNull(metrics, "metrics");
        issues = FoundationValidationV2.immutable(issues, "issues", 256).stream()
                .sorted(Comparator.comparing(Issue::ruleId).thenComparing(Issue::message))
                .toList();
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public FoundationRockyCoastCliffValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new FoundationRockyCoastCliffValidationArtifactV2(
                planVersion, contractVersion, rockyCoastFeatureId, seaCliffFeatureId,
                metrics, issues, checksum);
    }

    public record Metrics(
            boolean rockShelfPresent,
            boolean cliffFacePresent,
            boolean talusPresent,
            boolean notchPresent,
            boolean shoreSideOk,
            boolean hostAabbOk,
            boolean coastTransitionOk,
            boolean surfaceVolumeOwnershipOk,
            boolean haloBudgetOk
    ) {
    }

    public record Issue(String ruleId, String message) {
        public Issue {
            ruleId = FoundationValidationV2.slug(ruleId, "ruleId");
            message = FoundationValidationV2.nonBlank(message, "message", 512);
        }
    }
}
