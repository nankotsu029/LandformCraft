package com.github.nankotsu029.landformcraft.format.v2.field;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** Strict, checksum-verifying, bounded-window reader for LFC_GRID_V1. */
public final class LfcGridReaderV1 implements AutoCloseable {
    public static final long DEFAULT_MAXIMUM_ARTIFACT_BYTES = 8L * 1024L * 1024L;
    public static final long DEFAULT_MAXIMUM_WINDOW_BYTES = 8L * 1024L * 1024L;

    private final FileChannel channel;
    private final FieldArtifactDescriptorV2 descriptor;
    private final long payloadOffset;
    private final ReadLimits limits;

    private LfcGridReaderV1(
            FileChannel channel,
            FieldArtifactDescriptorV2 descriptor,
            long payloadOffset,
            ReadLimits limits
    ) {
        this.channel = channel;
        this.descriptor = descriptor;
        this.payloadOffset = payloadOffset;
        this.limits = limits;
    }

    public static LfcGridReaderV1 open(
            Path root,
            FieldArtifactDescriptorV2 descriptor
    ) throws IOException {
        return open(root, descriptor, ReadLimits.defaults(), () -> false);
    }

    public static LfcGridReaderV1 open(
            Path root,
            FieldArtifactDescriptorV2 descriptor,
            ReadLimits limits
    ) throws IOException {
        return open(root, descriptor, limits, () -> false);
    }

    public static LfcGridReaderV1 open(
            Path root,
            FieldArtifactDescriptorV2 descriptor,
            ReadLimits limits,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(descriptor, "descriptor");
        Path file = LfcGridPaths.resolveForRead(root, descriptor.relativePath());
        return openFile(file, descriptor, limits, token);
    }

    static void verifyTemporary(
            Path temporary,
            FieldArtifactDescriptorV2 descriptor,
            long maximumArtifactBytes,
            CancellationToken token
    ) throws IOException {
        ReadLimits limits = new ReadLimits(maximumArtifactBytes, DEFAULT_MAXIMUM_WINDOW_BYTES);
        try (LfcGridReaderV1 reader = openFile(temporary, descriptor, limits, token)) {
            if (!reader.descriptor().equals(descriptor)) {
                throw new IOException("verified field descriptor changed unexpectedly");
            }
        }
    }

    public FieldArtifactDescriptorV2 descriptor() {
        return descriptor;
    }

    public FieldWindow readWindow(int originX, int originZ, int width, int length) throws IOException {
        return readWindow(originX, originZ, width, length, () -> false);
    }

    public FieldWindow readWindow(
            int originX,
            int originZ,
            int width,
            int length,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(token, "token");
        requireOpen();
        var definition = descriptor.definition();
        if (originX < 0 || originZ < 0 || width < 1 || length < 1
                || (long) originX + width > definition.width()
                || (long) originZ + length > definition.length()) {
            throw new IndexOutOfBoundsException("field window lies outside artifact dimensions");
        }
        long workingBytes = estimateWindowWorkingBytes(width, length, definition.valueType());
        if (workingBytes > limits.maximumWindowBytes()) {
            throw new IOException("field window exceeds configured memory budget");
        }
        int cellCount = Math.multiplyExact(width, length);
        int[] values = new int[cellCount];
        int rowBytes = Math.multiplyExact(width, definition.valueType().bytesPerValue());
        ByteBuffer row = ByteBuffer.allocate(rowBytes).order(ByteOrder.BIG_ENDIAN);
        for (int localZ = 0; localZ < length; localZ++) {
            token.throwIfCancellationRequested();
            row.clear();
            long cellOffset = Math.addExact(
                    Math.multiplyExact((long) originZ + localZ, definition.width()),
                    originX
            );
            long byteOffset = Math.addExact(
                    payloadOffset,
                    Math.multiplyExact(cellOffset, definition.valueType().bytesPerValue())
            );
            LfcGridFormatV1.readFully(channel, row, byteOffset);
            row.flip();
            int destination = localZ * width;
            for (int localX = 0; localX < width; localX++) {
                values[destination + localX] = LfcGridFormatV1.getRaw(row, definition.valueType());
            }
        }
        return new FieldWindow(originX, originZ, width, length, values, definition);
    }

