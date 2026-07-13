package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;

public record ValidationIssue(
        ValidationSeverity severity,
        String code,
        String message,
        GridPosition position
) {
    public ValidationIssue {
        Objects.requireNonNull(severity, "severity");
        code = ModelValidation.requireSlug(code, "code");
        message = ModelValidation.requireNonBlank(message, "message", 2_000);
        Objects.requireNonNull(position, "position");
    }
}
