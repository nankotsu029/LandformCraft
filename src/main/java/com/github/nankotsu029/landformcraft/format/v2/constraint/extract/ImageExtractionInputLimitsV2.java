package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

/**
 * Admission ceilings for the secure extraction input envelope. Callers may tighten values; the
 * trusted implementation policy ceilings cannot be raised.
 */
public record ImageExtractionInputLimitsV2(
        long maximumSourceBytes,
        long maximumTotalSourceBytes,
        int maximumDimension,
        int maximumAspectRatio,
        long maximumPixelsPerImage,
        long maximumTotalPixels,
        long maximumDecodeWorkingBytes
) {
    /** Matches Phase 5 / retired ReferenceImageProcessor source-byte ceiling. */
    public static final long TRUSTED_MAXIMUM_SOURCE_BYTES = 8L * 1024L * 1024L;
    public static final long TRUSTED_MAXIMUM_TOTAL_SOURCE_BYTES = 32L * 1024L * 1024L;
    public static final int TRUSTED_MAXIMUM_DIMENSION = 4_096;
    public static final int TRUSTED_MAXIMUM_ASPECT_RATIO = 32;
    public static final long TRUSTED_MAXIMUM_PIXELS_PER_IMAGE = 4_000_000L;
    public static final long TRUSTED_MAXIMUM_TOTAL_PIXELS = 16_000_000L;
    /**
     * Peak decode working estimate: oriented ARGB int raster (4 bytes/pixel) plus a same-sized
     * temporary copy during EXIF orientation. Keeps LARGE full-frame decode out of this envelope.
     */
    public static final long TRUSTED_MAXIMUM_DECODE_WORKING_BYTES = 64L * 1024L * 1024L;

    /** Bytes reserved in the working-set estimate for ImageIO buffers and decoder state. */
    static final long DECODE_OVERHEAD_BYTES = 64L * 1024L;
    /** Oriented TYPE_INT_ARGB plus a temporary source copy during orientation remap. */
    static final long ARGB_WORKING_BYTES_PER_PIXEL = 8L;

    public ImageExtractionInputLimitsV2 {
        if (maximumSourceBytes < 1
                || maximumTotalSourceBytes < 1
                || maximumDimension < 1
                || maximumAspectRatio < 1
                || maximumPixelsPerImage < 1
                || maximumTotalPixels < 1
                || maximumDecodeWorkingBytes < 1) {
            throw new IllegalArgumentException("invalid image extraction input limits");
        }
        maximumSourceBytes = Math.min(maximumSourceBytes, TRUSTED_MAXIMUM_SOURCE_BYTES);
        maximumTotalSourceBytes = Math.min(maximumTotalSourceBytes, TRUSTED_MAXIMUM_TOTAL_SOURCE_BYTES);
        maximumDimension = Math.min(maximumDimension, TRUSTED_MAXIMUM_DIMENSION);
        maximumAspectRatio = Math.min(maximumAspectRatio, TRUSTED_MAXIMUM_ASPECT_RATIO);
        maximumPixelsPerImage = Math.min(maximumPixelsPerImage, TRUSTED_MAXIMUM_PIXELS_PER_IMAGE);
        maximumTotalPixels = Math.min(maximumTotalPixels, TRUSTED_MAXIMUM_TOTAL_PIXELS);
        maximumDecodeWorkingBytes = Math.min(maximumDecodeWorkingBytes, TRUSTED_MAXIMUM_DECODE_WORKING_BYTES);
    }

    public static ImageExtractionInputLimitsV2 defaults() {
        return new ImageExtractionInputLimitsV2(
                TRUSTED_MAXIMUM_SOURCE_BYTES,
                TRUSTED_MAXIMUM_TOTAL_SOURCE_BYTES,
                TRUSTED_MAXIMUM_DIMENSION,
                TRUSTED_MAXIMUM_ASPECT_RATIO,
                TRUSTED_MAXIMUM_PIXELS_PER_IMAGE,
                TRUSTED_MAXIMUM_TOTAL_PIXELS,
                TRUSTED_MAXIMUM_DECODE_WORKING_BYTES
        );
    }
}
