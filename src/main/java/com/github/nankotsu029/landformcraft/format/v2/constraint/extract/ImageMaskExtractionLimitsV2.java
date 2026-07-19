package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

/** Admission limits checked before the extractor allocates class and confidence rasters. */
public record ImageMaskExtractionLimitsV2(
        int maximumDimension,
        int maximumAspectRatio,
        long maximumPixels,
        long maximumWorkingBytes
) {
    public static final int TRUSTED_MAXIMUM_DIMENSION = 4_096;
    public static final int TRUSTED_MAXIMUM_ASPECT_RATIO = 32;
    public static final long TRUSTED_MAXIMUM_PIXELS = 16_000_000L;
    public static final long TRUSTED_MAXIMUM_WORKING_BYTES = 64L * 1024L * 1024L;

    public ImageMaskExtractionLimitsV2 {
        if (maximumDimension < 1 || maximumAspectRatio < 1
                || maximumPixels < 1 || maximumWorkingBytes < 1) {
            throw new IllegalArgumentException("invalid image mask extraction limits");
        }
        // Trusted implementation policy ceilings, not caller-controlled budget suggestions.
        maximumDimension = Math.min(maximumDimension, TRUSTED_MAXIMUM_DIMENSION);
        maximumAspectRatio = Math.min(maximumAspectRatio, TRUSTED_MAXIMUM_ASPECT_RATIO);
        maximumPixels = Math.min(maximumPixels, TRUSTED_MAXIMUM_PIXELS);
        maximumWorkingBytes = Math.min(maximumWorkingBytes, TRUSTED_MAXIMUM_WORKING_BYTES);
    }

    public static ImageMaskExtractionLimitsV2 defaults() {
        return new ImageMaskExtractionLimitsV2(
                TRUSTED_MAXIMUM_DIMENSION,
                TRUSTED_MAXIMUM_ASPECT_RATIO,
                TRUSTED_MAXIMUM_PIXELS,
                TRUSTED_MAXIMUM_WORKING_BYTES
        );
    }
}
