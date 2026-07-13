package com.github.nankotsu029.landformcraft.model;

public record GridPosition(int x, int z) {
    public static final GridPosition GLOBAL = new GridPosition(-1, -1);

    public GridPosition {
        if ((x < 0 || z < 0) && (x != -1 || z != -1)) {
            throw new IllegalArgumentException("position must be non-negative or GLOBAL");
        }
    }
}
