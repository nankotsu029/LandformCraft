package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Deterministic fixed-palette zone-label extraction. Integer-only squared Euclidean distance;
 * no AI, no iterative clustering (k-means). Thresholds are frozen under {@link #ALGORITHM_VERSION}.
 */
public final class ImageZoneLabelExtractorV2 {
    public static final String ALGORITHM_VERSION = "image-zone-label-extract-v1";

    /** Maximum squared RGB distance accepted as a palette match. */
    static final int MAXIMUM_MATCH_DISTANCE_SQUARED = 3 * 48 * 48;
    /** Minimum gap between best and second-best; smaller gaps are UNKNOWN (ambiguous). */
    static final int AMBIGUOUS_MARGIN_DISTANCE_SQUARED = 3 * 16 * 16;
    static final int MINIMUM_OPAQUE_ALPHA = 128;
    public static final int MAXIMUM_PALETTE_LABELS = 64;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final long WORKING_BYTES_PER_PIXEL = 2L;

    private ImageZoneLabelExtractorV2() {
    }

    public static ExtractedZoneLabelDraftV2 extract(
            int width,
            int length,
            int[] argbPixels,
            String sourceChecksum,
            ImageMaskExtractionLimitsV2 limits,
            BooleanSupplier cancelled
    ) {
        return extract(
                width, length, argbPixels, sourceChecksum,
                ZonePaletteEntryV2.sketchPaletteV1(), limits, cancelled);
    }

    public static ExtractedZoneLabelDraftV2 extract(
            int width,
            int length,
            int[] argbPixels,
            String sourceChecksum,
            List<ZonePaletteEntryV2> palette,
            ImageMaskExtractionLimitsV2 limits,
            BooleanSupplier cancelled
    ) {
        Objects.requireNonNull(argbPixels, "argbPixels");
        Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancelled, "cancelled");
        if (palette.isEmpty() || palette.size() > MAXIMUM_PALETTE_LABELS) {
            throw failure(ImageMaskExtractionFailureCodeV2.LABEL_BUDGET_EXCEEDED,
                    "zone palette label count must be within 1.." + MAXIMUM_PALETTE_LABELS);
        }
        validatePaletteUniqueness(palette);
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

        List<ZonePaletteEntryV2> frozen = List.copyOf(palette);
        byte[] labelIndices = new byte[(int) pixels];
        byte[] confidence = new byte[(int) pixels];
        int labeledCells = 0;
        int unknownCells = 0;
        for (int z = 0; z < length; z++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("image zone label extraction was cancelled");
            }
            int rowOffset = z * width;
            for (int x = 0; x < width; x++) {
                int pixel = argbPixels[rowOffset + x];
                int alpha = pixel >>> 24;
                int index = rowOffset + x;
                if (alpha < MINIMUM_OPAQUE_ALPHA) {
                    labelIndices[index] = (byte) ExtractedZoneLabelDraftV2.UNKNOWN_INDEX;
                    confidence[index] = 0;
                    unknownCells++;
                    continue;
                }
                int red = (pixel >>> 16) & 0xFF;
                int green = (pixel >>> 8) & 0xFF;
                int blue = pixel & 0xFF;
                int bestIndex = -1;
                int bestDistance = Integer.MAX_VALUE;
                int secondDistance = Integer.MAX_VALUE;
                for (int i = 0; i < frozen.size(); i++) {
                    int distance = frozen.get(i).squaredDistance(red, green, blue);
                    if (distance < bestDistance) {
                        secondDistance = bestDistance;
                        bestDistance = distance;
                        bestIndex = i;
                    } else if (distance < secondDistance) {
                        secondDistance = distance;
                    }
                }
                boolean ambiguous = bestDistance > MAXIMUM_MATCH_DISTANCE_SQUARED
                        || (secondDistance - bestDistance) < AMBIGUOUS_MARGIN_DISTANCE_SQUARED;
                if (ambiguous || bestIndex < 0) {
                    labelIndices[index] = (byte) ExtractedZoneLabelDraftV2.UNKNOWN_INDEX;
                    confidence[index] = 0;
                    unknownCells++;
                } else {
                    labelIndices[index] = (byte) bestIndex;
                    confidence[index] = (byte) confidenceFromDistance(bestDistance);
                    labeledCells++;
                }
            }
        }
        String semanticChecksum = semanticChecksum(
                width, length, sourceChecksum, frozen, labelIndices, confidence);
        return new ExtractedZoneLabelDraftV2(
                width, length, ALGORITHM_VERSION, sourceChecksum, semanticChecksum,
                ExtractedZoneLabelDraftV2.SAMPLE_SPACE_DECLARATION, frozen,
                labelIndices, confidence, labeledCells, unknownCells);
    }

    static int confidenceFromDistance(int distanceSquared) {
        // Map d² in 0..MAXIMUM to confidence 255..1 (integer-only).
        int capped = Math.min(distanceSquared, MAXIMUM_MATCH_DISTANCE_SQUARED);
        int confidence = 255 - (capped * 254) / MAXIMUM_MATCH_DISTANCE_SQUARED;
        return Math.max(1, confidence);
    }

    private static void validatePaletteUniqueness(List<ZonePaletteEntryV2> palette) {
        for (int i = 0; i < palette.size(); i++) {
            ZonePaletteEntryV2 left = Objects.requireNonNull(palette.get(i), "palette entry");
            for (int j = i + 1; j < palette.size(); j++) {
                ZonePaletteEntryV2 right = palette.get(j);
                if (left.sample() == right.sample() || left.label().equals(right.label())) {
                    throw failure(ImageMaskExtractionFailureCodeV2.INVALID_PALETTE,
                            "zone palette samples and labels must be unique");
                }
            }
        }
    }

    private static String semanticChecksum(
            int width,
            int length,
            String sourceChecksum,
            List<ZonePaletteEntryV2> palette,
            byte[] labelIndices,
            byte[] confidence
    ) {
        MessageDigest digest = sha256();
        updateBytes(digest, ALGORITHM_VERSION.getBytes(StandardCharsets.UTF_8));
        updateBytes(digest, ExtractedZoneLabelDraftV2.SAMPLE_SPACE_DECLARATION.getBytes(StandardCharsets.UTF_8));
        updateInt(digest, width);
        updateInt(digest, length);
        updateBytes(digest, sourceChecksum.getBytes(StandardCharsets.UTF_8));
        updateInt(digest, MAXIMUM_MATCH_DISTANCE_SQUARED);
        updateInt(digest, AMBIGUOUS_MARGIN_DISTANCE_SQUARED);
        updateInt(digest, palette.size());
        for (ZonePaletteEntryV2 entry : palette) {
            updateInt(digest, entry.sample());
            updateBytes(digest, entry.label().getBytes(StandardCharsets.UTF_8));
            updateInt(digest, entry.red());
            updateInt(digest, entry.green());
            updateInt(digest, entry.blue());
        }
        updateBytes(digest, labelIndices);
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
