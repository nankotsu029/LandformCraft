package com.github.nankotsu029.landformcraft.worldedit;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.TilePlan;
import com.github.nankotsu029.landformcraft.structure.StructureAsset;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/** Streaming Sponge Schematic v3 writer; it never expands a tile into an in-memory block list. */
public final class SpongeSchematicWriter {
    public static final int SCHEMATIC_VERSION = 3;
    public static final int MINECRAFT_1_21_11_DATA_VERSION = 4671;

    private static final byte TAG_END = 0;
    private static final byte TAG_SHORT = 2;
    private static final byte TAG_INT = 3;
    private static final byte TAG_BYTE_ARRAY = 7;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARRAY = 11;

    private final BlockColumnMaterializer materializer = new BlockColumnMaterializer();

    public void write(Path target, TerrainPlan plan, TilePlan tile, CancellationToken token) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(token, "token");
        StructureBlockIndex structures = new StructureBlockIndex(plan, tile);
        writeSchematic(target, tile.width(), plan.blueprint().bounds().verticalSpan(), tile.length(), token,
                (localX, localY, localZ) -> {
                    int x = tile.originX() + localX;
                    int y = plan.blueprint().bounds().minY() + localY;
                    int z = tile.originZ() + localZ;
                    return structures.paletteIdAt(x, y, z, materializer.paletteIdAt(plan, x, y, z));
                });
    }

    public void writeAsset(Path target, StructureAsset asset, CancellationToken token) throws IOException {
        Objects.requireNonNull(asset, "asset");
        Map<Long, Integer> blocks = new HashMap<>();
        for (var block : asset.blocks()) {
            blocks.put(assetKey(block.x(), block.y(), block.z()), MinecraftBlockPalette.id(block.blockState()));
        }
        writeSchematic(target, asset.width(), asset.height(), asset.length(), token,
                (x, y, z) -> blocks.getOrDefault(assetKey(x, y, z), MinecraftBlockPalette.AIR));
    }

    private static void writeSchematic(
            Path target,
            int width,
            int height,
            int length,
            CancellationToken token,
            BlockSupplier blocks
    ) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Files.createDirectories(Objects.requireNonNull(absolute.getParent(), "schematic parent"));
        try (OutputStream file = Files.newOutputStream(absolute);
             GZIPOutputStream gzip = new GZIPOutputStream(new BufferedOutputStream(file));
             DataOutputStream output = new DataOutputStream(gzip)) {
            writeNamedTag(output, TAG_COMPOUND, "");
            writeNamedTag(output, TAG_COMPOUND, "Schematic");
            writeInt(output, "Version", SCHEMATIC_VERSION);
            writeInt(output, "DataVersion", MINECRAFT_1_21_11_DATA_VERSION);
            writeShort(output, "Width", width);
            writeShort(output, "Height", height);
            writeShort(output, "Length", length);
            writeNamedTag(output, TAG_INT_ARRAY, "Offset");
            output.writeInt(3);
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(0);
            writeNamedTag(output, TAG_COMPOUND, "Blocks");
            writePalette(output);
            writeBlockData(output, width, height, length, blocks, token);
            writeNamedTag(output, TAG_LIST, "BlockEntities");
            output.writeByte(TAG_COMPOUND);
            output.writeInt(0);
            output.writeByte(TAG_END); // Blocks
            output.writeByte(TAG_END); // Schematic
            output.writeByte(TAG_END); // root
        }
    }

    private static void writePalette(DataOutputStream output) throws IOException {
        writeNamedTag(output, TAG_COMPOUND, "Palette");
        for (Map.Entry<String, Integer> entry : MinecraftBlockPalette.states().entrySet()) {
            writeInt(output, entry.getKey(), entry.getValue());
        }
        output.writeByte(TAG_END);
    }

    private static void writeBlockData(
            DataOutputStream output,
            int width,
            int height,
            int length,
            BlockSupplier blocks,
            CancellationToken token
    ) throws IOException {
        writeNamedTag(output, TAG_BYTE_ARRAY, "Data");
        int entries = Math.multiplyExact(Math.multiplyExact(width, length), height);
        output.writeInt(entries); // every palette id is below 128 and therefore a one-byte VarInt
        for (int localY = 0; localY < height; localY++) {
            token.throwIfCancellationRequested();
            for (int localZ = 0; localZ < length; localZ++) {
                for (int localX = 0; localX < width; localX++) {
                    output.writeByte(blocks.paletteIdAt(localX, localY, localZ));
                }
            }
        }
    }

    private static long assetKey(int x, int y, int z) {
        return ((long) y << 32) | ((long) z << 16) | x;
    }

    @FunctionalInterface
    private interface BlockSupplier {
        int paletteIdAt(int x, int y, int z);
    }

    private static void writeInt(DataOutputStream output, String name, int value) throws IOException {
        writeNamedTag(output, TAG_INT, name);
        output.writeInt(value);
    }

    private static void writeShort(DataOutputStream output, String name, int value) throws IOException {
        writeNamedTag(output, TAG_SHORT, name);
        output.writeShort(value);
    }

    private static void writeNamedTag(DataOutputStream output, byte type, String name) throws IOException {
        output.writeByte(type);
        byte[] encoded = name.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 65_535) {
            throw new IOException("NBT name is too long");
        }
        output.writeShort(encoded.length);
        output.write(encoded);
    }
}
