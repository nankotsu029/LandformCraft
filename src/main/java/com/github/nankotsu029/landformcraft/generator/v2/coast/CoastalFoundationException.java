package com.github.nankotsu029.landformcraft.generator.v2.coast;

/** Stable compiler failure raised before a canonical coastal plan is published. */
public final class CoastalFoundationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public CoastalFoundationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
