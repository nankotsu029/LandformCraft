package com.github.nankotsu029.landformcraft.generator.v2.composition.coast;

/** Stable-rule failure for coastal transition compilation or sampling. */
public final class CoastalTransitionException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public CoastalTransitionException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
