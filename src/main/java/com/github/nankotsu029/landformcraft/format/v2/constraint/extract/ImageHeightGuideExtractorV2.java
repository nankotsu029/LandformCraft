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
 * Deterministic luminance→height-guide draft extraction. Integer-only; no AI, no gamma-as-height,
 * no contour vectorization. Thresholds are frozen under {@link #ALGORITHM_VERSION}.
 */
public final class ImageHeightGuideExtractorV2 {
    public static final String ALGORITHM_VERSION = "image-height-guide-extract-v1";

    /** BT.601-style integer luminance weights (sum 256). */
    static final int LUMA_RED_WEIGHT = 77;
    static final int LUMA_GREEN_WEIGHT = 150;
    static final int LUMA_BLUE_WEIGHT = 29;
    /** Cells with alpha below this are no-data regardless of luminance. */
    static final int MINIMUM_OPAQUE_ALPHA = 128;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final long WORKING_BYTES_PER_PIXEL = 2L;

    private ImageHeightGuideExtractorV2() {
    }

    public static ExtractedHeightGuideDraftV2 extract(
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

        byte[] samples = new byte[(int) pixels];
        byte[] confidence = new byte[(int) pixels];
        int validCells = 0;
        int noDataCells = 0;
        for (int z = 0; z < length; z++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("image height guide extraction was cancelled");
            }
            int rowOffset = z * width;
            for (int x = 0; x < width; x++) {
                int pixel = argbPixels[rowOffset + x];
                int alpha = pixel >>> 24;
                int index = rowOffset + x;
                if (alpha < MINIMUM_OPAQUE_ALPHA) {
                    samples[index] = 0;
                    confidence[index] = 0;
                    noDataCells++;
                    continue;
                }
                int red = (pixel >>> 16) & 0xFF;
                int green = (pixel >>> 8) & 0xFF;
                int blue = pixel & 0xFF;
                int luma = (LUMA_RED_WEIGHT * red + LUMA_GREEN_WEIGHT * green + LUMA_BLUE_WEIGHT * blue) >> 8;
                int clamped = Math.min(ExtractedHeightGuideDraftV2.MAXIMUM_VALID_SAMPLE, luma);
                samples[index] = (byte) clamped;
                confidence[index] = (byte) alpha;
                validCells++;
            }
        }
        String semanticChecksum = semanticChecksum(width, length, sourceChecksum, samples, confidence);
        return new ExtractedHeightGuideDraftV2(
                width, length, ALGORITHM_VERSION, sourceChecksum, semanticChecksum,
                ExtractedHeightGuideDraftV2.SAMPLE_SPACE_DECLARATION,
                samples, confidence, validCells, noDataCells);
    }

    /** Integer luminance used by the frozen algorithm (exposed for golden tests). */
    public static int luminance(int red, int green, int blue) {
        return (LUMA_RED_WEIGHT * red + LUMA_GREEN_WEIGHT * green + LUMA_BLUE_WEIGHT * blue) >> 8;
    }

    private static String semanticChecksum(
            int width, int length, String sourceChecksum, byte[] samples, byte[] confidence) {
        MessageDigest digest = sha256();
        updateBytes(digest, ALGORITHM_VERSION.getBytes(StandardCharsets.UTF_8));
        updateBytes(digest, ExtractedHeightGuideDraftV2.SAMPLE_SPACE_DECLARATION.getBytes(StandardCharsets.UTF_8));
        updateInt(digest, width);
        updateInt(digest, length);
        updateBytes(digest, sourceChecksum.getBytes(StandardCharsets.UTF_8));
        updateBytes(digest, samples);
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
