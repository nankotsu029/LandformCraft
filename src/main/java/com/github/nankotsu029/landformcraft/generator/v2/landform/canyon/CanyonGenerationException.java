package com.github.nankotsu029.landformcraft.generator.v2.landform.canyon;

/** Stable pre-publication failure for the V2-3-05 canyon compiler or generator. */
public final class CanyonGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public CanyonGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public CanyonGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
