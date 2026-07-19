package com.github.nankotsu029.landformcraft.generator.v2.environment.local;

public final class VolumeLocalEnvironmentExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final VolumeLocalEnvironmentFailureCodeV2 failureCode;

    public VolumeLocalEnvironmentExceptionV2(
            VolumeLocalEnvironmentFailureCodeV2 failureCode,
            String message
    ) {
        super(message);
        this.failureCode = failureCode;
    }

    public VolumeLocalEnvironmentFailureCodeV2 failureCode() {
        return failureCode;
    }
}
