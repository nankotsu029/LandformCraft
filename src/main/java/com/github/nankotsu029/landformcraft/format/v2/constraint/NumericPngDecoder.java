package com.github.nankotsu029.landformcraft.format.v2.constraint;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;

/** Strict grayscale PNG decoder that preserves exact unsigned 8/16-bit samples. */
public final class NumericPngDecoder {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final long DECODE_FIXED_OVERHEAD_BYTES = 1024L * 1024L;

    public DecodedNumericRaster decode(
            LoadedConstraintMapSource source,
            ConstraintMapSourceSpec specification,
            NumericPngEncoding encoding,
            ConstraintMapDecodeLimits limits,
            BooleanSupplier cancellationRequested
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        if (!source.sourceId().equals(specification.sourceId())
                || !source.relativePath().equals(specification.relativePath())
                || !source.sourceChecksum().equals(specification.expectedSha256())) {
            throw failure(ConstraintMapFailureCode.INVALID_DESCRIPTOR,
                    "loaded source does not match its numeric decode descriptor");
        }
        ensureNotCancelled(cancellationRequested);
        byte[] bytes = source.contentForDecode();
        PngHeader header = inspect(bytes, cancellationRequested);
        validateHeader(header, source, specification, encoding, limits);

        long peak = estimatedPeakBytes(source.sourceBytes(), header.width(), header.length(), encoding.sampleType());
        if (peak > limits.maximumWorkingBytes()) {
            throw failure(ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED,
                    "numeric PNG decode working set exceeds its budget");
        }
        BufferedImage image = readImage(bytes, header, cancellationRequested);
        try {
            Raster raster = image.getRaster();
            validateRaster(raster, header, encoding.sampleType());
            return copySamples(raster, header, source.sourceChecksum(), encoding, cancellationRequested);
        } finally {
            image.flush();
        }
    }

    public static long estimatedPeakBytes(
            long sourceBytes,
            int width,
            int length,
            NumericPngEncoding.SampleType sampleType
    ) {
        Objects.requireNonNull(sampleType, "sampleType");
        try {
            long samples = Math.multiplyExact((long) width, length);
            long decoded = Math.multiplyExact(samples, sampleType.bytes());
            // The verified source remains retained while MemoryCacheImageInputStream caches another copy;
            // ImageIO's decoded raster and the compact immutable raster each retain one decoded sample set.
            return Math.addExact(Math.addExact(Math.multiplyExact(sourceBytes, 2L),
                            Math.multiplyExact(decoded, 2L)),
                    DECODE_FIXED_OVERHEAD_BYTES);
        } catch (ArithmeticException exception) {
            throw new ConstraintMapInputException(
                    ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED,
                    "numeric PNG decode working-set estimate overflow", exception);
        }
    }

    private static PngHeader inspect(byte[] bytes, BooleanSupplier cancellationRequested) {
        if (bytes.length < PNG_SIGNATURE.length + 12) {
            throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "truncated PNG constraint map");
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (bytes[index] != PNG_SIGNATURE[index]) {
                throw failure(ConstraintMapFailureCode.INVALID_MAGIC, "constraint-map source is not a PNG");
            }
        }

