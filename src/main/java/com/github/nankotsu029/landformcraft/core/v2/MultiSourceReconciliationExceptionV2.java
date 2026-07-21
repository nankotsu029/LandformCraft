package com.github.nankotsu029.landformcraft.core.v2;

import java.util.Objects;

public final class MultiSourceReconciliationExceptionV2 extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final MultiSourceReconciliationFailureCodeV2 code;

    public MultiSourceReconciliationExceptionV2(
            MultiSourceReconciliationFailureCodeV2 code,
            String message
    ) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public MultiSourceReconciliationExceptionV2(
            MultiSourceReconciliationFailureCodeV2 code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public MultiSourceReconciliationFailureCodeV2 code() {
        return code;
    }
}
