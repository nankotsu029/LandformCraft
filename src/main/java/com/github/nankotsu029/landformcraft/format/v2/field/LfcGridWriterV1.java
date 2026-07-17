package com.github.nankotsu029.landformcraft.format.v2.field;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Streaming, atomic writer for the uncompressed row-major LFC_GRID_V1 sidecar. */
public final class LfcGridWriterV1 {
    public static final long DEFAULT_MAXIMUM_ARTIFACT_BYTES = 8L * 1024L * 1024L;
    public static final long DEFAULT_MAXIMUM_WORKING_BYTES = 128L * 1024L;

    public FieldArtifactDescriptorV2 write(
            Path root,
            String relativePath,
            FieldArtifactDescriptorV2.Definition definition,
            FieldArtifactDescriptorV2.Provenance provenance,
            FieldValueSource values,
            CancellationToken token
    ) throws IOException {
        return write(root, relativePath, definition, provenance, values, token, WriteLimits.defaults());
    }

    public FieldArtifactDescriptorV2 write(
            Path root,
            String relativePath,
            FieldArtifactDescriptorV2.Definition definition,
            FieldArtifactDescriptorV2.Provenance provenance,
            FieldValueSource values,
            CancellationToken token,
            WriteLimits limits
    ) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(limits, "limits");

        // Constructing a pending descriptor validates the canonical relative path before touching disk.
        validateRelativePath(relativePath, definition, provenance);
        long artifactBytes = estimateArtifactBytes(definition, provenance);
        long workingBytes = estimatePeakWorkingBytes(definition, provenance);
        if (artifactBytes > limits.maximumArtifactBytes()) {
            throw new IOException("field artifact exceeds the configured byte budget");
        }
        if (workingBytes > limits.maximumWorkingBytes()) {
            throw new IOException("field writer exceeds the configured working-memory budget");
        }
        token.throwIfCancellationRequested();

        Path target = LfcGridPaths.resolveForWrite(root, relativePath);
        Path temporary = Files.createTempFile(target.getParent(), ".lfc-grid-", ".tmp");
        boolean published = false;
        try {
            String semanticChecksum = writeTemporary(
                    temporary, definition, provenance, values, token
            );
            token.throwIfCancellationRequested();
            String artifactChecksum;
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.READ)) {
                artifactChecksum = LfcGridFormatV1.sha256(channel, channel.size(), token);
            }
            FieldArtifactDescriptorV2 descriptor = new FieldArtifactDescriptorV2(
                    relativePath,
                    definition,
                    FieldArtifactDescriptorV2.ENCODING_VERSION,
                    artifactChecksum,
                    semanticChecksum,
                    provenance
            );
            LfcGridReaderV1.verifyTemporary(temporary, descriptor, limits.maximumArtifactBytes(), token);
            token.throwIfCancellationRequested();
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for field artifact publication", exception);
            }
            published = true;
            return descriptor;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public long estimateArtifactBytes(
            FieldArtifactDescriptorV2.Definition definition,
            FieldArtifactDescriptorV2.Provenance provenance
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(provenance, "provenance");
        int headerBytes = LfcGridFormatV1.header(
                definition, provenance, new byte[LfcGridFormatV1.CHECKSUM_BYTES]).remaining();
        return Math.addExact(headerBytes, definition.payloadBytes());
    }

    /** Upper bound for writer-owned heap; the caller-owned value source is excluded. */
    public long estimatePeakWorkingBytes(
            FieldArtifactDescriptorV2.Definition definition,
            FieldArtifactDescriptorV2.Provenance provenance
    ) {
        int headerBytes = LfcGridFormatV1.header(
                definition, provenance, new byte[LfcGridFormatV1.CHECKSUM_BYTES]).remaining();
        long rowBytes = Math.multiplyExact(
                (long) definition.width(), definition.valueType().bytesPerValue());
        long streamingWrite = Math.addExact(Math.multiplyExact(2L, headerBytes), rowBytes);
        long strictReadBack = Math.addExact(64L * 1024L, headerBytes);
        return Math.max(streamingWrite, strictReadBack);
    }

    private static String writeTemporary(
            Path temporary,
            FieldArtifactDescriptorV2.Definition definition,
            FieldArtifactDescriptorV2.Provenance provenance,
            FieldValueSource values,
            CancellationToken token
    ) throws IOException {
        ByteBuffer placeholder = LfcGridFormatV1.header(
                definition, provenance, new byte[LfcGridFormatV1.CHECKSUM_BYTES]
        );
        int headerBytes = placeholder.remaining();
        MessageDigest semanticDigest = LfcGridFormatV1.newSemanticDigest(definition);
        int rowBytes = Math.multiplyExact(definition.width(), definition.valueType().bytesPerValue());
        ByteBuffer row = ByteBuffer.allocate(rowBytes).order(ByteOrder.BIG_ENDIAN);

        try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.READ,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            LfcGridFormatV1.writeFully(channel, placeholder, 0L);
            long position = headerBytes;
            for (int z = 0; z < definition.length(); z++) {
                token.throwIfCancellationRequested();
                row.clear();
                for (int x = 0; x < definition.width(); x++) {
                    int rawValue = values.rawValueAt(x, z);
                    definition.requireRawValue(rawValue);
                    LfcGridFormatV1.putRaw(row, definition.valueType(), rawValue);
                    LfcGridFormatV1.updateRawValue(semanticDigest, rawValue);
                }
                row.flip();
                LfcGridFormatV1.writeFully(channel, row, position);
                position += rowBytes;
            }
            String semanticChecksum = HexFormat.of().formatHex(semanticDigest.digest());
            ByteBuffer finalHeader = LfcGridFormatV1.header(
                    definition, provenance, LfcGridFormatV1.checksumBytes(semanticChecksum)
            );
            LfcGridFormatV1.writeFully(channel, finalHeader, 0L);
            if (position != channel.size()) {
                throw new IOException("field writer produced an unexpected payload length");
            }
            channel.force(true);
            return semanticChecksum;
        }
    }

    private static void validateRelativePath(
            String relativePath,
            FieldArtifactDescriptorV2.Definition definition,
            FieldArtifactDescriptorV2.Provenance provenance
    ) {
        new FieldArtifactDescriptorV2(
                relativePath,
                definition,
                FieldArtifactDescriptorV2.ENCODING_VERSION,
                "0".repeat(64),
                "0".repeat(64),
                provenance
        );
    }

    public record WriteLimits(long maximumArtifactBytes, long maximumWorkingBytes) {
        public WriteLimits {
            if (maximumArtifactBytes < LfcGridFormatV1.FIXED_HEADER_BYTES + 1L
                    || maximumArtifactBytes > 64L * 1024L * 1024L
                    || maximumWorkingBytes < 1L
                    || maximumWorkingBytes > 16L * 1024L * 1024L) {
                throw new IllegalArgumentException("invalid LFC_GRID_V1 write limits");
            }
        }

        public static WriteLimits defaults() {
            return new WriteLimits(DEFAULT_MAXIMUM_ARTIFACT_BYTES, DEFAULT_MAXIMUM_WORKING_BYTES);
        }
    }
}
