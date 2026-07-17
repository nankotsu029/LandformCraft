package com.github.nankotsu029.landformcraft.format.v2.field;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

final class LfcGridFormatV1 {
    static final byte[] MAGIC = "LFCGRID1".getBytes(StandardCharsets.US_ASCII);
    static final int FORMAT_VERSION = 1;
    static final int FIXED_HEADER_BYTES = 108;
    static final int MAX_HEADER_BYTES = 2_048;
    static final int SEMANTIC_CHECKSUM_OFFSET = 76;
    static final int CHECKSUM_BYTES = 32;

    private static final byte[] SEMANTIC_DOMAIN =
            "LFC_GRID_SEMANTIC_V1".getBytes(StandardCharsets.US_ASCII);

    private LfcGridFormatV1() {
    }

    static ByteBuffer header(
            FieldArtifactDescriptorV2.Definition definition,
            FieldArtifactDescriptorV2.Provenance provenance,
            byte[] semanticChecksum
    ) {
        byte[][] strings = {
                utf8(definition.fieldId()),
                utf8(provenance.sourceId()),
                utf8(provenance.sourceChecksum()),
                utf8(provenance.decoderId()),
                utf8(provenance.decoderVersion()),
                utf8(provenance.transformId())
        };
        int headerBytes = FIXED_HEADER_BYTES;
        for (byte[] value : strings) {
            if (value.length > 65_535) {
                throw new IllegalArgumentException("field header string is too long");
            }
            headerBytes = Math.addExact(headerBytes, value.length);
        }
        if (headerBytes > MAX_HEADER_BYTES) {
            throw new IllegalArgumentException("field header exceeds format limit");
        }
        if (semanticChecksum.length != CHECKSUM_BYTES) {
            throw new IllegalArgumentException("semantic checksum must be 32 bytes");
        }

        ByteBuffer result = ByteBuffer.allocate(headerBytes).order(ByteOrder.BIG_ENDIAN);
        result.put(MAGIC);
        result.putInt(FORMAT_VERSION);
        result.putInt(headerBytes);
        result.putLong(definition.payloadBytes());
        result.putInt(definition.width());
        result.putInt(definition.length());
        result.put(valueTypeCode(definition.valueType()));
        result.put(semanticCode(definition.semantic()));
        result.put(coordinateCode(definition.coordinateSpace()));
        result.put(samplingCode(definition.sampling()));
        result.putLong(definition.scaleMillionths());
        result.putLong(definition.offsetMillionths());
        result.put((byte) (definition.hasNoData() ? 1 : 0));
        result.put(new byte[3]);
        result.putInt(definition.noDataRaw());
        result.put(sourceKindCode(provenance.sourceKind()));
        result.put(new byte[3]);
        for (byte[] value : strings) {
            result.putShort((short) value.length);
        }
        result.put(semanticChecksum);
        for (byte[] value : strings) {
            result.put(value);
        }
        return result.flip();
    }

