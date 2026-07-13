package com.github.nankotsu029.landformcraft.model;

public enum TerrainFeature {
    RIVER(1),
    LAKE(1 << 1),
    VEGETATION(1 << 2),
    CLIFF(1 << 3),
    COAST(1 << 4);

    private final int mask;

    TerrainFeature(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }

    public boolean isPresent(int value) {
        return (value & mask) != 0;
    }
}
