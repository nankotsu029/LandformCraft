package com.github.nankotsu029.landformcraft.format.v2.tile;

import java.util.Set;

/** Compile-time coastal export allowlist; V2-4 replaces material semantics through a new adapter version. */
final class CoastalBlockStateCatalogV2 {
    private static final Set<String> STATES = Set.of(
            "minecraft:air",
            "minecraft:bedrock",
            "minecraft:cobblestone",
            "minecraft:dirt",
            "minecraft:grass_block",
            "minecraft:gravel",
            "minecraft:mud",
            "minecraft:oak_log",
            "minecraft:oak_planks",
            "minecraft:sand",
            "minecraft:sandstone",
            "minecraft:snow_block",
            "minecraft:stone",
            "minecraft:stone_bricks",
            "minecraft:water"
    );

    private CoastalBlockStateCatalogV2() {
    }

    static String requireKnown(String state) {
        String canonical = CanonicalBlockStateV2.requireCanonical(state);
        if (!STATES.contains(canonical)) {
            throw new IllegalArgumentException("unknown V2-2 coastal block state: " + canonical);
        }
        return canonical;
    }
}
