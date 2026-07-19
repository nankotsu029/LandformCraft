package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Deterministic land-water classification of sanitized ARGB pixels. Integer-only; no AI, no
 * platform float, no locale, timezone, or iteration-order dependence. The thresholds are
 * frozen under {@link #ALGORITHM_VERSION}; any change requires a new version string so that
 * previously extracted drafts stay reproducible.
 */
public final class ImageLandWaterExtractorV2 {
    public static final String ALGORITHM_VERSION = "image-land-water-extract-v1";

    /** Cells with {@code 2*blue - red - green} at or above this are water. */
    static final int WATER_MINIMUM_BLUE_DOMINANCE = 32;
    /** Cells with {@code 2*blue - red - green} at or below this are land. */
    static final int LAND_MAXIMUM_BLUE_DOMINANCE = 0;
    /** Cells with alpha below this are UNKNOWN regardless of color. */
    static final int MINIMUM_OPAQUE_ALPHA = 128;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    /** Class raster plus confidence raster, one byte each per pixel. */
    private static final long WORKING_BYTES_PER_PIXEL = 2L;

    private ImageLandWaterExtractorV2() {
    }

    public static ExtractedMaskDraftV2 extract(
            int width,
            int length,
            int[] argbPixels,
            String sourceChecksum,
            ImageMaskExtractionLimitsV2 limits,
            BooleanSupplier cancelled
    ) {
        Objects.requireNonNull(argbPixels, "argbPixels");
        Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancelled, "cancelled");
        if (width < 1 || length < 1
                || width > limits.maximumDimension() || length > limits.maximumDimension()) {
            throw failure(ImageMaskExtractionFailureCodeV2.INVALID_DIMENSIONS,
                    "image dimensions must be between 1 and " + limits.maximumDimension());
        }
        long longSide = Math.max(width, length);
        long shortSide = Math.min(width, length);
        if (longSide > shortSide * (long) limits.maximumAspectRatio()) {
            throw failure(ImageMaskExtractionFailureCodeV2.ASPECT_RATIO_EXCEEDED,
                    "image aspect ratio exceeds " + limits.maximumAspectRatio());
        }
        long pixels = (long) width * length;
        if (pixels > limits.maximumPixels()) {
            throw failure(ImageMaskExtractionFailureCodeV2.PIXELS_EXCEEDED,
                    "image pixel count exceeds " + limits.maximumPixels());
        }
        if (pixels * WORKING_BYTES_PER_PIXEL > limits.maximumWorkingBytes()) {
            throw failure(ImageMaskExtractionFailureCodeV2.WORKING_BUDGET_EXCEEDED,
                    "extraction working set exceeds " + limits.maximumWorkingBytes() + " bytes");
        }
        if (argbPixels.length != pixels) {
            throw failure(ImageMaskExtractionFailureCodeV2.PIXEL_BUFFER_MISMATCH,
                    "pixel buffer length does not match the declared dimensions");
        }
        if (!SHA_256.matcher(sourceChecksum).matches()) {
            throw failure(ImageMaskExtractionFailureCodeV2.INVALID_SOURCE_CHECKSUM,
                    "sourceChecksum must be a lowercase SHA-256");
        }

        byte[] classes = new byte[(int) pixels];
        byte[] confidence = new byte[(int) pixels];
        int waterCells = 0;
        int landCells = 0;
        int unknownCells = 0;
        for (int z = 0; z < length; z++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("image mask extraction was cancelled");
            }
            int rowOffset = z * width;
            for (int x = 0; x < width; x++) {
                int pixel = argbPixels[rowOffset + x];
                int alpha = pixel >>> 24;
                int index = rowOffset + x;
                if (alpha < MINIMUM_OPAQUE_ALPHA) {
                    classes[index] = ExtractedMaskDraftV2.CLASS_UNKNOWN;
                    unknownCells++;
                    continue;
                }
                int red = (pixel >>> 16) & 0xFF;
                int green = (pixel >>> 8) & 0xFF;
                int blue = pixel & 0xFF;
                int blueDominance = 2 * blue - red - green;
                if (blueDominance >= WATER_MINIMUM_BLUE_DOMINANCE) {
                    classes[index] = ExtractedMaskDraftV2.CLASS_WATER;
                    confidence[index] = (byte) Math.min(255, blueDominance);
                    waterCells++;
                } else if (blueDominance <= LAND_MAXIMUM_BLUE_DOMINANCE) {
                    classes[index] = ExtractedMaskDraftV2.CLASS_LAND;
                    confidence[index] = (byte) Math.min(255, 1 - blueDominance);
                    landCells++;
                } else {
                    classes[index] = ExtractedMaskDraftV2.CLASS_UNKNOWN;
                    unknownCells++;
                }
            }
        }
        String semanticChecksum = semanticChecksum(width, length, sourceChecksum, classes, confidence);
        return new ExtractedMaskDraftV2(width, length, ALGORITHM_VERSION, sourceChecksum,
                semanticChecksum, classes, confidence, waterCells, landCells, unknownCells);
    }

    private static String semanticChecksum(
            int width, int length, String sourceChecksum, byte[] classes, byte[] confidence) {
        MessageDigest digest = sha256();
        updateBytes(digest, ALGORITHM_VERSION.getBytes(StandardCharsets.UTF_8));
        updateInt(digest, width);
        updateInt(digest, length);
        updateBytes(digest, sourceChecksum.getBytes(StandardCharsets.UTF_8));
        updateBytes(digest, classes);
        updateBytes(digest, confidence);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateBytes(MessageDigest digest, byte[] bytes) {
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ImageMaskExtractionExceptionV2 failure(
            ImageMaskExtractionFailureCodeV2 code, String message) {
        return new ImageMaskExtractionExceptionV2(code, message);
    }
}
