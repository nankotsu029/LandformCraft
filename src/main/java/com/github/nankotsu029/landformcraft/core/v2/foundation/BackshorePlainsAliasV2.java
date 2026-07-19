package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

/**
 * Maps legacy {@link TerrainIntentV2.BackshorePlainsParameters} to {@link TerrainIntentV2.PlainParameters}
 * without changing BACKSHORE_PLAINS serialization. BACKSHORE_PLAINS remains a diagnostic alias candidate.
 */
public final class BackshorePlainsAliasV2 {
    private BackshorePlainsAliasV2() {
    }

    public static TerrainIntentV2.PlainParameters suggestedPlainParameters(
            TerrainIntentV2.BackshorePlainsParameters backshore
    ) {
        int elevation = midpoint(backshore.elevationAboveWaterBlocks());
        return new TerrainIntentV2.PlainParameters(
                new TerrainIntentV2.IntRange(elevation, elevation),
                new TerrainIntentV2.IntRange(1, 3),
                new TerrainIntentV2.IntRange(4, 8));
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }
}
