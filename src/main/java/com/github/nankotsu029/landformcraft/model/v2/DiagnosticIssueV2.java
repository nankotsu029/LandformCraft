package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Machine-readable diagnostic; messageKey is presentation metadata, not the decision key. */
public record DiagnosticIssueV2(
        String issueId,
        String ruleId,
        int ruleVersion,
        Severity severity,
        TerrainIntentV2.Strength hardness,
        List<Reference> references,
        List<MetricEvidence> metricEvidence,
        String messageKey,
        List<String> diagnosticLayerIds
) {
    public DiagnosticIssueV2 {
        issueId = V2Validation.slug(issueId, "issueId");
        ruleId = V2Validation.qualifiedId(ruleId, "ruleId");
        if (ruleVersion < 1) throw new IllegalArgumentException("ruleVersion must be positive");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(hardness, "hardness");
        references = V2Validation.sorted(references, "references", 16,
                Comparator.comparing(Reference::type).thenComparing(Reference::id));
        metricEvidence = V2Validation.immutable(metricEvidence, "metricEvidence", 1);
        messageKey = V2Validation.qualifiedId(messageKey, "messageKey");
        diagnosticLayerIds = V2Validation.sorted(
                diagnosticLayerIds, "diagnosticLayerIds", 16, Comparator.naturalOrder());
    }

    public enum Severity { ERROR, WARNING, INFO }
    public enum ReferenceType { FEATURE, CONSTRAINT, MODULE, FIELD, STAGE }

    public record Reference(ReferenceType type, String id) {
        public Reference {
            Objects.requireNonNull(type, "type");
            id = V2Validation.qualifiedId(id, "reference id");
        }
    }

    public record MetricEvidence(
            String metric,
            long expectedMinimumMillionths,
            long expectedMaximumMillionths,
            long actualMillionths,
            long toleranceMillionths
    ) {
        public MetricEvidence {
            metric = V2Validation.qualifiedId(metric, "metric");
            if (expectedMinimumMillionths > expectedMaximumMillionths || toleranceMillionths < 0) {
                throw new IllegalArgumentException("metric evidence range is invalid");
            }
        }
    }
}
