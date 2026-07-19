package com.github.nankotsu029.landformcraft.core.v2.placement.reservation;

import java.util.Objects;

/** Checked failure while reserving regions/disk or issuing/verifying confirmation. */
public final class PlacementReservationExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final PlacementReservationFailureCodeV2 failureCode;

    public PlacementReservationExceptionV2(PlacementReservationFailureCodeV2 failureCode, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public PlacementReservationFailureCodeV2 failureCode() {
        return failureCode;
    }
}