        int position = PNG_SIGNATURE.length;
        boolean seenHeader = false;
        boolean seenData = false;
        boolean dataEnded = false;
        boolean seenEnd = false;
        int paletteCount = 0;
        int width = 0;
        int length = 0;
        int bitDepth = 0;
        int colorType = -1;
        int interlace = -1;
        while (position < bytes.length) {
            ensureNotCancelled(cancellationRequested);
            if (bytes.length - position < 12) {
                throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "truncated PNG chunk");
            }
            long unsignedLength = Integer.toUnsignedLong(readInt(bytes, position));
            if (unsignedLength > Integer.MAX_VALUE) {
                throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "PNG chunk is too large");
            }
            int chunkLength = (int) unsignedLength;
            long chunkEnd = (long) position + 12L + chunkLength;
            if (chunkEnd > bytes.length) {
                throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "truncated PNG chunk payload");
            }
            int typeOffset = position + 4;
            String type = chunkType(bytes, typeOffset);
            verifyChunkCrc(bytes, typeOffset, chunkLength);
            int dataOffset = typeOffset + 4;
            if (!seenHeader && !"IHDR".equals(type)) {
                throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "PNG IHDR must be first");
            }
            switch (type) {
                case "IHDR" -> {
                    if (seenHeader || chunkLength != 13) {
                        throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "invalid PNG IHDR");
                    }
                    seenHeader = true;
                    width = readInt(bytes, dataOffset);
                    length = readInt(bytes, dataOffset + 4);
                    bitDepth = Byte.toUnsignedInt(bytes[dataOffset + 8]);
                    colorType = Byte.toUnsignedInt(bytes[dataOffset + 9]);
                    int compression = Byte.toUnsignedInt(bytes[dataOffset + 10]);
                    int filter = Byte.toUnsignedInt(bytes[dataOffset + 11]);
                    interlace = Byte.toUnsignedInt(bytes[dataOffset + 12]);
                    if (width < 1 || length < 1 || compression != 0 || filter != 0
                            || interlace < 0 || interlace > 1) {
                        throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "invalid PNG IHDR values");
                    }
                }
                case "IDAT" -> {
                    if (dataEnded || seenEnd) {
                        throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE,
                                "PNG IDAT chunks must be contiguous and precede IEND");
                    }
                    seenData = true;
                }
                case "IEND" -> {
                    if (!seenData || seenEnd || chunkLength != 0) {
                        throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "invalid PNG IEND");
                    }
                    seenEnd = true;
                    if (chunkEnd != bytes.length) {
                        throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE,
                                "PNG contains bytes or chunks after IEND");
                    }
                }
                case "acTL", "fcTL", "fdAT" -> throw failure(
                        ConstraintMapFailureCode.MULTI_FRAME, "animated PNG constraint maps are not allowed");
                case "PLTE" -> {
                    if (seenData) {
                        throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "PNG PLTE appears after image data");
                    }
                    paletteCount++;
                    if (paletteCount > 1) {
                        throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE,
                                "PNG contains duplicate PLTE chunks");
                    }
                }
                default -> {
                    if (isCritical(bytes[typeOffset])) {
                        throw failure(ConstraintMapFailureCode.UNSUPPORTED_FORMAT,
                                "unsupported critical PNG chunk");
                    }
                }
            }
            if (seenData && !"IDAT".equals(type) && !"IEND".equals(type)) {
                dataEnded = true;
            }
            position = Math.toIntExact(chunkEnd);
        }
        if (!seenHeader || !seenData || !seenEnd) {
            throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "PNG is missing a required chunk");
        }
        return new PngHeader(width, length, bitDepth, colorType, interlace, paletteCount != 0);
    }

    private static void validateHeader(
            PngHeader header,
            LoadedConstraintMapSource source,
            ConstraintMapSourceSpec specification,
            NumericPngEncoding encoding,
            ConstraintMapDecodeLimits limits
    ) {
        if (!"image/png".equals(source.mediaType())) {
            throw failure(ConstraintMapFailureCode.UNSUPPORTED_FORMAT,
                    "numeric constraint maps must be PNG");
        }
        if (header.width() != specification.expectedWidth()
                || header.length() != specification.expectedLength()) {
            throw failure(ConstraintMapFailureCode.DIMENSIONS_MISMATCH,
                    "numeric PNG dimensions do not match the source descriptor");
        }
        if (header.width() > limits.maximumDimension() || header.length() > limits.maximumDimension()) {
            throw failure(ConstraintMapFailureCode.DIMENSIONS_EXCEEDED,
                    "numeric PNG dimensions exceed the decode limit");
        }
        int shorter = Math.min(header.width(), header.length());
        int longer = Math.max(header.width(), header.length());
        if ((long) longer > (long) shorter * limits.maximumAspectRatio()) {
            throw failure(ConstraintMapFailureCode.DIMENSIONS_EXCEEDED,
                    "numeric PNG aspect ratio exceeds the decode limit");
        }
        long pixels = Math.multiplyExact((long) header.width(), header.length());
        if (pixels > limits.maximumPixels()) {
            throw failure(ConstraintMapFailureCode.PIXELS_EXCEEDED,
                    "numeric PNG pixels exceed the decode limit");
        }
        long decodedBytes = Math.multiplyExact(pixels, encoding.sampleType().bytes());
        if (decodedBytes > limits.maximumDecodedSampleBytes()) {
            throw failure(ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED,
                    "numeric PNG decoded samples exceed the byte budget");
        }
        if (header.colorType() != 0 || header.bitDepth() != encoding.sampleType().bits()) {
            throw failure(ConstraintMapFailureCode.SAMPLE_TYPE_MISMATCH,
                    "numeric PNG must be exact grayscale U8 or U16 as declared");
        }
        if (header.palettePresent()) {
            throw failure(ConstraintMapFailureCode.UNSUPPORTED_FORMAT,
                    "grayscale numeric PNG constraint maps must not contain a palette");
        }
        if (header.interlace() != 0) {
            throw failure(ConstraintMapFailureCode.UNSUPPORTED_FORMAT,
                    "interlaced numeric PNG constraint maps are not supported");
        }
    }

    private static BufferedImage readImage(
            byte[] bytes,
            PngHeader header,
            BooleanSupplier cancellationRequested
    ) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE,
                        "no PNG decoder is available for the constraint map");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                if (!reader.getFormatName().toLowerCase(Locale.ROOT).contains("png")) {
                    throw failure(ConstraintMapFailureCode.UNSUPPORTED_FORMAT,
                            "constraint-map decoder is not a PNG decoder");
                }
                if (reader.getNumImages(true) != 1) {
                    throw failure(ConstraintMapFailureCode.MULTI_FRAME,
                            "multi-frame constraint maps are not allowed");
                }
                if (reader.getWidth(0) != header.width() || reader.getHeight(0) != header.length()) {
                    throw failure(ConstraintMapFailureCode.DIMENSIONS_MISMATCH,
                            "PNG decoder dimensions disagree with the verified header");
                }
                ensureNotCancelled(cancellationRequested);
                BufferedImage image = reader.read(0);
                ensureNotCancelled(cancellationRequested);
                if (image == null) {
                    throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE,
                            "PNG decoder returned no numeric image");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (ConstraintMapInputException exception) {
            throw exception;
        } catch (CancellationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ConstraintMapInputException(
                    ConstraintMapFailureCode.MALFORMED_IMAGE,
                    "numeric PNG decode failed", exception);
        }
    }

    private static void validateRaster(
            Raster raster,
            PngHeader header,
            NumericPngEncoding.SampleType sampleType
    ) {
        int expectedDataType = sampleType == NumericPngEncoding.SampleType.U8
                ? DataBuffer.TYPE_BYTE : DataBuffer.TYPE_USHORT;
        int[] sampleSizes = raster.getSampleModel().getSampleSize();
        if (raster.getWidth() != header.width() || raster.getHeight() != header.length()
                || raster.getNumBands() != 1 || sampleSizes.length != 1
                || sampleSizes[0] != sampleType.bits()
                || raster.getDataBuffer().getDataType() != expectedDataType) {
            throw failure(ConstraintMapFailureCode.SAMPLE_TYPE_MISMATCH,
                    "PNG decoder did not preserve the declared raw numeric sample type");
        }
    }

    private static DecodedNumericRaster copySamples(
            Raster raster,
            PngHeader header,
            String sourceChecksum,
            NumericPngEncoding encoding,
            BooleanSupplier cancellationRequested
    ) {
        int count = Math.multiplyExact(header.width(), header.length());
        byte[] u8 = encoding.sampleType() == NumericPngEncoding.SampleType.U8 ? new byte[count] : null;
        short[] u16 = encoding.sampleType() == NumericPngEncoding.SampleType.U16 ? new short[count] : null;
        for (int z = 0; z < header.length(); z++) {
            if ((z & 31) == 0) {
                ensureNotCancelled(cancellationRequested);
            }
            for (int x = 0; x < header.width(); x++) {
                int index = z * header.width() + x;
                int sample = raster.getSample(x, z, 0);
                if (u8 != null) {
                    u8[index] = (byte) sample;
                } else {
                    u16[index] = (short) sample;
                }
            }
        }
        ensureNotCancelled(cancellationRequested);
        return new DecodedNumericRaster(
                header.width(), header.length(), encoding.kind(), encoding.sampleType(),
                sourceChecksum, u8, u16);
    }

    private static void verifyChunkCrc(byte[] bytes, int typeOffset, int chunkLength) {
        CRC32 crc = new CRC32();
        crc.update(bytes, typeOffset, 4 + chunkLength);
        long expected = Integer.toUnsignedLong(readInt(bytes, typeOffset + 4 + chunkLength));
        if (crc.getValue() != expected) {
            throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "PNG chunk CRC mismatch");
        }
    }

    private static String chunkType(byte[] bytes, int offset) {
        char[] type = new char[4];
        for (int index = 0; index < type.length; index++) {
            int value = Byte.toUnsignedInt(bytes[offset + index]);
            if (!(value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z')) {
                throw failure(ConstraintMapFailureCode.MALFORMED_IMAGE, "PNG chunk type is invalid");
            }
            type[index] = (char) value;
        }
        return new String(type);
    }

    private static boolean isCritical(byte firstTypeByte) {
        return (firstTypeByte & 0x20) == 0;
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24
                | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8
                | bytes[offset + 3] & 0xff;
    }

    private static void ensureNotCancelled(BooleanSupplier cancellationRequested) {
        if (cancellationRequested.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("numeric constraint-map decoding was cancelled");
        }
    }

    private static ConstraintMapInputException failure(ConstraintMapFailureCode code, String message) {
        return new ConstraintMapInputException(code, message);
    }

    private record PngHeader(
            int width,
            int length,
            int bitDepth,
            int colorType,
            int interlace,
            boolean palettePresent
    ) {
    }
}
