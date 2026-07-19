package com.github.nankotsu029.landformcraft.generator.v2.volume.waterfall;

public final class WaterfallVolumeExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final WaterfallVolumeFailureCodeV2 failureCode;

    public WaterfallVolumeExceptionV2(WaterfallVolumeFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public WaterfallVolumeFailureCodeV2 failureCode() {
        return failureCode;
    }
}
