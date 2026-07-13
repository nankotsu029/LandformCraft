package com.github.nankotsu029.landformcraft.model;

public record WaterIntent(int riverCount, int lakeCount, int maximumSeaDepth, int shallowShelfWidth) {
    public static final int MAX_RIVERS = 16;
    public static final int MAX_LAKES = 64;
    public static final int MAX_SEA_DEPTH = 512;
    public static final int MAX_SHALLOW_SHELF_WIDTH = 1_000;

    public WaterIntent {
        if (riverCount < 0 || riverCount > MAX_RIVERS) {
            throw new IllegalArgumentException("riverCount must be between 0 and " + MAX_RIVERS);
        }
        if (lakeCount < 0 || lakeCount > MAX_LAKES) {
            throw new IllegalArgumentException("lakeCount must be between 0 and " + MAX_LAKES);
        }
        if (maximumSeaDepth < 0 || maximumSeaDepth > MAX_SEA_DEPTH) {
            throw new IllegalArgumentException("maximumSeaDepth must be between 0 and " + MAX_SEA_DEPTH);
        }
        if (shallowShelfWidth < 0 || shallowShelfWidth > MAX_SHALLOW_SHELF_WIDTH) {
            throw new IllegalArgumentException(
                    "shallowShelfWidth must be between 0 and " + MAX_SHALLOW_SHELF_WIDTH
            );
        }
    }
}
