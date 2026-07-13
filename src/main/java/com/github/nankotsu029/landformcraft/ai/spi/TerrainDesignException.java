package com.github.nankotsu029.landformcraft.ai.spi;

import java.io.Serial;
import java.util.Objects;

/** Sanitized provider failure. It intentionally contains no request body, prompt, or credentials. */
public final class TerrainDesignException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ProviderFailureCode code;
    private final int statusCode;
    private final int attempts;

    public TerrainDesignException(ProviderFailureCode code, String message, int statusCode, int attempts) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.statusCode = statusCode;
        this.attempts = attempts;
    }

    public TerrainDesignException(
            ProviderFailureCode code,
            String message,
            int statusCode,
            int attempts,
            Throwable cause
    ) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.statusCode = statusCode;
        this.attempts = attempts;
    }

    public ProviderFailureCode code() {
        return code;
    }

    public int statusCode() {
        return statusCode;
    }

    public int attempts() {
        return attempts;
    }
}
