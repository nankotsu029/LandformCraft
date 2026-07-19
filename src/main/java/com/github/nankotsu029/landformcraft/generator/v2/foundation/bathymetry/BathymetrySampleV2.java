package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

/** Shared 2.5D bathymetry cell sample for V2-9-08 foundation generators. */
public record BathymetrySampleV2(
        int depthBlocksBelowSea,
        int slopeMillionths,
        int coastDistanceBlocks,
        int ownershipMask,
        int floorY,
        int fluidColumnHintTopY
) {
    public static final byte TAG_EMPTY = 0;
    public static final byte TAG_SOLID = 1;
    public static final byte TAG_FLUID = 2;

    public static BathymetrySampleV2 outside(int waterLevel) {
        return new BathymetrySampleV2(0, 0, 0, 0, waterLevel, waterLevel);
    }

    public boolean owned() {
        return ownershipMask == 1;
    }

    public int rawValue(BathymetryField field) {
        return switch (field) {
            case DEPTH -> depthBlocksBelowSea;
            case SLOPE -> slopeMillionths;
            case COAST_DISTANCE -> coastDistanceBlocks;
            case OWNERSHIP -> ownershipMask;
        };
    }

    public enum BathymetryField {
        DEPTH,
        SLOPE,
        COAST_DISTANCE,
        OWNERSHIP
    }
}
