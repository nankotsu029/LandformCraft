package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Objects;

/**
 * Sealed, portable envelope for an independent hydrology validation result.
 *
 * <p>The report remains separate from {@link WorldBlueprintV2}: the blueprint is compiled intent,
 * while this artifact is evidence measured from finalized hydrology fields and optional
 * reconciliation residuals.</p>
 */
public record HydrologyValidationArtifactV2(
        int validationArtifactVersion,
        String sourceBlueprintChecksum,
        String validatorId,
        String validatorVersion,
        HydrologyValidationReport report,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String VALIDATOR_ID = "v2.hydrology.validation";
    public static final String VALIDATOR_VERSION = "hydrology-validator-v1";
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);

    public HydrologyValidationArtifactV2 {
        if (validationArtifactVersion != VERSION) {
            throw new IllegalArgumentException("hydrology validation artifact version must be 1");
        }
        sourceBlueprintChecksum = V2Validation.checksum(sourceBlueprintChecksum, "sourceBlueprintChecksum");
        validatorId = V2Validation.qualifiedId(validatorId, "validatorId");
        if (!VALIDATOR_ID.equals(validatorId)) {
            throw new IllegalArgumentException("hydrology validation artifact validatorId is unsupported");
        }
        validatorVersion = V2Validation.nonBlank(validatorVersion, "validatorVersion", 64);
        if (!VALIDATOR_VERSION.equals(validatorVersion)) {
            throw new IllegalArgumentException("hydrology validation artifact validatorVersion is unsupported");
        }
        Objects.requireNonNull(report, "report");
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public HydrologyValidationArtifactV2(
            String sourceBlueprintChecksum,
            String validatorVersion,
            HydrologyValidationReport report
    ) {
        this(VERSION, sourceBlueprintChecksum, VALIDATOR_ID, validatorVersion, report,
                PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public HydrologyValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new HydrologyValidationArtifactV2(
                validationArtifactVersion, sourceBlueprintChecksum, validatorId, validatorVersion, report, checksum);
    }

    /** Portable report shape kept in model.v2 so Release verification has no generator dependency. */
    public record HydrologyValidationReport(
            java.util.List<MetricResultV2> metrics,
            java.util.List<DiagnosticIssueV2> issues
    ) {
        public HydrologyValidationReport {
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(issues, "issues");
            metrics = metrics.stream().sorted(java.util.Comparator.comparing(MetricResultV2::metricId)
                    .thenComparing(MetricResultV2::subject)).toList();
            issues = issues.stream().sorted(java.util.Comparator.comparing(DiagnosticIssueV2::issueId)).toList();
            if (metrics.size() > 128 || issues.size() > 2_048) {
                throw new IllegalArgumentException("hydrology validation report exceeds its bounded result size");
            }
        }

        public boolean passesHardValidation() {
            return issues.stream().noneMatch(issue -> issue.severity() == DiagnosticIssueV2.Severity.ERROR);
        }
    }
}
