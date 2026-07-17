package com.github.nankotsu029.landformcraft.format.v2.tile;

import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** Bounded strict read-back for the V2 Sponge v3 subset before WorldEdit receives the artifact. */
public final class SpongeV3TileInspectorV2 {
    private static final int MAXIMUM_DECOMPRESSED_BYTES = 48 * 1024 * 1024;
    private static final int MAXIMUM_TAGS = 20_000;
    private static final int MAXIMUM_DEPTH = 16;

    public Inspection inspect(Path path, OfflineTilePlanV2 expectedPlan) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(expectedPlan, "expectedPlan");
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("offline tile schematic must be a regular non-symbolic file");
        }
        long compressedBytes = Files.size(path);
        if (compressedBytes < 2 || compressedBytes > OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES) {
            throw new IOException("offline tile compressed byte length is outside its budget");
        }
        byte[] compressed;
        try (InputStream input = Files.newInputStream(path)) {
            compressed = input.readNBytes(Math.toIntExact(OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES + 1L));
        }
        if (compressed.length != compressedBytes) {
            throw new IOException("offline tile changed while its bounded bytes were read");
        }
        return inspect(compressed, expectedPlan);
    }

    public Inspection inspect(byte[] compressed, OfflineTilePlanV2 expectedPlan) throws IOException {
        Objects.requireNonNull(compressed, "compressed");
        Objects.requireNonNull(expectedPlan, "expectedPlan");
        if (compressed.length < 2 || compressed.length > OfflineTileArtifactV2.MAXIMUM_ARTIFACT_BYTES) {
            throw new IOException("offline tile compressed byte length is outside its budget");
        }
        byte[] decompressed;
        try (InputStream file = new ByteArrayInputStream(compressed);
             GZIPInputStream gzip = new GZIPInputStream(file)) {
            decompressed = gzip.readNBytes(MAXIMUM_DECOMPRESSED_BYTES + 1);
        }
        if (decompressed.length > MAXIMUM_DECOMPRESSED_BYTES) {
            throw new IOException("offline tile expands beyond its decode budget");
        }
        NbtReader reader = new NbtReader(decompressed);
        Map<String, Object> root = reader.readRoot();
        Map<String, Object> schematic = compound(root.get("Schematic"), "Schematic");
        int version = integer(schematic.get("Version"), "Version");
        int dataVersion = integer(schematic.get("DataVersion"), "DataVersion");
        int width = unsignedShort(schematic.get("Width"), "Width");
        int height = unsignedShort(schematic.get("Height"), "Height");
        int length = unsignedShort(schematic.get("Length"), "Length");
        if (version != OfflineTileArtifactV2.SCHEMATIC_VERSION
                || dataVersion != OfflineTileArtifactV2.DATA_VERSION
                || width != expectedPlan.width() || height != expectedPlan.height()
                || length != expectedPlan.length()) {
            throw new IOException("offline tile version or dimensions differ from its plan");
        }
        int[] offset = intArray(schematic.get("Offset"), "Offset");
        if (offset.length != 3 || offset[0] != 0 || offset[1] != 0 || offset[2] != 0) {
            throw new IOException("offline tile Offset must be exactly [0,0,0]");
        }
        Map<String, Object> blocks = compound(schematic.get("Blocks"), "Blocks");
        Map<String, Object> paletteValues = compound(blocks.get("Palette"), "Palette");
        if (paletteValues.isEmpty() || paletteValues.size() > OfflineTileArtifactV2.MAXIMUM_PALETTE_SIZE) {
            throw new IOException("offline tile palette is outside 1..16384");
        }
        String[] statesById = new String[paletteValues.size()];
        for (Map.Entry<String, Object> entry : paletteValues.entrySet()) {
            int id = integer(entry.getValue(), "palette index");
            String state;
            try {
                state = CoastalBlockStateCatalogV2.requireKnown(entry.getKey());
            } catch (IllegalArgumentException exception) {
                throw new IOException("offline tile contains an unknown or non-canonical block state", exception);
            }
            if (id < 0 || id >= statesById.length || statesById[id] != null) {
                throw new IOException("offline tile palette IDs must be unique and contiguous");
            }
            statesById[id] = state;
        }
        for (String state : statesById) {
            if (state == null) throw new IOException("offline tile palette IDs must be contiguous");
        }
        byte[] data = byteArray(blocks.get("Data"), "Data");
        if (data.length > OfflineTileSchematicWriterV2.MAXIMUM_ENCODED_BLOCK_BYTES) {
            throw new IOException("offline tile block data exceeds its encoded-byte budget");
        }
        MessageDigest digest = CanonicalBlockStreamV2.newDigest(expectedPlan);
        int entries = validateAndHashVarInts(data, expectedPlan.blockCount(), statesById, digest);
        requireEmptyList(blocks.get("BlockEntities"), "BlockEntities");
        if (schematic.containsKey("Entities")) requireEmptyList(schematic.get("Entities"), "Entities");
        if (schematic.containsKey("Biomes")) throw new IOException("offline tile must not contain biome data");
        return new Inspection(
                width, height, length, statesById.length, entries, compressed.length,
                CanonicalBlockStreamV2.finish(digest));
    }

    private static int validateAndHashVarInts(
            byte[] data,
            int expectedEntries,
            String[] statesById,
            MessageDigest digest
    ) throws IOException {
        int entries = 0;
        int cursor = 0;
        while (cursor < data.length) {
            int value = 0;
            int shift = 0;
            int current;
            do {
                if (cursor >= data.length || shift >= 35) {
                    throw new IOException("offline tile contains a truncated or oversized VarInt");
                }
                current = Byte.toUnsignedInt(data[cursor++]);
                value |= (current & 0x7f) << shift;
                shift += 7;
            } while ((current & 0x80) != 0);
            if (value < 0 || value >= statesById.length || ++entries > expectedEntries) {
                throw new IOException("offline tile references an invalid palette ID");
            }
            CanonicalBlockStreamV2.updateState(digest, statesById[value]);
        }
        if (entries != expectedEntries) {
            throw new IOException("offline tile block count differs from its dimensions");
        }
        return entries;
    }

    private static void requireEmptyList(Object value, String name) throws IOException {
        if (!(value instanceof List<?> list) || !list.isEmpty()) {
            throw new IOException("offline tile " + name + " must be an empty list");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compound(Object value, String name) throws IOException {
        if (!(value instanceof Map<?, ?>)) throw new IOException(name + " must be an NBT compound");
        return (Map<String, Object>) value;
    }

    private static int integer(Object value, String name) throws IOException {
        if (!(value instanceof Integer integer)) throw new IOException(name + " must be an NBT int");
        return integer;
    }

    private static int unsignedShort(Object value, String name) throws IOException {
        if (!(value instanceof Short shortValue)) throw new IOException(name + " must be an NBT short");
        return Short.toUnsignedInt(shortValue);
    }

    private static int[] intArray(Object value, String name) throws IOException {
        if (!(value instanceof int[] values)) throw new IOException(name + " must be an NBT int array");
        return values;
    }

    private static byte[] byteArray(Object value, String name) throws IOException {
        if (!(value instanceof byte[] values)) throw new IOException(name + " must be an NBT byte array");
        return values;
    }

    public record Inspection(
            int width,
            int height,
            int length,
            int paletteSize,
            int blockCount,
            long compressedBytes,
            String semanticChecksum
    ) {
    }

    private static final class NbtReader {
        private final DataInputStream input;
        private int tags;

        private NbtReader(byte[] data) {
            input = new DataInputStream(new ByteArrayInputStream(data));
        }

        private Map<String, Object> readRoot() throws IOException {
            if (input.readUnsignedByte() != 10) throw new IOException("offline tile root must be a compound");
            readString();
            Map<String, Object> root = readCompound(0);
            if (input.read() != -1) throw new IOException("offline tile has trailing NBT bytes");
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
                    throw new IOException("truncated offline tile NBT compound", exception);
                }
                if (type == 0) return result;
                countTag();
                String name = readString();
                if (result.putIfAbsent(name, readPayload(type, depth + 1)) != null) {
                    throw new IOException("duplicate offline tile NBT tag: " + name);
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
                default -> throw new IOException("unsupported offline tile NBT tag type: " + type);
            };
        }

        private List<Object> readList(int depth) throws IOException {
            int elementType = input.readUnsignedByte();
            int length = readLength("list", MAXIMUM_TAGS);
            if (elementType == 0 && length != 0) throw new IOException("non-empty end-tag NBT list");
            ArrayList<Object> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                countTag();
                values.add(readPayload(elementType, depth + 1));
            }
            return List.copyOf(values);
        }

        private byte[] readByteArray() throws IOException {
            int length = readLength("byte array", OfflineTileSchematicWriterV2.MAXIMUM_ENCODED_BLOCK_BYTES);
            byte[] values = input.readNBytes(length);
            if (values.length != length) throw new IOException("truncated offline tile byte array");
            return values;
        }

        private int[] readIntArray() throws IOException {
            int length = readLength("int array", 16);
            int[] values = new int[length];
            for (int index = 0; index < length; index++) values[index] = input.readInt();
            return values;
        }

        private long[] readLongArray() throws IOException {
            int length = readLength("long array", 16);
            long[] values = new long[length];
            for (int index = 0; index < length; index++) values[index] = input.readLong();
            return values;
        }

        private int readLength(String kind, int maximum) throws IOException {
            int length = input.readInt();
            if (length < 0 || length > maximum) {
                throw new IOException("offline tile NBT " + kind + " length exceeds its budget");
            }
            return length;
        }

        private String readString() throws IOException {
            int length = input.readUnsignedShort();
            if (length > 512) throw new IOException("offline tile NBT string exceeds 512 bytes");
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) throw new IOException("truncated offline tile NBT string");
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private void countTag() throws IOException {
            if (++tags > MAXIMUM_TAGS) throw new IOException("offline tile has too many NBT tags");
        }

        private static void checkDepth(int depth) throws IOException {
            if (depth > MAXIMUM_DEPTH) throw new IOException("offline tile NBT is too deeply nested");
        }
    }
}
