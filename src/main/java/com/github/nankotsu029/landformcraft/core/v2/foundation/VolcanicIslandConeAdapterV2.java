package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Objects;

/**
 * Maps legacy {@link TerrainIntentV2.VolcanicArchipelagoParameters} / island specs to suggested
 * foundation parameters without changing volcanic serialization or checksums.
 */
public final class VolcanicIslandConeAdapterV2 {
    private VolcanicIslandConeAdapterV2() {
    }

    public static TerrainIntentV2.ArchipelagoParameters suggestedArchipelagoParameters(
            TerrainIntentV2.VolcanicArchipelagoParameters volcanic
    ) {
        Objects.requireNonNull(volcanic, "volcanic");
        return new TerrainIntentV2.ArchipelagoParameters(
                volcanic.islands(),
                volcanic.submarineSaddleDepthBlocks(),
                12);
    }

    public static TerrainIntentV2.VolcanicConeParameters suggestedVolcanicConeParameters(
            TerrainIntentV2.IslandSpec dominant
    ) {
        Objects.requireNonNull(dominant, "dominant");
        int radius = dominant.radiusBlocks();
        int summit = dominant.summitHeightBlocksAboveSea();
        int craterMin = Math.max(1, radius / 4);
        int craterMax = Math.max(craterMin, radius / 3);
        if (craterMax >= radius) {
            craterMax = Math.max(1, radius - 1);
            craterMin = Math.min(craterMin, craterMax);
        }
        int floor = Math.min(8, Math.max(1, summit / 4));
        return new TerrainIntentV2.VolcanicConeParameters(
                new TerrainIntentV2.IntRange(radius, radius),
                new TerrainIntentV2.IntRange(summit, summit),
                new TerrainIntentV2.IntRange(craterMin, craterMax),
                new TerrainIntentV2.IntRange(floor, floor),
                new TerrainIntentV2.FixedRange(300_000L, 600_000L));
    }
}
