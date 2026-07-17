package com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater;

/** Stable-rule failure raised while compiling or sampling a V2-2-05 breakwater. */
public final class BreakwaterGenerationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public BreakwaterGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public BreakwaterGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
