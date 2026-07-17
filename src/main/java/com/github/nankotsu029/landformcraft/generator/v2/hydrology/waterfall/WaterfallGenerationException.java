package com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall;

/** Stable pre-publication failure for the V2-3-06 waterfall compiler or generator. */
public final class WaterfallGenerationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public WaterfallGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public WaterfallGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
