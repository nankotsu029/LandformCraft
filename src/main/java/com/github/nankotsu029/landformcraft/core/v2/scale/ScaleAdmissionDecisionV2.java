package com.github.nankotsu029.landformcraft.core.v2.scale;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;

import java.util.Objects;

/** Deterministic result of scale admission: the frozen tile plan plus planning estimates. */
public record ScaleAdmissionDecisionV2(
        String admissionVersion,
        ScaleClassV2 scaleClass,
        TilePlanV2 tilePlan,
        boolean streamingExecutionRequired,
        long estimatedWorkingBytesPerTile,
        long estimatedRetainedBytes,
        long estimatedArtifactBytes
) {
    public ScaleAdmissionDecisionV2 {
        Objects.requireNonNull(admissionVersion, "admissionVersion");
        Objects.requireNonNull(scaleClass, "scaleClass");
        Objects.requireNonNull(tilePlan, "tilePlan");
        if (estimatedWorkingBytesPerTile < 0 || estimatedRetainedBytes < 0 || estimatedArtifactBytes < 0) {
            throw new IllegalArgumentException("admission estimates must not be negative");
        }
    }
}
