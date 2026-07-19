package com.github.nankotsu029.landformcraft.generator.v2.volume.query;

public final class VolumeTerrainQueryExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final VolumeTerrainQueryFailureCodeV2 failureCode;

    public VolumeTerrainQueryExceptionV2(VolumeTerrainQueryFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public VolumeTerrainQueryFailureCodeV2 failureCode() {
        return failureCode;
    }
}
