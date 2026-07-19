package com.github.nankotsu029.landformcraft.ai.spi.v2;

import java.util.Objects;

/** Sanitized design failure. Contains no prompt, secret, raw path, or provider payload body. */
public final class DesignExceptionV2 extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final DesignFailureCodeV2 code;

    public DesignExceptionV2(DesignFailureCodeV2 code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public DesignExceptionV2(DesignFailureCodeV2 code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public DesignFailureCodeV2 code() {
        return code;
    }
}
