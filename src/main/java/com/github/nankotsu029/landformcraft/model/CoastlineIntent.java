package com.github.nankotsu029.landformcraft.model;

public record CoastlineIntent(double irregularity, int bayCount, int capeCount) {
    public static final int MAX_BAYS = 64;
    public static final int MAX_CAPES = 64;

    public CoastlineIntent {
        irregularity = ModelValidation.requireUnitInterval(irregularity, "irregularity");
        if (bayCount < 0 || bayCount > MAX_BAYS) {
            throw new IllegalArgumentException("bayCount must be between 0 and " + MAX_BAYS);
        }
        if (capeCount < 0 || capeCount > MAX_CAPES) {
            throw new IllegalArgumentException("capeCount must be between 0 and " + MAX_CAPES);
        }
    }
}
