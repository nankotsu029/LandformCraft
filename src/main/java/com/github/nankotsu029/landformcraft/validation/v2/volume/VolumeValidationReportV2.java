package com.github.nankotsu029.landformcraft.validation.v2.volume;

import com.github.nankotsu029.landformcraft.model.v2.DiagnosticIssueV2;
import com.github.nankotsu029.landformcraft.model.v2.MetricResultV2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Stable machine-readable result of one independent volume validation pass. */
public record VolumeValidationReportV2(
        List<MetricResultV2> metrics,
        List<DiagnosticIssueV2> issues
) {
    public VolumeValidationReportV2 {
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(issues, "issues");
        metrics = metrics.stream().sorted(Comparator.comparing(MetricResultV2::metricId)
                .thenComparing(MetricResultV2::subject)).toList();
        issues = issues.stream().sorted(Comparator.comparing(DiagnosticIssueV2::issueId)).toList();
        if (metrics.size() > 128 || issues.size() > 2_048) {
            throw new IllegalArgumentException("volume validation report exceeds its bounded result size");
        }
    }

    public boolean passesHardValidation() {
        return issues.stream().noneMatch(issue -> issue.severity() == DiagnosticIssueV2.Severity.ERROR);
    }
}
