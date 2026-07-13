package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;

/** Immutable center region produced after discarding a tile's calculation margin. */
public record TerrainTile(
        TilePlan plan,
        IntGrid heightMap,
        IntGrid waterDepthMap,
        SurfaceMaterialGrid surfaceMaterials,
        IntGrid featureMask
) {
    public TerrainTile {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(heightMap, "heightMap");
        Objects.requireNonNull(waterDepthMap, "waterDepthMap");
        Objects.requireNonNull(surfaceMaterials, "surfaceMaterials");
        Objects.requireNonNull(featureMask, "featureMask");
        if (heightMap.width() != plan.width() || heightMap.length() != plan.length()
                || waterDepthMap.width() != plan.width() || waterDepthMap.length() != plan.length()
                || surfaceMaterials.width() != plan.width() || surfaceMaterials.length() != plan.length()
                || featureMask.width() != plan.width() || featureMask.length() != plan.length()) {
            throw new IllegalArgumentException("tile grids must match the center tile dimensions");
        }
    }
}
