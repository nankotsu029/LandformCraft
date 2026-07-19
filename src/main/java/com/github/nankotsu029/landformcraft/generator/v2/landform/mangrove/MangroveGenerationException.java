package com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove;

/** Stable diagnostic failure for V2-4-09 mangrove compilation and rasterization. */
public final class MangroveGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public MangroveGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public MangroveGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
