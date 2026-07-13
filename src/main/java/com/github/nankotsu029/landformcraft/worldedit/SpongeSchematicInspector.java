package com.github.nankotsu029.landformcraft.worldedit;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Bounded, dependency-free reader used to verify untrusted Sponge v3 schematic files. */
public final class SpongeSchematicInspector {
    private static final long MAX_COMPRESSED_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_DECOMPRESSED_BYTES = 48 * 1024 * 1024;
    private static final int MAX_BYTE_ARRAY_LENGTH = 34_000_000;
    private static final int MAX_TAGS = 20_000;
    private static final int MAX_DEPTH = 16;

    public SchematicInfo inspect(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new IOException("schematic must be a regular non-symbolic file: " + path);
        }
        long compressedSize = Files.size(path);
        if (compressedSize < 2 || compressedSize > MAX_COMPRESSED_BYTES) {
            throw new IOException("schematic compressed size is outside the allowed range");
        }
        byte[] decompressed;
        try (InputStream file = Files.newInputStream(path); GZIPInputStream gzip = new GZIPInputStream(file)) {
            decompressed = gzip.readNBytes(MAX_DECOMPRESSED_BYTES + 1);
        }
        if (decompressed.length > MAX_DECOMPRESSED_BYTES) {
            throw new IOException("schematic expands beyond the verification limit");
        }
        NbtReader reader = new NbtReader(decompressed);
        Map<String, Object> root = reader.readRoot();
        Map<String, Object> schematic = compound(root.get("Schematic"), "Schematic");
        int version = integer(schematic.get("Version"), "Version");
        int dataVersion = integer(schematic.get("DataVersion"), "DataVersion");
        int width = unsignedShort(schematic.get("Width"), "Width");
        int height = unsignedShort(schematic.get("Height"), "Height");
        int length = unsignedShort(schematic.get("Length"), "Length");
        if (version != SpongeSchematicWriter.SCHEMATIC_VERSION
                || dataVersion != SpongeSchematicWriter.MINECRAFT_1_21_11_DATA_VERSION
                || width < 1 || width > 256 || length < 1 || length > 256
                || height < 1 || height > 512) {
            throw new IOException("unsupported schematic version or dimensions");
        }
        int[] offset = intArray(schematic.get("Offset"), "Offset");
        if (offset.length != 3) {
            throw new IOException("Offset must contain exactly three integers");
        }
        Map<String, Object> blocks = compound(schematic.get("Blocks"), "Blocks");
        Map<String, Object> paletteValues = compound(blocks.get("Palette"), "Palette");
        if (paletteValues.isEmpty() || paletteValues.size() > 4_096) {
            throw new IOException("schematic palette size is outside the allowed range");
        }
        Map<String, Integer> palette = new LinkedHashMap<>();
        boolean[] indexes = new boolean[paletteValues.size()];
        for (Map.Entry<String, Object> entry : paletteValues.entrySet()) {
            int index = integer(entry.getValue(), "palette index");
            if (entry.getKey().length() > 512 || index < 0 || index >= indexes.length || indexes[index]) {
                throw new IOException("schematic palette indexes must be unique and contiguous");
            }
            indexes[index] = true;
            palette.put(entry.getKey(), index);
        }
        byte[] data = byteArray(blocks.get("Data"), "Data");
        int expectedEntries = Math.multiplyExact(Math.multiplyExact(width, length), height);
        int entries = validateVarInts(data, expectedEntries, palette.size());
        Object blockEntities = blocks.get("BlockEntities");
        if (blockEntities != null && (!(blockEntities instanceof List<?> values) || !values.isEmpty())) {
            throw new IOException("custom schematics must not contain block entities");
        }
        Object entities = schematic.get("Entities");
        if (entities != null && (!(entities instanceof List<?> values) || !values.isEmpty())) {
            throw new IOException("custom schematics must not contain entities");
        }
        if (schematic.containsKey("Biomes")) {
            throw new IOException("custom schematics must not contain biome data");
        }
        return new SchematicInfo(
                version, dataVersion, width, height, length,
                offset[0], offset[1], offset[2], palette, entries
        );
    }

    private static int validateVarInts(byte[] data, int expected, int paletteSize) throws IOException {
        int entries = 0;
        for (int cursor = 0; cursor < data.length;) {
            int value = 0;
            int shift = 0;
            byte current;
            do {
                if (cursor >= data.length || shift >= 35) {
                    throw new IOException("invalid schematic block VarInt data");
                }
                current = data[cursor++];
                value |= (current & 0x7F) << shift;
                shift += 7;
            } while ((current & 0x80) != 0);
            if (value < 0 || value >= paletteSize || ++entries > expected) {
                throw new IOException("schematic block data references an invalid palette index");
            }
        }
        if (entries != expected) {
            throw new IOException("schematic block entry count does not match its dimensions");
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compound(Object value, String name) throws IOException {
        if (!(value instanceof Map<?, ?>)) {
            throw new IOException(name + " must be an NBT compound");
        }
        return (Map<String, Object>) value;
    }

    private static int integer(Object value, String name) throws IOException {
        if (!(value instanceof Integer integer)) {
            throw new IOException(name + " must be an NBT int");
        }
        return integer;
    }

    private static int unsignedShort(Object value, String name) throws IOException {
        if (!(value instanceof Short shortValue)) {
            throw new IOException(name + " must be an NBT short");
        }
        return Short.toUnsignedInt(shortValue);
    }

    private static int[] intArray(Object value, String name) throws IOException {
        if (!(value instanceof int[] values)) {
            throw new IOException(name + " must be an NBT int array");
        }
        return values;
    }

    private static byte[] byteArray(Object value, String name) throws IOException {
        if (!(value instanceof byte[] values)) {
            throw new IOException(name + " must be an NBT byte array");
        }
        return values;
    }

    private static final class NbtReader {
        private final DataInputStream input;
        private int tags;

        private NbtReader(byte[] data) {
            this.input = new DataInputStream(new ByteArrayInputStream(data));
        }

        private Map<String, Object> readRoot() throws IOException {
            int type = input.readUnsignedByte();
            if (type != 10) {
                throw new IOException("schematic NBT root must be a compound");
            }
            readString();
            Map<String, Object> root = readCompound(0);
            if (input.read() != -1) {
                throw new IOException("trailing bytes after schematic NBT root");
            }
            return root;
        }

        private Map<String, Object> readCompound(int depth) throws IOException {
            checkDepth(depth);
            Map<String, Object> result = new LinkedHashMap<>();
            while (true) {
                int type;
                try {
                    type = input.readUnsignedByte();
                } catch (EOFException exception) {
                    throw new IOException("truncated NBT compound", exception);
                }
                if (type == 0) {
                    return result;
                }
                countTag();
                String name = readString();
                if (result.putIfAbsent(name, readPayload(type, depth + 1)) != null) {
                    throw new IOException("duplicate NBT tag: " + name);
                }
            }
        }

        private Object readPayload(int type, int depth) throws IOException {
            checkDepth(depth);
            return switch (type) {
                case 1 -> input.readByte();
                case 2 -> input.readShort();
                case 3 -> input.readInt();
                case 4 -> input.readLong();
                case 5 -> input.readFloat();
                case 6 -> input.readDouble();
                case 7 -> readByteArray();
                case 8 -> readString();
                case 9 -> readList(depth);
                case 10 -> readCompound(depth);
                case 11 -> readIntArray();
                case 12 -> readLongArray();
                default -> throw new IOException("unsupported NBT tag type: " + type);
            };
        }

        private List<Object> readList(int depth) throws IOException {
            int elementType = input.readUnsignedByte();
            int length = readLength("list", MAX_TAGS);
            if (elementType == 0 && length != 0) {
                throw new IOException("non-empty NBT list cannot have end-tag elements");
            }
            java.util.ArrayList<Object> values = new java.util.ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                countTag();
                values.add(readPayload(elementType, depth + 1));
            }
            return List.copyOf(values);
        }

        private byte[] readByteArray() throws IOException {
            return input.readNBytes(readLength("byte array", MAX_BYTE_ARRAY_LENGTH));
        }

        private int[] readIntArray() throws IOException {
            int length = readLength("int array", 1_000_000);
            int[] values = new int[length];
            for (int index = 0; index < length; index++) {
                values[index] = input.readInt();
            }
            return values;
        }

        private long[] readLongArray() throws IOException {
            int length = readLength("long array", 500_000);
            long[] values = new long[length];
            for (int index = 0; index < length; index++) {
                values[index] = input.readLong();
            }
            return values;
        }

        private int readLength(String kind, int maximum) throws IOException {
            int length = input.readInt();
            if (length < 0 || length > maximum) {
                throw new IOException("NBT " + kind + " length is outside the allowed range");
            }
            return length;
        }

        private String readString() throws IOException {
            int length = input.readUnsignedShort();
            if (length > 32_768) {
                throw new IOException("NBT string is too long");
            }
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) {
                throw new IOException("truncated NBT string");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private void countTag() throws IOException {
            if (++tags > MAX_TAGS) {
                throw new IOException("schematic contains too many NBT tags");
            }
        }

        private static void checkDepth(int depth) throws IOException {
            if (depth > MAX_DEPTH) {
                throw new IOException("schematic NBT nesting is too deep");
            }
        }
    }
}
