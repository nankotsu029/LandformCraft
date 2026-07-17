package com.github.nankotsu029.landformcraft.generator.v2.hydrology.river;

/** Stable pre-publication failure for the V2-3-03 river compiler or generator. */
public final class RiverGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public RiverGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public RiverGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
