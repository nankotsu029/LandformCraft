package com.github.nankotsu029.landformcraft.generator.v2.volume.seacave;

public final class SeaCaveExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final SeaCaveFailureCodeV2 failureCode;

    public SeaCaveExceptionV2(SeaCaveFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public SeaCaveFailureCodeV2 failureCode() {
        return failureCode;
    }
}
