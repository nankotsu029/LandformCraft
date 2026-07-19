package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

public final class VolumeTileCacheExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final VolumeTileCacheFailureCodeV2 failureCode;

    public VolumeTileCacheExceptionV2(VolumeTileCacheFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public VolumeTileCacheExceptionV2(
            VolumeTileCacheFailureCodeV2 failureCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public VolumeTileCacheFailureCodeV2 failureCode() {
        return failureCode;
    }
}
