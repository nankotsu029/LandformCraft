package com.github.nankotsu029.landformcraft.generator.v2.hydrology.core;

/** Stable rule-tagged failure from the V2-3-02 routing kernel. */
public final class HydrologyRoutingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public HydrologyRoutingException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public HydrologyRoutingException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
