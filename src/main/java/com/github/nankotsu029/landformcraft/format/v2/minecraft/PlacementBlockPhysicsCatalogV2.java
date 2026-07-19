package com.github.nankotsu029.landformcraft.format.v2.minecraft;

import com.github.nankotsu029.landformcraft.format.v2.tile.CanonicalBlockStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPhysicsClassV2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Closed identifier→physics catalog for Release 2 containment preflight (V2-6-05).
 * Classification is deterministic and property-independent after the identifier is stripped.
 * Unknown identifiers have no entry; callers must reject rather than guess.
 */
public final class PlacementBlockPhysicsCatalogV2 {
    public static final String CATALOG_VERSION = "release-2-placement-block-physics-catalog-v1";

    private static final Map<String, PlacementBlockPhysicsKindV2> BY_IDENTIFIER = buildCatalog();

    private PlacementBlockPhysicsCatalogV2() {
    }

    public static String catalogVersion() {
        return CATALOG_VERSION;
    }

    public static int size() {
        return BY_IDENTIFIER.size();
    }

    public static Optional<PlacementBlockPhysicsKindV2> find(String blockState) {
        String identifier = identifierOf(CanonicalBlockStateV2.requireCanonical(blockState));
        return Optional.ofNullable(BY_IDENTIFIER.get(identifier));
    }

    public static PlacementBlockPhysicsKindV2 require(String blockState) {
        return find(blockState).orElseThrow(() -> new IllegalArgumentException(
                "unknown placement block physics state: " + blockState));
    }

    public static boolean isKnown(String blockState) {
        return find(blockState).isPresent();
    }

