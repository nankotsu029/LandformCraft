package com.github.nankotsu029.landformcraft.core.v2.scale;

import java.util.Objects;

/** Admission rejection raised before any generation stage allocates memory or disk. */
public final class ScaleAdmissionExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ScaleAdmissionFailureCodeV2 failureCode;

    public ScaleAdmissionExceptionV2(ScaleAdmissionFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public ScaleAdmissionFailureCodeV2 failureCode() {
        return failureCode;
    }
}
