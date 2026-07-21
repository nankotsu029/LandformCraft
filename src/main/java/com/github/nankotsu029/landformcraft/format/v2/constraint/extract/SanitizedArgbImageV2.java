package com.github.nankotsu029.landformcraft.format.v2.constraint.extract;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * EXIF-oriented, metadata-stripped ARGB raster ready for deterministic extraction. The source
 * checksum is the SHA-256 of the raw file bytes before decode; it never incorporates EXIF or
 * other ancillary metadata as semantic input.
 */
public final class SanitizedArgbImageV2 {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final String relativePath;
    private final String mediaType;
    private final int width;
    private final int length;
    private final int[] argbPixels;
    private final String sourceChecksum;
    private final int exifOrientation;
    private final boolean metadataDetected;

    SanitizedArgbImageV2(
            String relativePath,
            String mediaType,
            int width,
            int length,
            int[] argbPixels,
            String sourceChecksum,
            int exifOrientation,
            boolean metadataDetected
    ) {
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        this.width = width;
        this.length = length;
        Objects.requireNonNull(argbPixels, "argbPixels");
        this.sourceChecksum = Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("sanitized image dimensions must be positive");
        }
        if (argbPixels.length != Math.multiplyExact(width, length)) {
            throw new IllegalArgumentException("argbPixels length does not match dimensions");
        }
        if (!SHA_256.matcher(sourceChecksum).matches()) {
            throw new IllegalArgumentException("sourceChecksum must be a lowercase SHA-256");
        }
        if (exifOrientation < 1 || exifOrientation > 8) {
            throw new IllegalArgumentException("exifOrientation must be in 1..8");
        }
        this.argbPixels = argbPixels.clone();
        this.exifOrientation = exifOrientation;
        this.metadataDetected = metadataDetected;
    }

    public String relativePath() {
        return relativePath;
    }

    public String mediaType() {
        return mediaType;
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public int[] argbPixels() {
        return argbPixels.clone();
    }

    /** SHA-256 of the raw source file bytes admitted by the envelope. */
    public String sourceChecksum() {
        return sourceChecksum;
    }

    public int exifOrientation() {
        return exifOrientation;
    }

    public boolean metadataDetected() {
        return metadataDetected;
    }
}
