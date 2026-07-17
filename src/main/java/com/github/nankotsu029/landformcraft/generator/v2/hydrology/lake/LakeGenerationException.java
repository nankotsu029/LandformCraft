package com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake;

/** Stable pre-publication failure for the V2-3-04 lake compiler or generator. */
public final class LakeGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public LakeGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public LakeGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
