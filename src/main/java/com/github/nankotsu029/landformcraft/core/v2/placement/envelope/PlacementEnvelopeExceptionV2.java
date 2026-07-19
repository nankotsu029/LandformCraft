package com.github.nankotsu029.landformcraft.core.v2.placement.envelope;

import java.util.Objects;

/** Checked failure while compiling or validating a Release 2 placement envelope. */
public final class PlacementEnvelopeExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final PlacementEnvelopeFailureCodeV2 failureCode;

    public PlacementEnvelopeExceptionV2(PlacementEnvelopeFailureCodeV2 failureCode, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public PlacementEnvelopeFailureCodeV2 failureCode() {
        return failureCode;
    }
}
