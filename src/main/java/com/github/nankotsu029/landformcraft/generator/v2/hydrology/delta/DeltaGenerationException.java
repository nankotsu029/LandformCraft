package com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta;

/** Stable V2-3-07 compile/generation failure. */
public final class DeltaGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public DeltaGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public DeltaGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
