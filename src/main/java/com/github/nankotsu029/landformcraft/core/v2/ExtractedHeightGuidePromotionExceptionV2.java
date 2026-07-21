package com.github.nankotsu029.landformcraft.core.v2;

import java.io.Serial;
import java.util.Objects;

/** Fail-closed height-guide promotion error; never falls back to implicit HARD promotion. */
public final class ExtractedHeightGuidePromotionExceptionV2 extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ExtractedHeightGuidePromotionFailureCodeV2 code;

    public ExtractedHeightGuidePromotionExceptionV2(
            ExtractedHeightGuidePromotionFailureCodeV2 code,
            String message
    ) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ExtractedHeightGuidePromotionExceptionV2(
            ExtractedHeightGuidePromotionFailureCodeV2 code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ExtractedHeightGuidePromotionFailureCodeV2 code() {
        return code;
    }
}
