package com.github.nankotsu029.landformcraft.core.v2;

import java.io.Serial;
import java.util.Objects;

/** Fail-closed zone-label promotion error; never falls back to implicit HARD promotion. */
public final class ExtractedZoneLabelPromotionExceptionV2 extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ExtractedZoneLabelPromotionFailureCodeV2 code;

    public ExtractedZoneLabelPromotionExceptionV2(
            ExtractedZoneLabelPromotionFailureCodeV2 code,
            String message
    ) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ExtractedZoneLabelPromotionExceptionV2(
            ExtractedZoneLabelPromotionFailureCodeV2 code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ExtractedZoneLabelPromotionFailureCodeV2 code() {
        return code;
    }
}
