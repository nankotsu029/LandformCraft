package com.github.nankotsu029.landformcraft.core;

import java.util.Objects;
import java.util.UUID;

/** Sanitized domain failure carrying a stable code and correlation identifier. */
public final class LandformException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final LandformErrorCode code;
    private final UUID correlationId;
    private final String operation;
    private final String resourceId;
    private final String stage;
    private final String suggestedAction;

    public LandformException(
            LandformErrorCode code,
            String safeMessage,
            String operation,
            String resourceId,
            String stage,
            String suggestedAction
    ) {
        this(code, safeMessage, operation, resourceId, stage, suggestedAction, null);
    }

    public LandformException(
            LandformErrorCode code,
            String safeMessage,
            String operation,
            String resourceId,
            String stage,
            String suggestedAction,
            Throwable cause
    ) {
        super(Objects.requireNonNull(safeMessage, "safeMessage"), cause);
        this.code = Objects.requireNonNull(code, "code");
        this.correlationId = UUID.randomUUID();
        this.operation = Objects.requireNonNullElse(operation, "unknown");
        this.resourceId = Objects.requireNonNullElse(resourceId, "");
        this.stage = Objects.requireNonNullElse(stage, "unknown");
        this.suggestedAction = Objects.requireNonNullElse(suggestedAction, "Review the administrator log.");
    }

    public LandformErrorCode code() { return code; }
    public UUID correlationId() { return correlationId; }
    public String operation() { return operation; }
    public String resourceId() { return resourceId; }
    public String stage() { return stage; }
    public String suggestedAction() { return suggestedAction; }
}
