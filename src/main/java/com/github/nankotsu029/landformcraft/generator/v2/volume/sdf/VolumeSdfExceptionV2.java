package com.github.nankotsu029.landformcraft.generator.v2.volume.sdf;

/** Checked failure for volume SDF evaluation. */
public final class VolumeSdfExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final VolumeSdfFailureCodeV2 failureCode;

    public VolumeSdfExceptionV2(VolumeSdfFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public VolumeSdfExceptionV2(VolumeSdfFailureCodeV2 failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public VolumeSdfFailureCodeV2 failureCode() {
        return failureCode;
    }
}
