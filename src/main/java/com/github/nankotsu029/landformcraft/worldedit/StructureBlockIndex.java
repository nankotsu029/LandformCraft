package com.github.nankotsu029.landformcraft.worldedit;

import com.github.nankotsu029.landformcraft.generator.StructurePlanner;
import com.github.nankotsu029.landformcraft.model.StructurePlan;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.TilePlan;
import com.github.nankotsu029.landformcraft.structure.BuiltInStructureAssetCatalog;
import com.github.nankotsu029.landformcraft.structure.StructureAsset;

import java.util.HashMap;
import java.util.Map;

/** Small per-tile override index; terrain columns remain streamed and are never expanded to a 3D array. */
final class StructureBlockIndex {
    private final Map<Long, Integer> blocks;

    StructureBlockIndex(TerrainPlan plan, TilePlan tile) {
        BuiltInStructureAssetCatalog catalog = new BuiltInStructureAssetCatalog();
        Map<Position, String> structureBlocks = new HashMap<>();
        for (StructurePlan placement : plan.structures()) {
            StructureAsset asset = catalog.requireById(placement.assetId());
            validatePlacementAsset(placement, asset);
            for (var block : asset.blocks()) {
                var rotated = StructurePlanner.rotate(asset, placement.rotation(), block.x(), block.z());
                int worldX = placement.anchorX() + rotated.x();
                int worldZ = placement.anchorZ() + rotated.z();
                int baseY = placement.terrainFollowing()
                        ? plan.heightMap().get(worldX, worldZ) + 1
                        : placement.anchorY();
                int worldY = baseY + block.y();
                if (worldY < plan.blueprint().bounds().minY() || worldY > plan.blueprint().bounds().maxY()) {
                    throw new IllegalStateException("validated structure block is outside vertical bounds");
                }
                if (structureBlocks.putIfAbsent(new Position(worldX, worldY, worldZ), block.blockState()) != null) {
                    throw new IllegalStateException("structure block collision escaped validation");
                }
            }
        }
        Map<Long, Integer> values = new HashMap<>();
        BlockColumnMaterializer materializer = new BlockColumnMaterializer();
        for (var entry : structureBlocks.entrySet()) {
            Position position = entry.getKey();
            if (position.x() < tile.originX() || position.x() >= tile.originX() + tile.width()
                    || position.z() < tile.originZ() || position.z() >= tile.originZ() + tile.length()) {
                continue;
            }
            int paletteId = MinecraftBlockPalette.id(entry.getValue());
            if (paletteId == MinecraftBlockPalette.OAK_FENCE) {
                paletteId = MinecraftBlockPalette.oakFence(
                        connects(plan, materializer, structureBlocks, position.x() + 1, position.y(), position.z()),
                        connects(plan, materializer, structureBlocks, position.x(), position.y(), position.z() - 1),
                        connects(plan, materializer, structureBlocks, position.x(), position.y(), position.z() + 1),
                        connects(plan, materializer, structureBlocks, position.x() - 1, position.y(), position.z())
                );
            }
            values.put(key(position.x(), position.y(), position.z()), paletteId);
        }
        blocks = Map.copyOf(values);
    }

    int paletteIdAt(int x, int y, int z, int terrainPaletteId) {
        return blocks.getOrDefault(key(x, y, z), terrainPaletteId);
    }

    private static boolean connects(
            TerrainPlan plan,
            BlockColumnMaterializer materializer,
            Map<Position, String> structureBlocks,
            int x,
            int y,
            int z
    ) {
        String structure = structureBlocks.get(new Position(x, y, z));
        if (structure != null) {
            return !structure.equals("minecraft:air") && !structure.equals("minecraft:water");
        }
        var bounds = plan.blueprint().bounds();
        if (x < 0 || x >= bounds.width() || z < 0 || z >= bounds.length()
                || y < bounds.minY() || y > bounds.maxY()) {
            return false;
        }
        int terrain = materializer.paletteIdAt(plan, x, y, z);
        return terrain != MinecraftBlockPalette.AIR && terrain != MinecraftBlockPalette.WATER;
    }

    private static void validatePlacementAsset(StructurePlan placement, StructureAsset asset) {
        if (placement.type() != asset.type()
                || !placement.assetChecksum().equals(asset.semanticChecksum())
                || !placement.minecraftVersion().equals(asset.minecraftVersion())
                || placement.terrainFollowing() != asset.terrainFollowing()
                || placement.sizeX() != asset.rotatedWidth(placement.rotation())
                || placement.sizeY() != asset.height()
                || placement.sizeZ() != asset.rotatedLength(placement.rotation())) {
            throw new IllegalStateException("structure placement does not match built-in asset: " + placement.assetId());
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (y + 2_048) << 40) | ((long) z << 20) | x;
    }

    private record Position(int x, int y, int z) {
    }
}
