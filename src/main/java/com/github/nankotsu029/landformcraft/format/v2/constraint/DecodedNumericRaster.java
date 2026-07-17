package com.github.nankotsu029.landformcraft.format.v2.constraint;

import java.util.Objects;

/** Compact immutable row-major unsigned raster returned by the strict numeric PNG decoder. */
public final class DecodedNumericRaster {
    private final int width;
    private final int length;
    private final NumericPngEncoding.NumericKind kind;
    private final NumericPngEncoding.SampleType sampleType;
    private final String sourceChecksum;
    private final byte[] u8Samples;
    private final short[] u16Samples;

    DecodedNumericRaster(
            int width,
            int length,
            NumericPngEncoding.NumericKind kind,
            NumericPngEncoding.SampleType sampleType,
            String sourceChecksum,
            byte[] u8Samples,
            short[] u16Samples
    ) {
        this.width = width;
        this.length = length;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sampleType = Objects.requireNonNull(sampleType, "sampleType");
        this.sourceChecksum = Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        this.u8Samples = u8Samples;
        this.u16Samples = u16Samples;
        int expected = Math.multiplyExact(width, length);
        if (sampleType == NumericPngEncoding.SampleType.U8
                && (u8Samples == null || u8Samples.length != expected || u16Samples != null)
                || sampleType == NumericPngEncoding.SampleType.U16
                && (u16Samples == null || u16Samples.length != expected || u8Samples != null)) {
            throw new IllegalArgumentException("numeric raster storage does not match sample type");
        }
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public NumericPngEncoding.NumericKind kind() {
        return kind;
    }

    public NumericPngEncoding.SampleType sampleType() {
        return sampleType;
    }

    public String sourceChecksum() {
        return sourceChecksum;
    }

    public int sample(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("numeric raster coordinate outside bounds");
        }
        int index = z * width + x;
        return sampleType == NumericPngEncoding.SampleType.U8
                ? Byte.toUnsignedInt(u8Samples[index])
                : Short.toUnsignedInt(u16Samples[index]);
    }
}
