package com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic;

/** Stable diagnostic failure for V2-3-11 volcanic compilation and rasterization. */
public final class VolcanicGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public VolcanicGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public VolcanicGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
