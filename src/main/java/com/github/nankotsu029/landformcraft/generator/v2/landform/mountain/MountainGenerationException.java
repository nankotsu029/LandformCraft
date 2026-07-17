package com.github.nankotsu029.landformcraft.generator.v2.landform.mountain;

/** Stable diagnostic failure for V2-3-10 mountain compilation and rasterization. */
public final class MountainGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public MountainGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public MountainGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
