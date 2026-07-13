package com.github.nankotsu029.landformcraft.model;

public record GenerationMetrics(
        long generationMillis,
        long estimatedRetainedBytes,
        long estimatedPeakWorkingBytes
) {
    public GenerationMetrics {
        if (generationMillis < 0 || estimatedRetainedBytes < 0 || estimatedPeakWorkingBytes < estimatedRetainedBytes) {
            throw new IllegalArgumentException("invalid generation metrics");
        }
    }
}
