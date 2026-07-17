package com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal;

/** Stable V2-3-08 compile/generation failure. */
public final class TidalGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public TidalGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public TidalGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
