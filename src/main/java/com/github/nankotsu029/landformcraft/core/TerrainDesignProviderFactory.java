package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignProvider;

/** Runtime adapter factory injected by Paper so core depends only on the provider SPI. */
@FunctionalInterface
public interface TerrainDesignProviderFactory {
    TerrainDesignProvider create(String provider, String modelOrPath);
}
