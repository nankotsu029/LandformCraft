package com.github.nankotsu029.landformcraft.core.v2;

/** Fatal diagnostic compile failure. Unsupported generation capability remains a Blueprint issue instead. */
public final class DiagnosticCompilationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public DiagnosticCompilationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
