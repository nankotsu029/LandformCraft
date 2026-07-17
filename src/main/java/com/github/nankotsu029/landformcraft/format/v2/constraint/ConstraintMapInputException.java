package com.github.nankotsu029.landformcraft.format.v2.constraint;

import java.io.Serial;
import java.util.Objects;

/** Redacted domain failure raised before a constraint map can become a canonical artifact. */
public final class ConstraintMapInputException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ConstraintMapFailureCode code;

    public ConstraintMapInputException(ConstraintMapFailureCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ConstraintMapInputException(ConstraintMapFailureCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ConstraintMapFailureCode code() {
        return code;
    }
}
