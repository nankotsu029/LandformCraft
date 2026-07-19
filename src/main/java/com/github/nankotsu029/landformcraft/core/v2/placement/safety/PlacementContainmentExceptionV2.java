package com.github.nankotsu029.landformcraft.core.v2.placement.safety;

/**
 * Checked-style runtime failure for containment preflight. Callers must not fall back to apply or
 * treat missing evidence as contained.
 */
public final class PlacementContainmentExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final PlacementContainmentFailureCodeV2 failureCode;

    public PlacementContainmentExceptionV2(PlacementContainmentFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public PlacementContainmentExceptionV2(
            PlacementContainmentFailureCodeV2 failureCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public PlacementContainmentFailureCodeV2 failureCode() {
        return failureCode;
    }
}
