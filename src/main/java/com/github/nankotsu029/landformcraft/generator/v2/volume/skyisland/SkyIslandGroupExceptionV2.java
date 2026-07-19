package com.github.nankotsu029.landformcraft.generator.v2.volume.skyisland;

public final class SkyIslandGroupExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final SkyIslandGroupFailureCodeV2 failureCode;

    public SkyIslandGroupExceptionV2(SkyIslandGroupFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public SkyIslandGroupFailureCodeV2 failureCode() {
        return failureCode;
    }
}
