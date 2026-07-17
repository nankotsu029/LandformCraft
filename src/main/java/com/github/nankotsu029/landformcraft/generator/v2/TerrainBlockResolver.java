package com.github.nankotsu029.landformcraft.generator.v2;

/** Resolves a semantic terrain query to one canonical Minecraft block-state string. */
@FunctionalInterface
public interface TerrainBlockResolver {
    String blockStateAt(int x, int y, int z);
}
