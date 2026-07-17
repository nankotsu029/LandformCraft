package com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile;

/** Stable preflight/input failure before a reconciliation result can be produced. */
public final class HydrologyReconciliationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public HydrologyReconciliationException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public HydrologyReconciliationException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
