package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

/**
 * Versioned Release 2 mutation passes. The explicit order value is part of the apply contract;
 * callers must not rely on the Java enum ordinal.
 */
public enum PlacementApplyPassV2 {
    SOLID(10),
    AIR_CARVE(20),
    FLUID(30);

    private final int applyOrder;

    PlacementApplyPassV2(int applyOrder) {
        this.applyOrder = applyOrder;
    }

    public int applyOrder() {
        return applyOrder;
    }
}
