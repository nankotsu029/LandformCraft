package com.github.nankotsu029.landformcraft.core.v2.recovery;

import java.util.Objects;

/** Failure for Release 2 recovery orchestration. */
public final class PlacementRecoveryExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final PlacementRecoveryFailureCodeV2 code;
    private final boolean worldMutationMayHaveOccurred;

    public PlacementRecoveryExceptionV2(
            PlacementRecoveryFailureCodeV2 code,
            String message,
            boolean worldMutationMayHaveOccurred
    ) {
        this(code, message, worldMutationMayHaveOccurred, null);
    }

    public PlacementRecoveryExceptionV2(
            PlacementRecoveryFailureCodeV2 code,
            String message,
            boolean worldMutationMayHaveOccurred,
            Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = Objects.requireNonNull(code, "code");
        this.worldMutationMayHaveOccurred = worldMutationMayHaveOccurred;
    }

    public PlacementRecoveryFailureCodeV2 code() {
        return code;
    }

    public boolean worldMutationMayHaveOccurred() {
        return worldMutationMayHaveOccurred;
    }
}
