package com.github.nankotsu029.landformcraft.model;

public record ReliefIntent(double minimum, double average, double maximum) {
    public ReliefIntent {
        minimum = ModelValidation.requireUnitInterval(minimum, "minimum");
        average = ModelValidation.requireUnitInterval(average, "average");
        maximum = ModelValidation.requireUnitInterval(maximum, "maximum");
        if (minimum > average || average > maximum) {
            throw new IllegalArgumentException("relief must satisfy minimum <= average <= maximum");
        }
    }
}
