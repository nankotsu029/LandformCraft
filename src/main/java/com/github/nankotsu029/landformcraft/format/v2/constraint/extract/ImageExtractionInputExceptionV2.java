package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import java.util.Objects;

/** Rejection raised while loading or sanitizing an extraction image file. */
public final class ImageExtractionInputExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ImageExtractionInputFailureCodeV2 failureCode;

    public ImageExtractionInputExceptionV2(ImageExtractionInputFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public ImageExtractionInputExceptionV2(
            ImageExtractionInputFailureCodeV2 failureCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public ImageExtractionInputFailureCodeV2 failureCode() {
        return failureCode;
    }
}
