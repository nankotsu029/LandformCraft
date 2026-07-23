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

    /**
     * V2-15-10 / ADR 0039 Candidate A: maps a {@code RIVER} feature's {@link TerrainIntentV2.RiverParameters}
     * onto {@link TerrainIntentV2.MeanderingRiverParameters} so the offline hydrology-plan pipeline can
     * compile it with {@code MeanderingRiverPlanCompilerV2} without changing that compiler's math or the
     * {@code MEANDERING_RIVER} kind's own contract. {@code variant} controls whether the compiled reach
     * meanders ({@link TerrainIntentV2.RiverVariant#MEANDERING_RIVER}) or stays straight
     * ({@link TerrainIntentV2.RiverVariant#RIVER}).
     */
    public static TerrainIntentV2.MeanderingRiverParameters meanderingParametersFor(
            TerrainIntentV2.RiverParameters river,
            TerrainIntentV2.RiverVariant variant
    ) {
        Objects.requireNonNull(river, "river");
        Objects.requireNonNull(variant, "variant");
        return new TerrainIntentV2.MeanderingRiverParameters(
                river.bankfullWidthBlocks(),
                river.dischargeClass(),
                river.minimumBedSlopeMillionths(),
                variant);
    }
}
