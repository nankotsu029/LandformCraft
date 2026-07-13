package com.github.nankotsu029.landformcraft.structure;

import com.github.nankotsu029.landformcraft.model.StructureType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned Phase 6 catalog of small deterministic vanilla structure templates. */
public final class BuiltInStructureAssetCatalog {
    public static final String MINECRAFT_VERSION = "1.21.11";

    private static final List<StructureAsset> ASSETS = createAssets();
    private static final Map<String, StructureAsset> BY_ID = byId();
    private static final Map<StructureType, StructureAsset> BY_TYPE = byType();

    public List<StructureAsset> assets() {
        return ASSETS;
    }

    public StructureAsset requireById(String assetId) {
        StructureAsset asset = BY_ID.get(assetId);
        if (asset == null) {
            throw new IllegalArgumentException("unknown structure asset: " + assetId);
        }
        return asset;
    }

    public StructureAsset requireByType(StructureType type) {
        StructureAsset asset = BY_TYPE.get(type);
        if (asset == null) {
            throw new IllegalArgumentException("no built-in structure asset for type: " + type);
        }
        return asset;
    }

    private static List<StructureAsset> createAssets() {
        return List.of(
                pier(), bridge(), hut(), ruin(), path(), retainingWall(), stoneSteps(), fence()
        );
    }

    private static StructureAsset pier() {
        Builder blocks = new Builder();
        blocks.fill(0, 3, 0, 4, 3, 8, "minecraft:oak_planks");
        for (int z : List.of(2, 5, 8)) {
            blocks.fill(0, 0, z, 0, 2, z, "minecraft:oak_log");
            blocks.fill(4, 0, z, 4, 2, z, "minecraft:oak_log");
        }
        blocks.fill(0, 4, 1, 0, 4, 8, "minecraft:oak_fence");
        blocks.fill(4, 4, 1, 4, 4, 8, "minecraft:oak_fence");
        return asset("small-pier-v1", StructureType.SMALL_PIER, 5, 5, 9,
                false, StructurePlacementKind.WATER_EDGE, 7, blocks);
    }

    private static StructureAsset bridge() {
        Builder blocks = new Builder();
        blocks.fill(0, 1, 0, 4, 1, 8, "minecraft:oak_planks");
        blocks.fill(0, 2, 0, 0, 2, 8, "minecraft:oak_fence");
        blocks.fill(4, 2, 0, 4, 2, 8, "minecraft:oak_fence");
        for (int z : List.of(0, 4, 8)) {
            blocks.fill(0, 0, z, 0, 0, z, "minecraft:oak_log");
            blocks.fill(4, 0, z, 4, 0, z, "minecraft:oak_log");
        }
        return asset("small-bridge-v1", StructureType.SMALL_BRIDGE, 5, 3, 9,
                false, StructurePlacementKind.WATER_CROSSING, 3, blocks);
    }

    private static StructureAsset hut() {
        Builder blocks = new Builder();
        blocks.fill(0, 0, 0, 6, 0, 6, "minecraft:oak_planks");
        for (int x : List.of(0, 6)) {
            for (int z : List.of(0, 6)) {
                blocks.fill(x, 1, z, x, 4, z, "minecraft:oak_log");
            }
        }
        blocks.wall(0, 1, 0, 6, 3, 6, "minecraft:oak_planks", 3, 0);
        blocks.fill(0, 4, 0, 6, 4, 6, "minecraft:oak_planks");
        blocks.fill(1, 5, 1, 5, 5, 5, "minecraft:oak_planks");
        return asset("fishing-hut-v1", StructureType.FISHING_HUT, 7, 6, 7,
                false, StructurePlacementKind.DRY_FLAT, 2, blocks);
    }

    private static StructureAsset ruin() {
        Builder blocks = new Builder();
        blocks.fill(0, 0, 0, 6, 0, 6, "minecraft:cobblestone");
        blocks.fill(0, 1, 0, 0, 4, 6, "minecraft:stone_bricks");
        blocks.fill(6, 1, 0, 6, 3, 4, "minecraft:stone_bricks");
        blocks.fill(1, 1, 6, 4, 2, 6, "minecraft:stone_bricks");
        blocks.fill(2, 1, 0, 5, 1, 0, "minecraft:stone_bricks");
        return asset("stone-ruin-v1", StructureType.STONE_RUIN, 7, 5, 7,
                false, StructurePlacementKind.DRY_FLAT, 3, blocks);
    }

    private static StructureAsset path() {
        Builder blocks = new Builder();
        blocks.fill(0, 0, 0, 2, 0, 8, "minecraft:gravel");
        return asset("path-v1", StructureType.PATH, 3, 1, 9,
                true, StructurePlacementKind.DRY_FOLLOWING, 4, blocks);
    }

    private static StructureAsset retainingWall() {
        Builder blocks = new Builder();
        blocks.fill(0, 0, 0, 0, 3, 8, "minecraft:stone_bricks");
        return asset("retaining-wall-v1", StructureType.RETAINING_WALL, 1, 4, 9,
                true, StructurePlacementKind.DRY_FOLLOWING, 5, blocks);
    }

    private static StructureAsset stoneSteps() {
        Builder blocks = new Builder();
        blocks.fill(0, 0, 0, 2, 0, 6, "minecraft:stone_bricks");
        return asset("stone-steps-v1", StructureType.STONE_STEPS, 3, 1, 7,
                true, StructurePlacementKind.DRY_FOLLOWING, 6, blocks);
    }

    private static StructureAsset fence() {
        Builder blocks = new Builder();
        blocks.fill(0, 0, 0, 0, 0, 8, "minecraft:oak_fence");
        return asset("fence-v1", StructureType.FENCE, 1, 1, 9,
                true, StructurePlacementKind.DRY_FOLLOWING, 4, blocks);
    }

    private static StructureAsset asset(
            String id,
            StructureType type,
            int width,
            int height,
            int length,
            boolean following,
            StructurePlacementKind kind,
            int slope,
            Builder blocks
    ) {
        return new StructureAsset(id, type, MINECRAFT_VERSION, width, height, length,
                following, kind, slope, blocks.build());
    }

    private static Map<String, StructureAsset> byId() {
        Map<String, StructureAsset> values = new LinkedHashMap<>();
        for (StructureAsset asset : ASSETS) {
            if (values.put(asset.assetId(), asset) != null) {
                throw new IllegalStateException("duplicate built-in asset id");
            }
        }
        return Map.copyOf(values);
    }

    private static Map<StructureType, StructureAsset> byType() {
        Map<StructureType, StructureAsset> values = new EnumMap<>(StructureType.class);
        for (StructureAsset asset : ASSETS) {
            if (values.put(asset.type(), asset) != null) {
                throw new IllegalStateException("duplicate built-in asset type");
            }
        }
        return Map.copyOf(values);
    }

    private static final class Builder {
        private final Map<String, StructureBlock> blocks = new LinkedHashMap<>();

        void fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String state) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        put(x, y, z, state);
                    }
                }
            }
        }

        void wall(
                int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                String state, int doorX, int doorZ
        ) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                        boolean door = x == doorX && z == doorZ && y <= minY + 1;
                        if (edge && !door) {
                            put(x, y, z, state);
                        }
                    }
                }
            }
        }

        private void put(int x, int y, int z, String state) {
            blocks.put(x + ":" + y + ":" + z, new StructureBlock(x, y, z, state));
        }

        List<StructureBlock> build() {
            return List.copyOf(new ArrayList<>(blocks.values()));
        }
    }
}
