package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Objects;

/**
 * Sealed portable envelope for an independent volume validation result (V2-5-15).
 *
 * <p>Measured from public volume feature descriptors only — never from generator-private
 * arrays or dense voxel grids.</p>
 */
public record VolumeValidationArtifactV2(
        int validationArtifactVersion,
        String sourcePlanChecksum,
        String validatorId,
        String validatorVersion,
        VolumeValidationReport report,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String VALIDATOR_ID = "v2.volume.validation";
    public static final String VALIDATOR_VERSION = "volume-validator-v1";
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);

    public VolumeValidationArtifactV2 {
        if (validationArtifactVersion != VERSION) {
            throw new IllegalArgumentException("volume validation artifact version must be 1");
        }
        sourcePlanChecksum = V2Validation.checksum(sourcePlanChecksum, "sourcePlanChecksum");
        validatorId = V2Validation.qualifiedId(validatorId, "validatorId");
        if (!VALIDATOR_ID.equals(validatorId)) {
            throw new IllegalArgumentException("volume validation artifact validatorId is unsupported");
        }
        validatorVersion = V2Validation.nonBlank(validatorVersion, "validatorVersion", 64);
        if (!VALIDATOR_VERSION.equals(validatorVersion)) {
            throw new IllegalArgumentException("volume validation artifact validatorVersion is unsupported");
        }
        Objects.requireNonNull(report, "report");
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public VolumeValidationArtifactV2(String sourcePlanChecksum, VolumeValidationReport report) {
        this(VERSION, sourcePlanChecksum, VALIDATOR_ID, VALIDATOR_VERSION, report, PENDING_CANONICAL_CHECKSUM);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public VolumeValidationArtifactV2 withCanonicalChecksum(String checksum) {
        return new VolumeValidationArtifactV2(
                validationArtifactVersion, sourcePlanChecksum, validatorId, validatorVersion, report, checksum);
    }

    public record VolumeValidationReport(
            java.util.List<MetricResultV2> metrics,
            java.util.List<DiagnosticIssueV2> issues
    ) {
        public VolumeValidationReport {
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(issues, "issues");
            metrics = metrics.stream().sorted(java.util.Comparator.comparing(MetricResultV2::metricId)
                    .thenComparing(MetricResultV2::subject)).toList();
            issues = issues.stream().sorted(java.util.Comparator.comparing(DiagnosticIssueV2::issueId)).toList();
            if (metrics.size() > 128 || issues.size() > 2_048) {
                throw new IllegalArgumentException("volume validation report exceeds its bounded result size");
            }
        }

        public boolean passesHardValidation() {
            return issues.stream().noneMatch(issue -> issue.severity() == DiagnosticIssueV2.Severity.ERROR);
        }
    }
}
