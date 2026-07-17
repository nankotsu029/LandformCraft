package com.github.nankotsu029.landformcraft.generator.v2.coast.cape;

/** Stable V2-2-06 compile/generation failure with a diagnostic rule id. */
public final class RockyCapeGenerationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public RockyCapeGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public RockyCapeGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
