package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import java.util.Objects;

public final class PlacementApplyExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final PlacementApplyFailureCodeV2 code;
    private final boolean worldMutationMayHaveOccurred;

    public PlacementApplyExceptionV2(
            PlacementApplyFailureCodeV2 code,
            String message,
            boolean worldMutationMayHaveOccurred
    ) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.worldMutationMayHaveOccurred = worldMutationMayHaveOccurred;
    }

    public PlacementApplyExceptionV2(
            PlacementApplyFailureCodeV2 code,
            String message,
            boolean worldMutationMayHaveOccurred,
            Throwable cause
    ) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.worldMutationMayHaveOccurred = worldMutationMayHaveOccurred;
    }

    public PlacementApplyFailureCodeV2 code() {
        return code;
    }

    public boolean worldMutationMayHaveOccurred() {
        return worldMutationMayHaveOccurred;
    }
}