    public static PlacementPhysicsClassV2 toEnvelopeClass(PlacementBlockPhysicsKindV2 kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case SOLID -> PlacementPhysicsClassV2.SOLID;
            case AIR -> PlacementPhysicsClassV2.AIR;
            case FLUID -> PlacementPhysicsClassV2.FLUID;
            case GRAVITY -> PlacementPhysicsClassV2.GRAVITY;
            case NEIGHBOR -> PlacementPhysicsClassV2.NEIGHBOR;
            case UNSUPPORTED -> throw new IllegalArgumentException(
                    "UNSUPPORTED cannot map to an envelope physics class");
        };
    }

    public static String identifierOf(String canonicalBlockState) {
        String value = CanonicalBlockStateV2.requireCanonical(canonicalBlockState);
        int propertiesStart = value.indexOf('[');
        return propertiesStart < 0 ? value : value.substring(0, propertiesStart);
    }

    /** Deterministic insertion-ordered snapshot for tests. */
    public static Map<String, PlacementBlockPhysicsKindV2> orderedEntries() {
        return Map.copyOf(BY_IDENTIFIER);
    }

    private static Map<String, PlacementBlockPhysicsKindV2> buildCatalog() {
        LinkedHashMap<String, PlacementBlockPhysicsKindV2> map = new LinkedHashMap<>();
        putAll(map, PlacementBlockPhysicsKindV2.AIR, List.of(
                "minecraft:air",
                "minecraft:cave_air",
                "minecraft:void_air"));
        putAll(map, PlacementBlockPhysicsKindV2.FLUID, List.of(
                "minecraft:water",
                "minecraft:lava"));
        putAll(map, PlacementBlockPhysicsKindV2.GRAVITY, List.of(
                "minecraft:sand",
                "minecraft:red_sand",
                "minecraft:gravel",
                "minecraft:white_concrete_powder",
                "minecraft:orange_concrete_powder",
                "minecraft:magenta_concrete_powder",
                "minecraft:light_blue_concrete_powder",
                "minecraft:yellow_concrete_powder",
                "minecraft:lime_concrete_powder",
                "minecraft:pink_concrete_powder",
                "minecraft:gray_concrete_powder",
                "minecraft:light_gray_concrete_powder",
                "minecraft:cyan_concrete_powder",
                "minecraft:purple_concrete_powder",
                "minecraft:blue_concrete_powder",
                "minecraft:brown_concrete_powder",
                "minecraft:green_concrete_powder",
                "minecraft:red_concrete_powder",
                "minecraft:black_concrete_powder"));
        putAll(map, PlacementBlockPhysicsKindV2.NEIGHBOR, List.of(
                "minecraft:oak_fence",
                "minecraft:glass_pane",
                "minecraft:iron_bars",
                "minecraft:cobblestone_wall"));
        putAll(map, PlacementBlockPhysicsKindV2.UNSUPPORTED, List.of(
                "minecraft:fire",
                "minecraft:soul_fire",
                "minecraft:tnt",
                "minecraft:redstone_wire",
                "minecraft:redstone_block",
                "minecraft:observer",
                "minecraft:piston",
                "minecraft:sticky_piston",
                "minecraft:spawner",
                "minecraft:command_block",
                "minecraft:chain_command_block",
                "minecraft:repeating_command_block",
                "minecraft:structure_block",
                "minecraft:nether_portal",
                "minecraft:end_portal",
                "minecraft:end_gateway",
                "minecraft:bubble_column",
                "minecraft:kelp",
                "minecraft:kelp_plant",
                "minecraft:seagrass",
                "minecraft:tall_seagrass",
                "minecraft:sponge",
                "minecraft:wet_sponge"));
        putAll(map, PlacementBlockPhysicsKindV2.SOLID, List.of(
                "minecraft:bedrock",
                "minecraft:stone",
                "minecraft:stone_bricks",
                "minecraft:cobblestone",
                "minecraft:dirt",
                "minecraft:grass_block",
                "minecraft:mud",
                "minecraft:sandstone",
                "minecraft:red_sandstone",
                "minecraft:oak_log",
                "minecraft:oak_planks",
                "minecraft:snow_block",
                "minecraft:packed_ice",
                "minecraft:blue_ice",
                "minecraft:prismarine",
                "minecraft:dark_prismarine",
                "minecraft:prismarine_bricks",
                "minecraft:basalt",
                "minecraft:smooth_basalt",
                "minecraft:blackstone",
                "minecraft:deepslate",
                "minecraft:cobbled_deepslate",
                "minecraft:polished_deepslate",
                "minecraft:deepslate_bricks",
                "minecraft:deepslate_tiles",
                "minecraft:reinforced_deepslate",
                "minecraft:tuff",
                "minecraft:calcite",
                "minecraft:dripstone_block",
                "minecraft:granite",
                "minecraft:diorite",
                "minecraft:andesite",
                "minecraft:coal_ore",
                "minecraft:iron_ore",
                "minecraft:copper_ore",
                "minecraft:gold_ore",
                "minecraft:redstone_ore",
                "minecraft:lapis_ore",
                "minecraft:diamond_ore",
                "minecraft:emerald_ore",
                "minecraft:deepslate_coal_ore",
                "minecraft:deepslate_iron_ore",
                "minecraft:deepslate_copper_ore",
                "minecraft:deepslate_gold_ore",
                "minecraft:deepslate_redstone_ore",
                "minecraft:deepslate_lapis_ore",
                "minecraft:deepslate_diamond_ore",
                "minecraft:deepslate_emerald_ore",
                "minecraft:raw_iron_block",
                "minecraft:raw_copper_block",
                "minecraft:raw_gold_block",
                "minecraft:terracotta",
                "minecraft:white_wool",
                "minecraft:orange_wool",
                "minecraft:magenta_wool",
                "minecraft:light_blue_wool",
                "minecraft:yellow_wool",
                "minecraft:lime_wool",
                "minecraft:pink_wool",
                "minecraft:gray_wool",
                "minecraft:light_gray_wool",
                "minecraft:cyan_wool",
                "minecraft:purple_wool",
                "minecraft:blue_wool",
                "minecraft:brown_wool",
                "minecraft:green_wool",
                "minecraft:red_wool",
                "minecraft:black_wool",
                "minecraft:white_concrete",
                "minecraft:orange_concrete",
                "minecraft:magenta_concrete",
                "minecraft:light_blue_concrete",
                "minecraft:yellow_concrete",
                "minecraft:lime_concrete",
                "minecraft:pink_concrete",
                "minecraft:gray_concrete",
                "minecraft:light_gray_concrete",
                "minecraft:cyan_concrete",
                "minecraft:purple_concrete",
                "minecraft:blue_concrete",
                "minecraft:brown_concrete",
                "minecraft:green_concrete",
                "minecraft:red_concrete",
                "minecraft:black_concrete",
                "minecraft:white_terracotta",
                "minecraft:orange_terracotta",
                "minecraft:magenta_terracotta",
                "minecraft:light_blue_terracotta",
                "minecraft:yellow_terracotta",
                "minecraft:lime_terracotta",
                "minecraft:pink_terracotta",
                "minecraft:gray_terracotta",
                "minecraft:light_gray_terracotta",
                "minecraft:cyan_terracotta",
                "minecraft:purple_terracotta",
                "minecraft:blue_terracotta",
                "minecraft:brown_terracotta",
                "minecraft:green_terracotta",
                "minecraft:red_terracotta",
                "minecraft:black_terracotta",
                "minecraft:glass",
                "minecraft:white_stained_glass",
                "minecraft:orange_stained_glass",
                "minecraft:magenta_stained_glass",
                "minecraft:light_blue_stained_glass",
                "minecraft:yellow_stained_glass",
                "minecraft:lime_stained_glass",
                "minecraft:pink_stained_glass",
                "minecraft:gray_stained_glass",
                "minecraft:light_gray_stained_glass",
                "minecraft:cyan_stained_glass",
                "minecraft:purple_stained_glass",
                "minecraft:blue_stained_glass",
                "minecraft:brown_stained_glass",
                "minecraft:green_stained_glass",
                "minecraft:red_stained_glass",
                "minecraft:black_stained_glass"));
        return Map.copyOf(map);
    }

    private static void putAll(
            LinkedHashMap<String, PlacementBlockPhysicsKindV2> map,
            PlacementBlockPhysicsKindV2 kind,
            List<String> identifiers
    ) {
        for (String identifier : identifiers) {
            if (map.put(identifier, kind) != null) {
                throw new IllegalStateException("duplicate placement physics catalog entry: " + identifier);
            }
        }
    }
}
