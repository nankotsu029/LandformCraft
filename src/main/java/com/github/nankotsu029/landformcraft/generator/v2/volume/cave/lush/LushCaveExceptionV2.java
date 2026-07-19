package com.github.nankotsu029.landformcraft.generator.v2.volume.cave.lush;

public final class LushCaveExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final LushCaveFailureCodeV2 failureCode;

    public LushCaveExceptionV2(LushCaveFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public LushCaveFailureCodeV2 failureCode() {
        return failureCode;
    }
}
