package com.github.nankotsu029.landformcraft.generator.v2.volume.arch;

public final class NaturalArchExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final NaturalArchFailureCodeV2 failureCode;

    public NaturalArchExceptionV2(NaturalArchFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public NaturalArchFailureCodeV2 failureCode() {
        return failureCode;
    }
}
