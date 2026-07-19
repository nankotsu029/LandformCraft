package com.github.nankotsu029.landformcraft.generator.v2.landform.reef;

/** Stable diagnostic failure for V2-4-10 coral reef compilation and rasterization. */
public final class CoralReefGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public CoralReefGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public CoralReefGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
