package com.github.nankotsu029.landformcraft.generator.v2.coast.beach;

/** Stable pre-publication failure for the V2-2-03 sandy beach compiler or generator. */
public final class SandyBeachGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public SandyBeachGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public SandyBeachGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
