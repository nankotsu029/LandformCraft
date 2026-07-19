package com.github.nankotsu029.landformcraft.format.v2.minecraft;

import com.github.nankotsu029.landformcraft.format.v2.tile.CanonicalBlockStateV2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Closed V2-4-08 environment export allowlist. It is a strict superset of the V2-2 coastal catalog
 * and includes enough vanilla states to exercise the Sponge palette 127/128 VarInt boundary without
 * inventing non-Minecraft identifiers.
 */
public final class EnvironmentBlockStateCatalogV2 {
    private static final List<String> DYE_COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private static final List<String> ORDERED_STATES = buildStates();
    private static final Set<String> STATES = Set.copyOf(ORDERED_STATES);

    private EnvironmentBlockStateCatalogV2() {
    }

    public static int size() {
        return ORDERED_STATES.size();
    }

    public static boolean contains(String state) {
        return STATES.contains(CanonicalBlockStateV2.requireCanonical(state));
    }

    public static String requireKnown(String state) {
        String canonical = CanonicalBlockStateV2.requireCanonical(state);
        if (!STATES.contains(canonical)) {
            throw new IllegalArgumentException("unknown V2-4 environment block state: " + canonical);
        }
        return canonical;
    }

    /** Deterministic, insertion-ordered snapshot used by palette-boundary tests. */
    public static List<String> orderedStates() {
        return ORDERED_STATES;
    }

    private static List<String> buildStates() {
        LinkedHashSet<String> states = new LinkedHashSet<>();
        states.addAll(List.of(
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
                "minecraft:water"));
        for (String color : DYE_COLORS) {
            states.add("minecraft:" + color + "_wool");
            states.add("minecraft:" + color + "_carpet");
            states.add("minecraft:" + color + "_concrete");
            states.add("minecraft:" + color + "_concrete_powder");
            states.add("minecraft:" + color + "_terracotta");
            states.add("minecraft:" + color + "_stained_glass");
            states.add("minecraft:" + color + "_stained_glass_pane");
            states.add("minecraft:" + color + "_shulker_box");
        }
        List<String> ordered = List.copyOf(new ArrayList<>(states));
        if (ordered.size() < 129) {
            throw new IllegalStateException(
                    "environment block-state catalog must cover the 128 palette-ID VarInt boundary");
        }
        return ordered;
    }
}
