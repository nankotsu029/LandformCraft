package com.github.nankotsu029.landformcraft.generator.v2.landform.fjord;

/** Stable diagnostic failure for V2-3-09 fjord compilation and rasterization. */
public final class FjordGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public FjordGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public FjordGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
