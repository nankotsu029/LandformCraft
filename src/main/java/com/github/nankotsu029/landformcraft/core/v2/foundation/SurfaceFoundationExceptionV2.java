package com.github.nankotsu029.landformcraft.core.v2.foundation;

import java.util.Objects;

/** Checked failure for surface foundation compile/merge with a stable failure code. */
public final class SurfaceFoundationExceptionV2 extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final SurfaceFoundationFailureCodeV2 failureCode;

    public SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2 failureCode, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    /** Keeps the originating compiler failure attached when a plan rejection is re-coded (V2-19-07). */
    public SurfaceFoundationExceptionV2(
            SurfaceFoundationFailureCodeV2 failureCode,
            String message,
            Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public SurfaceFoundationFailureCodeV2 failureCode() {
        return failureCode;
    }
}
