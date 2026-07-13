package com.github.nankotsu029.landformcraft.model;

import java.util.List;

public record ValidationResult(List<ValidationIssue> issues) {
    public ValidationResult {
        issues = ModelValidation.immutableList(issues, "issues", 10_000);
    }

    public boolean isValid() {
        return issues.stream().noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }
}
