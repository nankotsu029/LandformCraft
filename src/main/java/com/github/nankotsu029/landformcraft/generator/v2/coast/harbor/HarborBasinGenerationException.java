package com.github.nankotsu029.landformcraft.generator.v2.coast.harbor;

/** Stable rejection raised before an invalid harbor plan or field can be published. */
public final class HarborBasinGenerationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public HarborBasinGenerationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public HarborBasinGenerationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