    /** Samples in cell-index fixed point: 0 is the center of cell 0, SCALE is cell 1. */
    public FieldSample sample(long xMillionths, long zMillionths) throws IOException {
        return sample(xMillionths, zMillionths, () -> false);
    }

    public FieldSample sample(
            long xMillionths,
            long zMillionths,
            CancellationToken token
    ) throws IOException {
        return switch (descriptor.definition().sampling()) {
            case NEAREST -> sampleNearest(xMillionths, zMillionths, token);
            case BILINEAR_FIXED -> sampleBilinearFixed(xMillionths, zMillionths, token);
        };
    }

    public FieldSample sampleNearest(long xMillionths, long zMillionths) throws IOException {
        return sampleNearest(xMillionths, zMillionths, () -> false);
    }

    public FieldSample sampleNearest(
            long xMillionths,
            long zMillionths,
            CancellationToken token
    ) throws IOException {
        requireSampleCoordinate(xMillionths, zMillionths);
        int x = Math.toIntExact((xMillionths + FieldArtifactDescriptorV2.FIXED_SCALE / 2L)
                / FieldArtifactDescriptorV2.FIXED_SCALE);
        int z = Math.toIntExact((zMillionths + FieldArtifactDescriptorV2.FIXED_SCALE / 2L)
                / FieldArtifactDescriptorV2.FIXED_SCALE);
        return readWindow(x, z, 1, 1, token).sampleAt(0, 0);
    }

    public FieldSample sampleBilinearFixed(long xMillionths, long zMillionths) throws IOException {
        return sampleBilinearFixed(xMillionths, zMillionths, () -> false);
    }

    public FieldSample sampleBilinearFixed(
            long xMillionths,
            long zMillionths,
            CancellationToken token
    ) throws IOException {
        requireSampleCoordinate(xMillionths, zMillionths);
        long scale = FieldArtifactDescriptorV2.FIXED_SCALE;
        int x0 = Math.toIntExact(xMillionths / scale);
        int z0 = Math.toIntExact(zMillionths / scale);
        int x1 = Math.min(descriptor.definition().width() - 1, x0 + 1);
        int z1 = Math.min(descriptor.definition().length() - 1, z0 + 1);
        long fractionX = xMillionths % scale;
        long fractionZ = zMillionths % scale;
        FieldWindow window = readWindow(x0, z0, x1 - x0 + 1, z1 - z0 + 1, token);
        FieldSample northWest = window.sampleAt(0, 0);
        FieldSample northEast = window.sampleAt(x1 - x0, 0);
        FieldSample southWest = window.sampleAt(0, z1 - z0);
        FieldSample southEast = window.sampleAt(x1 - x0, z1 - z0);
        if (northWest.noData() || northEast.noData() || southWest.noData() || southEast.noData()) {
            return FieldSample.missing();
        }
        long north = lerp(northWest.valueMillionths(), northEast.valueMillionths(), fractionX);
        long south = lerp(southWest.valueMillionths(), southEast.valueMillionths(), fractionX);
        return FieldSample.value(lerp(north, south, fractionZ));
    }

