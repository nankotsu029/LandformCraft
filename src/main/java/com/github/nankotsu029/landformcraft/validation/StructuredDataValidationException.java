package com.github.nankotsu029.landformcraft.validation;

import java.util.List;

public final class StructuredDataValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String[] violations;

    public StructuredDataValidationException(String documentName, List<String> violations) {
        super(documentName + " failed validation: " + String.join("; ", violations));
        this.violations = violations.toArray(String[]::new);
    }

    public List<String> violations() {
        return List.of(violations.clone());
    }
}
