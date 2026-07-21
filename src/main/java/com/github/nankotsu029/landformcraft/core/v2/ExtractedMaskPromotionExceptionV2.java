package com.github.nankotsu029.landformcraft.core.v2;

import java.io.Serial;
import java.util.Objects;

/** Fail-closed promotion error; never falls back to implicit HARD promotion. */
public final class ExtractedMaskPromotionExceptionV2 extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ExtractedMaskPromotionFailureCodeV2 code;

    public ExtractedMaskPromotionExceptionV2(ExtractedMaskPromotionFailureCodeV2 code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ExtractedMaskPromotionExceptionV2(
            ExtractedMaskPromotionFailureCodeV2 code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ExtractedMaskPromotionFailureCodeV2 code() {
        return code;
    }
}
