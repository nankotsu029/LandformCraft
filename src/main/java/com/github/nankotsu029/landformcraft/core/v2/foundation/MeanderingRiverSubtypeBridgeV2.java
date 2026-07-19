package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Objects;

/**
 * Maps legacy {@link TerrainIntentV2.MeanderingRiverParameters} to suggested
 * {@link TerrainIntentV2.RiverParameters} without changing meander serialization.
 */
public final class MeanderingRiverSubtypeBridgeV2 {
    private MeanderingRiverSubtypeBridgeV2() {
    }

    public static TerrainIntentV2.RiverParameters suggestedRiverParameters(
            TerrainIntentV2.MeanderingRiverParameters meander
    ) {
        Objects.requireNonNull(meander, "meander");
        int floodMin = Math.max(meander.bankfullWidthBlocks().minimum(),
                meander.bankfullWidthBlocks().maximum());
        int floodMax = Math.min(128, Math.max(floodMin, meander.bankfullWidthBlocks().maximum() * 2));
        return new TerrainIntentV2.RiverParameters(
                meander.bankfullWidthBlocks(),
                meander.dischargeClass(),
                meander.minimumBedSlopeMillionths(),
                new TerrainIntentV2.IntRange(floodMin, floodMax),
                1,
                2);
    }
}
