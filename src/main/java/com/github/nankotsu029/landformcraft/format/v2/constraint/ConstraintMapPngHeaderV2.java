package com.github.nankotsu029.landformcraft.format.v2.constraint;

/**
 * IHDR dimension check for an already-admitted constraint map PNG.
 *
 * <p>{@link SecureConstraintMapSourceLoader} verifies the path, the byte budget, the PNG signature
 * and the digest, but not that the image is the size the request declared. Both the HARD preflight
 * gate (V2-18-03) and the binding verification verb (V2-19-04) need that last step without decoding
 * pixels, so it lives here once instead of twice.</p>
 */
public final class ConstraintMapPngHeaderV2 {
    private ConstraintMapPngHeaderV2() {
    }

    /**
     * Returns {@code null} when the IHDR dimensions match, otherwise a stable, redaction-safe reason.
     *
     * @param pngBytes bytes whose 8-byte PNG signature the loader already verified
     */
    public static String dimensionMismatch(byte[] pngBytes, int expectedWidth, int expectedLength) {
        // The signature is verified upstream, so IHDR (length + "IHDR" + width + height) starts at
        // byte 8 and the two big-endian dimensions sit at offsets 16 and 20.
        if (pngBytes.length < 24) {
            return "PNG is too short to carry an IHDR header";
        }
        int width = readBigEndianInt(pngBytes, 16);
        int height = readBigEndianInt(pngBytes, 20);
        if (width != expectedWidth || height != expectedLength) {
            return "PNG dimensions " + width + "x" + height
                    + " do not match the declared " + expectedWidth + "x" + expectedLength;
        }
        return null;
    }

    private static int readBigEndianInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}
