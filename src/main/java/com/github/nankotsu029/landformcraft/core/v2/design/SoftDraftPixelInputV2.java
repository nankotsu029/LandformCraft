package com.github.nankotsu029.landformcraft.core.v2.design;

import java.util.Objects;
import java.util.regex.Pattern;

/** Sanitized in-memory reference image pixels for soft draft extraction. */
public record SoftDraftPixelInputV2(
        int width,
        int length,
        int[] argbPixels,
        String sourceChecksum
) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public SoftDraftPixelInputV2 {
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("width and length must be positive");
        }
        Objects.requireNonNull(argbPixels, "argbPixels");
        if (argbPixels.length != (long) width * length) {
            throw new IllegalArgumentException("argbPixels length does not match dimensions");
        }
        Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        if (!SHA_256.matcher(sourceChecksum).matches()) {
            throw new IllegalArgumentException("sourceChecksum must be a lowercase SHA-256");
        }
        argbPixels = argbPixels.clone();
    }

    public int[] argbPixels() {
        return argbPixels.clone();
    }
}
