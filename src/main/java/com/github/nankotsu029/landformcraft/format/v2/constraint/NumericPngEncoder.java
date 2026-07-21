package com.github.nankotsu029.landformcraft.format.v2.constraint;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Writes unsigned 8-bit grayscale PNGs that {@link NumericPngDecoder} can re-verify. Encoding is
 * integer-only and does not invent semantic labels.
 */
public final class NumericPngEncoder {
    public byte[] encodeU8(int width, int length, byte[] samplesRowMajor) throws IOException {
        Objects.requireNonNull(samplesRowMajor, "samplesRowMajor");
        int expected = Math.multiplyExact(width, length);
        if (width < 1 || length < 1 || samplesRowMajor.length != expected) {
            throw new IllegalArgumentException("U8 PNG sample storage does not match dimensions");
        }
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = image.getRaster();
        for (int z = 0; z < length; z++) {
            int row = z * width;
            for (int x = 0; x < width; x++) {
                raster.setSample(x, z, 0, Byte.toUnsignedInt(samplesRowMajor[row + x]));
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(256, expected + 256));
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("failed to encode U8 grayscale PNG");
        }
        image.flush();
        return output.toByteArray();
    }

    public void writeU8(Path path, int width, int length, byte[] samplesRowMajor) throws IOException {
        Objects.requireNonNull(path, "path");
        Path parent = Objects.requireNonNull(path.getParent(), "PNG path requires a parent");
        Files.createDirectories(parent);
        Files.write(path, encodeU8(width, length, samplesRowMajor));
    }
}
