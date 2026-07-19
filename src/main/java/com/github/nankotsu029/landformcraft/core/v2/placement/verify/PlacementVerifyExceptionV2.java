package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

import java.util.Objects;

/** Checked failure for settle／full verify orchestration. */
public final class PlacementVerifyExceptionV2 extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final PlacementVerifyFailureCodeV2 code;
    private final boolean worldMutationMayHaveOccurred;

    public PlacementVerifyExceptionV2(
            PlacementVerifyFailureCodeV2 code,
            String message,
            boolean worldMutationMayHaveOccurred
    ) {
        this(code, message, worldMutationMayHaveOccurred, null);
    }

    public PlacementVerifyExceptionV2(
            PlacementVerifyFailureCodeV2 code,
            String message,
            boolean worldMutationMayHaveOccurred,
            Throwable cause
    ) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = Objects.requireNonNull(code, "code");
        this.worldMutationMayHaveOccurred = worldMutationMayHaveOccurred;
    }

    public PlacementVerifyFailureCodeV2 code() {
        return code;
    }

    public boolean worldMutationMayHaveOccurred() {
        return worldMutationMayHaveOccurred;
    }
}
