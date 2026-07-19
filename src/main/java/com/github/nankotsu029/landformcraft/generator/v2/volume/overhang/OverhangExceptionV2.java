package com.github.nankotsu029.landformcraft.generator.v2.volume.overhang;

public final class OverhangExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final OverhangFailureCodeV2 failureCode;

    public OverhangExceptionV2(OverhangFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public OverhangFailureCodeV2 failureCode() {
        return failureCode;
    }
}
