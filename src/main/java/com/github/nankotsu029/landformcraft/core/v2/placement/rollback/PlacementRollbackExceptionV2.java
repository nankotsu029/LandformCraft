package com.github.nankotsu029.landformcraft.core.v2.placement.rollback;

import java.util.Objects;

/** Failure for Release 2 rollback orchestration. */
public final class PlacementRollbackExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final PlacementRollbackFailureCodeV2 code;
    private final boolean worldMutationMayHaveOccurred;

    public PlacementRollbackExceptionV2(
            PlacementRollbackFailureCodeV2 code,
            String message,
            boolean worldMutationMayHaveOccurred
    ) {
        this(code, message, worldMutationMayHaveOccurred, null);
    }

    public PlacementRollbackExceptionV2(
            PlacementRollbackFailureCodeV2 code,
            String message,
            boolean worldMutationMayHaveOccurred,
            Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = Objects.requireNonNull(code, "code");
        this.worldMutationMayHaveOccurred = worldMutationMayHaveOccurred;
    }

    public PlacementRollbackFailureCodeV2 code() {
        return code;
    }

    public boolean worldMutationMayHaveOccurred() {
        return worldMutationMayHaveOccurred;
    }
}
