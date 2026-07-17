package com.github.nankotsu029.landformcraft.generator.v2.coast;

/** Stable rule-id failure raised before a coastal raster or window is exposed. */
public final class CoastalRasterException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String ruleId;

    public CoastalRasterException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public CoastalRasterException(String ruleId, String message, Throwable cause) {
        super(message, cause);
        this.ruleId = ruleId;
    }

    public String ruleId() {
        return ruleId;
    }
}
