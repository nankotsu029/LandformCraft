package com.github.nankotsu029.landformcraft.core.v2.placement.undo;

import java.util.Objects;

/** Failure for Release 2 Undo prepare／execute orchestration. */
public final class PlacementUndoExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final PlacementUndoFailureCodeV2 code;
    private final boolean worldMutationMayHaveOccurred;

    public PlacementUndoExceptionV2(
            PlacementUndoFailureCodeV2 code,
            String message,
            boolean worldMutationMayHaveOccurred
    ) {
        this(code, message, worldMutationMayHaveOccurred, null);
    }

    public PlacementUndoExceptionV2(
            PlacementUndoFailureCodeV2 code,
            String message,
            boolean worldMutationMayHaveOccurred,
            Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = Objects.requireNonNull(code, "code");
        this.worldMutationMayHaveOccurred = worldMutationMayHaveOccurred;
    }

    public PlacementUndoFailureCodeV2 code() {
        return code;
    }

    public boolean worldMutationMayHaveOccurred() {
        return worldMutationMayHaveOccurred;
    }
}
