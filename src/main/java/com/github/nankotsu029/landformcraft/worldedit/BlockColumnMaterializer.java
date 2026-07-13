package com.github.nankotsu029.landformcraft.worldedit;

import com.github.nankotsu029.landformcraft.model.SurfaceMaterial;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;

import java.util.Objects;

/** Converts the compact 2D terrain plan into deterministic vertical block columns on demand. */
public final class BlockColumnMaterializer {
    private static final int SUBSOIL_DEPTH = 3;

    public int paletteIdAt(TerrainPlan plan, int x, int y, int z) {
        Objects.requireNonNull(plan, "plan");
        var bounds = plan.blueprint().bounds();
        if (x < 0 || x >= bounds.width() || z < 0 || z >= bounds.length()
                || y < bounds.minY() || y > bounds.maxY()) {
            throw new IndexOutOfBoundsException("block coordinate outside terrain bounds");
        }
        int surfaceY = plan.heightMap().get(x, z);
        SurfaceMaterial material = plan.surfaceMaterials().get(x, z);
        if (y == bounds.minY()) {
            return MinecraftBlockPalette.BEDROCK;
        }
        if (y < surfaceY - SUBSOIL_DEPTH + 1) {
            return MinecraftBlockPalette.STONE;
        }
        if (y < surfaceY) {
            return MinecraftBlockPalette.subsoil(material);
        }
        if (y == surfaceY) {
            return MinecraftBlockPalette.surface(material);
        }
        if (y <= bounds.waterLevel() && plan.waterDepthMap().get(x, z) > 0) {
            return MinecraftBlockPalette.WATER;
        }
        return MinecraftBlockPalette.AIR;
    }
}
