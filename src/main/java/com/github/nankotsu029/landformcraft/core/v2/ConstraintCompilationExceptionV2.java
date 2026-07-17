package com.github.nankotsu029.landformcraft.core.v2;

import java.io.Serial;
import java.util.Objects;

/** Redacted V2-1 compilation error; source paths and raw samples are not included in messages. */
public final class ConstraintCompilationExceptionV2 extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ConstraintCompilationFailureCodeV2 code;

    public ConstraintCompilationExceptionV2(
            ConstraintCompilationFailureCodeV2 code,
            String message
    ) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ConstraintCompilationFailureCodeV2 code() {
        return code;
    }
}
