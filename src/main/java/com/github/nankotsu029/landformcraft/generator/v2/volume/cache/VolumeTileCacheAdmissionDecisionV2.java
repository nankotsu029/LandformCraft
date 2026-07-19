package com.github.nankotsu029.landformcraft.generator.v2.volume.cache;

/**
 * Immutable admission result for one volume tile-cache plan.
 */
public record VolumeTileCacheAdmissionDecisionV2(
        String admissionVersion,
        long retainedBytes,
        long peakWorkingBytesPerChunk,
        long concurrency,
        long cacheBytes,
        long requiredWorkingBytes
) {
    public VolumeTileCacheAdmissionDecisionV2 {
        if (admissionVersion == null || admissionVersion.isBlank()) {
            throw new IllegalArgumentException("admissionVersion");
        }
        if (retainedBytes < 1
                || peakWorkingBytesPerChunk < 1
                || concurrency < 1
                || cacheBytes < 1
                || requiredWorkingBytes < 1) {
            throw new IllegalArgumentException("admission budgets must be positive");
        }
    }
}
