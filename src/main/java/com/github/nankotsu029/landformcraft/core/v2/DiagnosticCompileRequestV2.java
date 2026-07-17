package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.Objects;

/** Trusted compile invocation metadata; it is not a new public GenerationRequest format. */
public record DiagnosticCompileRequestV2(
        String requestId,
        GenerationBounds bounds,
        int tileSize,
        long globalSeed,
        String sourceRequestChecksum,
        WorldBlueprintV2.ResourceBudget budget
) {
    public DiagnosticCompileRequestV2 {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(bounds, "bounds");
        if (tileSize < 32 || tileSize > 256 || Integer.bitCount(tileSize) != 1) throw new IllegalArgumentException("invalid tileSize");
        Objects.requireNonNull(sourceRequestChecksum, "sourceRequestChecksum");
        Objects.requireNonNull(budget, "budget");
    }

    public static WorldBlueprintV2.ResourceBudget defaultBudget() {
        return new WorldBlueprintV2.ResourceBudget(
                256, 512, 512, 16_384, 128, 256,
                256, 64,
                256L * 1024L * 1024L,
                100_000_000L,
                16L * 1024L * 1024L
        );
    }
}