    public static long estimateWindowWorkingBytes(
            int width,
            int length,
            FieldArtifactDescriptorV2.FieldValueType valueType
    ) {
        Objects.requireNonNull(valueType, "valueType");
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("window dimensions must be positive");
        }
        long values = Math.multiplyExact(Math.multiplyExact((long) width, length), Integer.BYTES);
        long row = Math.multiplyExact((long) width, valueType.bytesPerValue());
        return Math.addExact(values, row);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private static LfcGridReaderV1 openFile(
            Path file,
            FieldArtifactDescriptorV2 descriptor,
            ReadLimits limits,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(token, "token");
        FileChannel channel = FileChannel.open(
                file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
        boolean success = false;
        try {
            long size = channel.size();
            if (size < LfcGridFormatV1.FIXED_HEADER_BYTES || size > limits.maximumArtifactBytes()) {
                throw new IOException("field artifact size exceeds configured bounds");
            }
            String artifactChecksum = LfcGridFormatV1.sha256(channel, size, token);
            if (!artifactChecksum.equals(descriptor.artifactChecksum())) {
                throw new IOException("field artifact checksum mismatch");
            }
            LfcGridFormatV1.Header header = LfcGridFormatV1.readHeader(channel, size);
            if (!header.definition().equals(descriptor.definition())
                    || !header.provenance().equals(descriptor.provenance())) {
                throw new IOException("field artifact header does not match its descriptor");
            }
            if (!header.semanticChecksum().equals(descriptor.semanticChecksum())) {
                throw new IOException("field semantic checksum does not match its descriptor");
            }
            verifySemanticChecksum(channel, header, token);
            if (channel.size() != size) {
                throw new IOException("field artifact changed during verification");
            }
            success = true;
            return new LfcGridReaderV1(channel, descriptor, header.headerBytes(), limits);
        } finally {
            if (!success) {
                channel.close();
            }
        }
    }

    private static void verifySemanticChecksum(
            FileChannel channel,
            LfcGridFormatV1.Header header,
            CancellationToken token
    ) throws IOException {
        var definition = header.definition();
        MessageDigest digest = LfcGridFormatV1.newSemanticDigest(definition);
        int bytesPerValue = definition.valueType().bytesPerValue();
        int maximumCells = Math.max(1, (64 * 1024) / bytesPerValue);
        ByteBuffer values = ByteBuffer.allocate(maximumCells * bytesPerValue).order(ByteOrder.BIG_ENDIAN);
        long cellsRemaining = definition.cellCount();
        long position = header.headerBytes();
        while (cellsRemaining > 0L) {
            token.throwIfCancellationRequested();
            int cells = (int) Math.min(maximumCells, cellsRemaining);
            values.clear();
            values.limit(cells * bytesPerValue);
            LfcGridFormatV1.readFully(channel, values, position);
            values.flip();
            for (int index = 0; index < cells; index++) {
                LfcGridFormatV1.updateRawValue(
                        digest, LfcGridFormatV1.getRaw(values, definition.valueType())
                );
            }
            int bytes = cells * bytesPerValue;
            position += bytes;
            cellsRemaining -= cells;
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(header.semanticChecksum())) {
            throw new IOException("field semantic checksum mismatch");
        }
    }

    private void requireSampleCoordinate(long xMillionths, long zMillionths) {
        long maximumX = Math.multiplyExact(
                descriptor.definition().width() - 1L, FieldArtifactDescriptorV2.FIXED_SCALE);
        long maximumZ = Math.multiplyExact(
                descriptor.definition().length() - 1L, FieldArtifactDescriptorV2.FIXED_SCALE);
        if (xMillionths < 0L || zMillionths < 0L
                || xMillionths > maximumX || zMillionths > maximumZ) {
            throw new IndexOutOfBoundsException("fixed-point sample coordinate lies outside field");
        }
    }

    private void requireOpen() throws IOException {
        if (!channel.isOpen()) {
            throw new IOException("field reader is closed");
        }
    }

    private static long lerp(long first, long second, long fractionMillionths) {
        long delta = Math.subtractExact(second, first);
        long numerator = Math.multiplyExact(delta, fractionMillionths);
        long half = FieldArtifactDescriptorV2.FIXED_SCALE / 2L;
        long rounded = numerator >= 0L
                ? (numerator + half) / FieldArtifactDescriptorV2.FIXED_SCALE
                : -((-numerator + half) / FieldArtifactDescriptorV2.FIXED_SCALE);
        return Math.addExact(first, rounded);
    }

    public record ReadLimits(long maximumArtifactBytes, long maximumWindowBytes) {
        public ReadLimits {
            if (maximumArtifactBytes < LfcGridFormatV1.FIXED_HEADER_BYTES + 1L
                    || maximumArtifactBytes > 64L * 1024L * 1024L
                    || maximumWindowBytes < Integer.BYTES
                    || maximumWindowBytes > 16L * 1024L * 1024L) {
                throw new IllegalArgumentException("invalid LFC_GRID_V1 read limits");
            }
        }

        public static ReadLimits defaults() {
            return new ReadLimits(DEFAULT_MAXIMUM_ARTIFACT_BYTES, DEFAULT_MAXIMUM_WINDOW_BYTES);
        }
    }
}
