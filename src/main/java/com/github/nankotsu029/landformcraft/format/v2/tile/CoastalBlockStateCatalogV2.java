package com.github.nankotsu029.landformcraft.format.v2.tile;

import com.github.nankotsu029.landformcraft.format.v2.minecraft.EnvironmentBlockStateCatalogV2;

/**
 * V2-2 coastal export gate. V2-4-08 widened the shared offline allowlist to
 * {@link EnvironmentBlockStateCatalogV2}; coastal states remain a required subset.
 */
final class CoastalBlockStateCatalogV2 {
    private CoastalBlockStateCatalogV2() {
    }

    static String requireKnown(String state) {
        return EnvironmentBlockStateCatalogV2.requireKnown(state);
    }
}
