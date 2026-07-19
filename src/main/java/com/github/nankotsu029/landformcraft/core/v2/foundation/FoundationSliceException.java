package com.github.nankotsu029.landformcraft.core.v2.foundation;

/** Stable diagnostic failure for V2-9-02 foundation slice compilation and rasterization. */
public final class FoundationSliceException extends IllegalArgumentException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;
    private final String ruleId;

    public FoundationSliceException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public FoundationSliceException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
