package com.github.nankotsu029.landformcraft.generator.v2.volume.water;

public final class UndergroundLakeExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final UndergroundLakeFailureCodeV2 failureCode;

    public UndergroundLakeExceptionV2(UndergroundLakeFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public UndergroundLakeFailureCodeV2 failureCode() {
        return failureCode;
    }
}
