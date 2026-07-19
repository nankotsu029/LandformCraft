package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

/** 2.5D submarine-canyon cell sample for V2-9-09 foundation generator. */
public record SubmarineCanyonSampleV2(
        int mask,
        int floorDepthBlocksBelowSea,
        int ownershipMask,
        int hostHandoffCode,
        int fluidColumnHintTopY,
        int floorY
) {
    public static SubmarineCanyonSampleV2 outside(int waterLevel) {
        return new SubmarineCanyonSampleV2(0, 0, 0, 0, waterLevel, waterLevel);
    }

    public boolean owned() {
        return ownershipMask == 1;
    }

    public int rawValue(SubmarineCanyonField field) {
        return switch (field) {
            case MASK -> mask;
            case FLOOR_DEPTH -> floorDepthBlocksBelowSea;
            case OWNERSHIP -> ownershipMask;
            case HOST_HANDOFF -> hostHandoffCode;
            case FLUID_COLUMN_HINT -> fluidColumnHintTopY;
        };
    }

    public enum SubmarineCanyonField {
        MASK,
        FLOOR_DEPTH,
        OWNERSHIP,
        HOST_HANDOFF,
        FLUID_COLUMN_HINT
    }
}
