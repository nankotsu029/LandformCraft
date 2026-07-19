package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import java.util.Objects;

/** Extraction rejection raised before or during deterministic pixel classification. */
public final class ImageMaskExtractionExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ImageMaskExtractionFailureCodeV2 failureCode;

    public ImageMaskExtractionExceptionV2(ImageMaskExtractionFailureCodeV2 failureCode, String message) {
        super(message);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public ImageMaskExtractionFailureCodeV2 failureCode() {
        return failureCode;
    }
}
