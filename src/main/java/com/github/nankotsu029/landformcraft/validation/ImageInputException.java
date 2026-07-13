package com.github.nankotsu029.landformcraft.validation;

import java.io.Serial;
import java.util.Objects;

public final class ImageInputException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ImageInputFailureCode code;

    public ImageInputException(ImageInputFailureCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ImageInputException(ImageInputFailureCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ImageInputFailureCode code() {
        return code;
    }
}
