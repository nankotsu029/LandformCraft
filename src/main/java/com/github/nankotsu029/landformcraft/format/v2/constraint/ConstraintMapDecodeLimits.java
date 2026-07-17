package com.github.nankotsu029.landformcraft.format.v2.constraint;

/** Admission limits checked before source retention and before ImageIO allocates decoded pixels. */
public record ConstraintMapDecodeLimits(
        int maximumSources,
        long maximumSourceBytes,
        long maximumTotalSourceBytes,
        int maximumDimension,
        int maximumAspectRatio,
        long maximumPixels,
        long maximumDecodedSampleBytes,
        long maximumWorkingBytes
) {
    public static final int TRUSTED_MAXIMUM_SOURCES = 32;
    public static final long TRUSTED_MAXIMUM_SOURCE_BYTES = 8L * 1024L * 1024L;
    public static final long TRUSTED_MAXIMUM_TOTAL_SOURCE_BYTES = 32L * 1024L * 1024L;
    public static final int TRUSTED_MAXIMUM_DIMENSION = 4_096;
    public static final int TRUSTED_MAXIMUM_ASPECT_RATIO = 32;
    public static final long TRUSTED_MAXIMUM_PIXELS = 4_000_000L;
    public static final long TRUSTED_MAXIMUM_DECODED_SAMPLE_BYTES = 8L * 1024L * 1024L;
    public static final long TRUSTED_MAXIMUM_WORKING_BYTES = 32L * 1024L * 1024L;

    public ConstraintMapDecodeLimits {
        if (maximumSources < 1
                || maximumSourceBytes < 1 || maximumTotalSourceBytes < maximumSourceBytes
                || maximumDimension < 1
                || maximumAspectRatio < 1
                || maximumPixels < 1 || maximumDecodedSampleBytes < 1 || maximumWorkingBytes < 1) {
            throw new IllegalArgumentException("invalid constraint-map decode limits");
        }
        // These are trusted implementation policy ceilings, not caller-controlled budget suggestions.
        maximumSources = Math.min(maximumSources, TRUSTED_MAXIMUM_SOURCES);
        maximumSourceBytes = Math.min(maximumSourceBytes, TRUSTED_MAXIMUM_SOURCE_BYTES);
        maximumTotalSourceBytes = Math.min(maximumTotalSourceBytes, TRUSTED_MAXIMUM_TOTAL_SOURCE_BYTES);
        maximumDimension = Math.min(maximumDimension, TRUSTED_MAXIMUM_DIMENSION);
        maximumAspectRatio = Math.min(maximumAspectRatio, TRUSTED_MAXIMUM_ASPECT_RATIO);
        maximumPixels = Math.min(maximumPixels, TRUSTED_MAXIMUM_PIXELS);
        maximumDecodedSampleBytes = Math.min(
                maximumDecodedSampleBytes, TRUSTED_MAXIMUM_DECODED_SAMPLE_BYTES);
        maximumWorkingBytes = Math.min(maximumWorkingBytes, TRUSTED_MAXIMUM_WORKING_BYTES);
    }

    public static ConstraintMapDecodeLimits defaults() {
        return new ConstraintMapDecodeLimits(
                TRUSTED_MAXIMUM_SOURCES,
                TRUSTED_MAXIMUM_SOURCE_BYTES,
                TRUSTED_MAXIMUM_TOTAL_SOURCE_BYTES,
                TRUSTED_MAXIMUM_DIMENSION,
                TRUSTED_MAXIMUM_ASPECT_RATIO,
                TRUSTED_MAXIMUM_PIXELS,
                TRUSTED_MAXIMUM_DECODED_SAMPLE_BYTES,
                TRUSTED_MAXIMUM_WORKING_BYTES
        );
    }
}
