package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.format.v2.tile.CanonicalBlockStateV2;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.PlacementBlockPhysicsCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.PlacementBlockPhysicsKindV2;

import java.util.Objects;

/** One final, feature-neutral desired block from the canonical preview/export resolver stream. */
public record PlacementDesiredBlockV2(
        int x,
        int y,
        int z,
        String blockState,
        PlacementApplyPassV2 pass,
        int overlayOrdinal,
        int ownerTileIndex
) {
    public static final int MAXIMUM_OVERLAY_ORDINAL = 1_000_000;

    public PlacementDesiredBlockV2 {
        blockState = CanonicalBlockStateV2.requireCanonical(blockState);
        Objects.requireNonNull(pass, "pass");
        if (overlayOrdinal < 0 || overlayOrdinal > MAXIMUM_OVERLAY_ORDINAL) {
            throw new IllegalArgumentException("overlayOrdinal out of range");
        }
        if (ownerTileIndex < 0) {
            throw new IllegalArgumentException("ownerTileIndex must be non-negative");
        }
        PlacementApplyPassV2 expected = classify(blockState);
        if (pass != expected) {
            throw new IllegalArgumentException(
                    "block state requires " + expected + " pass, not " + pass);
        }
    }

    public static PlacementApplyPassV2 classify(String blockState) {
        PlacementBlockPhysicsKindV2 physics = PlacementBlockPhysicsCatalogV2.require(
                CanonicalBlockStateV2.requireCanonical(blockState));
        return switch (physics) {
            case AIR -> PlacementApplyPassV2.AIR_CARVE;
            case FLUID -> PlacementApplyPassV2.FLUID;
            case SOLID, GRAVITY, NEIGHBOR -> PlacementApplyPassV2.SOLID;
            case UNSUPPORTED -> throw new IllegalArgumentException(
                    "unsupported block state cannot enter the Release 2 apply stream");
        };
    }
}
