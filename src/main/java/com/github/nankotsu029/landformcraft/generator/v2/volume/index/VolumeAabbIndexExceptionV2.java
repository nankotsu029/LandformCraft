package com.github.nankotsu029.landformcraft.generator.v2.volume.index;

/** Checked failure for volume AABB index operations. */
public final class VolumeAabbIndexExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final VolumeAabbIndexFailureCodeV2 failureCode;

    public VolumeAabbIndexExceptionV2(VolumeAabbIndexFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public VolumeAabbIndexExceptionV2(
            VolumeAabbIndexFailureCodeV2 failureCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public VolumeAabbIndexFailureCodeV2 failureCode() {
        return failureCode;
    }
}
