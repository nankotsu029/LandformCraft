package com.github.nankotsu029.landformcraft.model.v2;

/** Result type reserved for later measurement stages; V2-0 does not produce instances. */
public record MetricResultV2(
        String metricId,
        int metricVersion,
        String subject,
        long actualMillionths,
        TerrainIntentV2.FixedRange expected,
        long toleranceMillionths,
        String unit,
        boolean passed,
        String evidenceChecksum
) {
    public MetricResultV2 {
        metricId = V2Validation.qualifiedId(metricId, "metricId");
        if (metricVersion < 1) throw new IllegalArgumentException("metricVersion must be positive");
        subject = V2Validation.nonBlank(subject, "subject", 96);
        if (expected == null || toleranceMillionths < 0) throw new IllegalArgumentException("metric target is invalid");
        unit = V2Validation.qualifiedId(unit, "unit");
        evidenceChecksum = V2Validation.checksum(evidenceChecksum, "evidenceChecksum");
    }
}
