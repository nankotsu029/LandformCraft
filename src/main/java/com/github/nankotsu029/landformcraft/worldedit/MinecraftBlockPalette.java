package com.github.nankotsu029.landformcraft.worldedit;

import com.github.nankotsu029.landformcraft.model.SurfaceMaterial;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/** Deterministic vanilla block-state palette used by the column materializer. */
public final class MinecraftBlockPalette {
    public static final int AIR = 0;
    public static final int BEDROCK = 1;
    public static final int STONE = 2;
    public static final int DIRT = 3;
    public static final int GRASS_BLOCK = 4;
    public static final int SAND = 5;
    public static final int SANDSTONE = 6;
    public static final int GRAVEL = 7;
    public static final int MUD = 8;
    public static final int SNOW_BLOCK = 9;
    public static final int WATER = 10;
    public static final int OAK_PLANKS = 11;
    public static final int OAK_LOG = 12;
    public static final int COBBLESTONE = 13;
    public static final int STONE_BRICKS = 14;
    public static final int OAK_FENCE = 15;

    private static final Map<String, Integer> STATES = createStates();

    private MinecraftBlockPalette() {
    }

    public static Map<String, Integer> states() {
        return STATES;
    }

    public static int surface(SurfaceMaterial material) {
        return switch (material) {
            case GRASS -> GRASS_BLOCK;
            case SAND -> SAND;
            case STONE -> STONE;
            case GRAVEL -> GRAVEL;
            case MUD -> MUD;
            case SNOW -> SNOW_BLOCK;
        };
    }

    public static int subsoil(SurfaceMaterial material) {
        return switch (material) {
            case GRASS, MUD -> DIRT;
            case SAND -> SANDSTONE;
            case STONE, GRAVEL, SNOW -> STONE;
        };
    }

    public static int id(String blockState) {
        Integer value = STATES.get(blockState);
        if (value == null) {
            throw new IllegalArgumentException("block state is not in the Phase 6 palette: " + blockState);
        }
        return value;
    }

    public static int oakFence(boolean east, boolean north, boolean south, boolean west) {
        int mask = (east ? 1 : 0) | (north ? 2 : 0) | (south ? 4 : 0) | (west ? 8 : 0);
        return OAK_FENCE + mask;
    }

    private static Map<String, Integer> createStates() {
        Map<String, Integer> states = new LinkedHashMap<>();
        states.put("minecraft:air", AIR);
        states.put("minecraft:bedrock", BEDROCK);
        states.put("minecraft:stone", STONE);
        states.put("minecraft:dirt", DIRT);
        states.put("minecraft:grass_block", GRASS_BLOCK);
        states.put("minecraft:sand", SAND);
        states.put("minecraft:sandstone", SANDSTONE);
        states.put("minecraft:gravel", GRAVEL);
        states.put("minecraft:mud", MUD);
        states.put("minecraft:snow_block", SNOW_BLOCK);
        states.put("minecraft:water", WATER);
        states.put("minecraft:oak_planks", OAK_PLANKS);
        states.put("minecraft:oak_log", OAK_LOG);
        states.put("minecraft:cobblestone", COBBLESTONE);
        states.put("minecraft:stone_bricks", STONE_BRICKS);
        states.put("minecraft:oak_fence", OAK_FENCE);
        for (int mask = 1; mask < 16; mask++) {
            states.put("minecraft:oak_fence[east=" + ((mask & 1) != 0)
                    + ",north=" + ((mask & 2) != 0)
                    + ",south=" + ((mask & 4) != 0)
                    + ",waterlogged=false,west=" + ((mask & 8) != 0) + "]", OAK_FENCE + mask);
        }
        return Collections.unmodifiableMap(states);
    }
}
