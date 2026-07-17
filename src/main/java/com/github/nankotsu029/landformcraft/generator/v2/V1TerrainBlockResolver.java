package com.github.nankotsu029.landformcraft.generator.v2;

import java.util.Objects;

/** Resolves the v1 semantic adapter to the exact canonical states used by Release format 1. */
public final class V1TerrainBlockResolver implements TerrainBlockResolver {
    private final TerrainQuery terrain;

    public V1TerrainBlockResolver(TerrainQuery terrain) {
        this.terrain = Objects.requireNonNull(terrain, "terrain");
    }

    @Override
    public String blockStateAt(int x, int y, int z) {
        return switch (terrain.blockClassAt(x, y, z)) {
            case AIR -> "minecraft:air";
            case FLUID -> fluidState(terrain.fluidBodyAt(x, y, z));
            case SOLID -> solidState(terrain.semanticMaterialAt(x, y, z));
        };
    }

    private static String fluidState(TerrainQuery.FluidBody fluid) {
        if (fluid == TerrainQuery.FluidBody.WATER) {
            return "minecraft:water";
        }
        throw new IllegalStateException("v1 fluid block has no semantic fluid body");
    }

    private static String solidState(TerrainQuery.SemanticMaterial material) {
        return switch (material) {
            case BEDROCK -> "minecraft:bedrock";
            case STONE -> "minecraft:stone";
            case DIRT -> "minecraft:dirt";
            case GRASS -> "minecraft:grass_block";
            case SAND -> "minecraft:sand";
            case SANDSTONE -> "minecraft:sandstone";
            case GRAVEL -> "minecraft:gravel";
            case MUD -> "minecraft:mud";
            case SNOW -> "minecraft:snow_block";
            case NONE -> throw new IllegalStateException("v1 solid block has no semantic material");
        };
    }
}
