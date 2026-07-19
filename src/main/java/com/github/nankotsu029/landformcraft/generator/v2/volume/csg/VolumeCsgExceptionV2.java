package com.github.nankotsu029.landformcraft.generator.v2.volume.csg;

/** Checked failure for ordered volume CSG evaluation. */
public final class VolumeCsgExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final VolumeCsgFailureCodeV2 failureCode;

    public VolumeCsgExceptionV2(VolumeCsgFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public VolumeCsgFailureCodeV2 failureCode() {
        return failureCode;
    }
}
