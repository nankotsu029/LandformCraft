package com.github.nankotsu029.landformcraft.generator.v2.volume.cave;

public final class CaveNetworkExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final CaveNetworkFailureCodeV2 failureCode;

    public CaveNetworkExceptionV2(CaveNetworkFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public CaveNetworkFailureCodeV2 failureCode() {
        return failureCode;
    }
}