    static Header readHeader(FileChannel channel, long fileSize) throws IOException {
        if (fileSize < FIXED_HEADER_BYTES) {
            throw new IOException("LFC_GRID_V1 file is shorter than its fixed header");
        }
        ByteBuffer fixed = ByteBuffer.allocate(FIXED_HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        readFully(channel, fixed, 0L);
        fixed.flip();
        byte[] magic = new byte[MAGIC.length];
        fixed.get(magic);
        if (!Arrays.equals(MAGIC, magic)) {
            throw new IOException("invalid LFC_GRID_V1 magic bytes");
        }
        int version = fixed.getInt();
        if (version != FORMAT_VERSION) {
            throw new IOException("unsupported LFC_GRID encoding version: " + version);
        }
        int headerBytes = fixed.getInt();
        long payloadBytes = fixed.getLong();
        int width = fixed.getInt();
        int length = fixed.getInt();
        byte valueType = fixed.get();
        byte semantic = fixed.get();
        byte coordinate = fixed.get();
        byte sampling = fixed.get();
        long scale = fixed.getLong();
        long offset = fixed.getLong();
        byte hasNoData = fixed.get();
        requireZero(fixed.get(), "reserved header byte");
        requireZero(fixed.get(), "reserved header byte");
        requireZero(fixed.get(), "reserved header byte");
        int noData = fixed.getInt();
        byte sourceKind = fixed.get();
        requireZero(fixed.get(), "reserved provenance byte");
        requireZero(fixed.get(), "reserved provenance byte");
        requireZero(fixed.get(), "reserved provenance byte");
        int[] lengths = new int[6];
        int expectedHeader = FIXED_HEADER_BYTES;
        for (int index = 0; index < lengths.length; index++) {
            lengths[index] = Short.toUnsignedInt(fixed.getShort());
            expectedHeader = Math.addExact(expectedHeader, lengths[index]);
        }
        byte[] checksum = new byte[CHECKSUM_BYTES];
        fixed.get(checksum);

        if (headerBytes != expectedHeader || headerBytes < FIXED_HEADER_BYTES
                || headerBytes > MAX_HEADER_BYTES) {
            throw new IOException("invalid LFC_GRID_V1 header length");
        }
        if (payloadBytes < 1L || fileSize != Math.addExact((long) headerBytes, payloadBytes)) {
            throw new IOException("LFC_GRID_V1 payload length does not match file length");
        }
        if (hasNoData != 0 && hasNoData != 1) {
            throw new IOException("invalid LFC_GRID_V1 no-data flag");
        }

        ByteBuffer variable = ByteBuffer.allocate(headerBytes - FIXED_HEADER_BYTES);
        readFully(channel, variable, FIXED_HEADER_BYTES);
        variable.flip();
        String[] strings = new String[lengths.length];
        for (int index = 0; index < lengths.length; index++) {
            byte[] value = new byte[lengths[index]];
            variable.get(value);
            strings[index] = strictUtf8(value);
        }
        if (variable.hasRemaining()) {
            throw new IOException("LFC_GRID_V1 header has trailing bytes");
        }

        try {
            FieldArtifactDescriptorV2.Definition definition = new FieldArtifactDescriptorV2.Definition(
                    strings[0], semantic(semantic), valueType(valueType), width, length,
                    coordinate(coordinate), sampling(sampling), scale, offset, hasNoData == 1, noData
            );
            if (definition.payloadBytes() != payloadBytes) {
                throw new IOException("LFC_GRID_V1 payload length does not match dimensions and value type");
            }
            FieldArtifactDescriptorV2.Provenance provenance = new FieldArtifactDescriptorV2.Provenance(
                    sourceKind(sourceKind), strings[1], strings[2], strings[3], strings[4], strings[5]
            );
            return new Header(
                    definition,
                    provenance,
                    HexFormat.of().formatHex(checksum),
                    headerBytes,
                    payloadBytes
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid LFC_GRID_V1 header metadata", exception);
        }
    }

    static MessageDigest newSemanticDigest(FieldArtifactDescriptorV2.Definition definition) {
        MessageDigest digest = newSha256();
        digest.update(SEMANTIC_DOMAIN);
        updateString(digest, definition.fieldId());
        updateInt(digest, Byte.toUnsignedInt(semanticCode(definition.semantic())));
        updateInt(digest, Byte.toUnsignedInt(valueTypeCode(definition.valueType())));
        updateInt(digest, definition.width());
        updateInt(digest, definition.length());
        updateInt(digest, Byte.toUnsignedInt(coordinateCode(definition.coordinateSpace())));
        updateInt(digest, Byte.toUnsignedInt(samplingCode(definition.sampling())));
        updateLong(digest, definition.scaleMillionths());
        updateLong(digest, definition.offsetMillionths());
        updateInt(digest, definition.hasNoData() ? 1 : 0);
        updateInt(digest, definition.noDataRaw());
        return digest;
    }

    static void updateRawValue(MessageDigest digest, int rawValue) {
        updateInt(digest, rawValue);
    }

    static String sha256(FileChannel channel, long size, CancellationToken token) throws IOException {
        MessageDigest digest = newSha256();
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        long position = 0L;
        while (position < size) {
            token.throwIfCancellationRequested();
            buffer.clear();
            buffer.limit((int) Math.min(buffer.capacity(), size - position));
            readFully(channel, buffer, position);
            buffer.flip();
            digest.update(buffer);
            position += buffer.limit();
        }
        if (channel.size() != size) {
            throw new IOException("field artifact changed while checksumming");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static int getRaw(ByteBuffer buffer, FieldArtifactDescriptorV2.FieldValueType valueType) {
        return switch (valueType) {
            case U8 -> Byte.toUnsignedInt(buffer.get());
            case U16 -> Short.toUnsignedInt(buffer.getShort());
            case I32 -> buffer.getInt();
        };
    }

    static void putRaw(ByteBuffer buffer, FieldArtifactDescriptorV2.FieldValueType valueType, int raw) {
        switch (valueType) {
            case U8 -> buffer.put((byte) raw);
            case U16 -> buffer.putShort((short) raw);
            case I32 -> buffer.putInt(raw);
        }
    }

    static void readFully(FileChannel channel, ByteBuffer target, long position) throws IOException {
        long cursor = position;
        while (target.hasRemaining()) {
            int read = channel.read(target, cursor);
            if (read < 0) {
                throw new EOFException("unexpected end of LFC_GRID_V1 artifact");
            }
            if (read == 0) {
                throw new IOException("zero-byte read from LFC_GRID_V1 artifact");
            }
            cursor += read;
        }
    }

    static void writeFully(FileChannel channel, ByteBuffer source, long position) throws IOException {
        long cursor = position;
        while (source.hasRemaining()) {
            int written = channel.write(source, cursor);
            if (written <= 0) {
                throw new IOException("failed to make progress writing LFC_GRID_V1 artifact");
            }
            cursor += written;
        }
    }

    static byte[] checksumBytes(String checksum) {
        return HexFormat.of().parseHex(checksum);
    }

    private static byte valueTypeCode(FieldArtifactDescriptorV2.FieldValueType value) {
        return switch (value) {
            case U8 -> 1;
            case U16 -> 2;
            case I32 -> 3;
        };
    }

    private static FieldArtifactDescriptorV2.FieldValueType valueType(byte value) throws IOException {
        return switch (value) {
            case 1 -> FieldArtifactDescriptorV2.FieldValueType.U8;
            case 2 -> FieldArtifactDescriptorV2.FieldValueType.U16;
            case 3 -> FieldArtifactDescriptorV2.FieldValueType.I32;
            default -> throw new IOException("unknown LFC_GRID_V1 value type: " + value);
        };
    }

    private static byte semanticCode(FieldArtifactDescriptorV2.FieldSemantic value) {
        return switch (value) {
            case LAND_WATER_MASK -> 1;
            case HEIGHT_GUIDE -> 2;
            case ZONE_LABEL_MAP -> 3;
            case DESIRED_LAND_WATER -> 4;
            case ACTUAL_LAND_WATER -> 5;
            case RESIDUAL_LAND_WATER -> 6;
            case DESIRED_HEIGHT -> 7;
            case ACTUAL_HEIGHT -> 8;
            case RESIDUAL_HEIGHT -> 9;
            case HYDROLOGY_FLOW_DIRECTION -> 10;
            case HYDROLOGY_FLOW_ACCUMULATION -> 11;
            case GEOLOGY_PROVINCE_ID -> 12;
            case GEOLOGY_FORMATION_ID -> 13;
            case GEOLOGY_HARDNESS -> 14;
            case GEOLOGY_PERMEABILITY -> 15;
        };
    }

    private static FieldArtifactDescriptorV2.FieldSemantic semantic(byte value) throws IOException {
        return switch (value) {
            case 1 -> FieldArtifactDescriptorV2.FieldSemantic.LAND_WATER_MASK;
            case 2 -> FieldArtifactDescriptorV2.FieldSemantic.HEIGHT_GUIDE;
            case 3 -> FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP;
            case 4 -> FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER;
            case 5 -> FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER;
            case 6 -> FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER;
            case 7 -> FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT;
            case 8 -> FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT;
            case 9 -> FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT;
            case 10 -> FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_DIRECTION;
            case 11 -> FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_ACCUMULATION;
            case 12 -> FieldArtifactDescriptorV2.FieldSemantic.GEOLOGY_PROVINCE_ID;
            case 13 -> FieldArtifactDescriptorV2.FieldSemantic.GEOLOGY_FORMATION_ID;
            case 14 -> FieldArtifactDescriptorV2.FieldSemantic.GEOLOGY_HARDNESS;
            case 15 -> FieldArtifactDescriptorV2.FieldSemantic.GEOLOGY_PERMEABILITY;
            default -> throw new IOException("unknown LFC_GRID_V1 semantic: " + value);
        };
    }

    private static byte coordinateCode(FieldArtifactDescriptorV2.CoordinateSpace value) {
        return switch (value) {
            case RELEASE_LOCAL_XZ -> 1;
        };
    }

    private static FieldArtifactDescriptorV2.CoordinateSpace coordinate(byte value) throws IOException {
        if (value == 1) {
            return FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ;
        }
        throw new IOException("unknown LFC_GRID_V1 coordinate space: " + value);
    }

    private static byte samplingCode(FieldArtifactDescriptorV2.Sampling value) {
        return switch (value) {
            case NEAREST -> 1;
            case BILINEAR_FIXED -> 2;
        };
    }

    private static FieldArtifactDescriptorV2.Sampling sampling(byte value) throws IOException {
        return switch (value) {
            case 1 -> FieldArtifactDescriptorV2.Sampling.NEAREST;
            case 2 -> FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED;
            default -> throw new IOException("unknown LFC_GRID_V1 sampling: " + value);
        };
    }

    private static byte sourceKindCode(FieldArtifactDescriptorV2.SourceKind value) {
        return switch (value) {
            case MANUAL -> 1;
            case CONSTRAINT_MAP -> 2;
            case DERIVED -> 3;
        };
    }

    private static FieldArtifactDescriptorV2.SourceKind sourceKind(byte value) throws IOException {
        return switch (value) {
            case 1 -> FieldArtifactDescriptorV2.SourceKind.MANUAL;
            case 2 -> FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP;
            case 3 -> FieldArtifactDescriptorV2.SourceKind.DERIVED;
            default -> throw new IOException("unknown LFC_GRID_V1 source kind: " + value);
        };
    }

    private static void requireZero(byte value, String field) throws IOException {
        if (value != 0) {
            throw new IOException(field + " must be zero");
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String strictUtf8(byte[] value) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("invalid UTF-8 in LFC_GRID_V1 header", exception);
        }
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = utf8(value);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateLong(MessageDigest digest, long value) {
        updateInt(digest, (int) (value >>> 32));
        updateInt(digest, (int) value);
    }

    record Header(
            FieldArtifactDescriptorV2.Definition definition,
            FieldArtifactDescriptorV2.Provenance provenance,
            String semanticChecksum,
            int headerBytes,
            long payloadBytes
    ) {
    }
}
