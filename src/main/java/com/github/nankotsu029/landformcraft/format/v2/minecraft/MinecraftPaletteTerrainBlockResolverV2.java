package com.github.nankotsu029.landformcraft.format.v2.minecraft;

import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;

import java.util.Objects;

/**
 * Column-oriented {@link TerrainBlockResolver} that projects semantic class codes through the
 * frozen Minecraft palette adapter. Bedrock / air / water remain explicit caller inputs; this
 * adapter never invents missing semantic IDs.
 */
public final class MinecraftPaletteTerrainBlockResolverV2 implements TerrainBlockResolver {
    private final MinecraftPaletteResolverV2 palette;
    private final MinecraftPaletteResolverV2.ClassCodeSource surfaceCodes;
    private final int surfaceY;
    private final int minY;
    private final int maxY;

    public MinecraftPaletteTerrainBlockResolverV2(
            MinecraftPaletteResolverV2 palette,
            MinecraftPaletteResolverV2.ClassCodeSource surfaceCodes,
            int surfaceY,
            int minY,
            int maxY
    ) {
        this.palette = Objects.requireNonNull(palette, "palette");
        this.surfaceCodes = Objects.requireNonNull(surfaceCodes, "surfaceCodes");
        if (minY > surfaceY || surfaceY > maxY) {
            throw new IllegalArgumentException("minecraft-palette column bounds are invalid");
        }
        this.surfaceY = surfaceY;
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public String blockStateAt(int x, int y, int z) {
        if (y < minY || y > maxY) {
            throw new IllegalArgumentException("minecraft-palette sample Y is out of column bounds");
        }
        if (y == minY) {
            return EnvironmentBlockStateCatalogV2.requireKnown("minecraft:bedrock");
        }
        if (y > surfaceY) {
            return EnvironmentBlockStateCatalogV2.requireKnown("minecraft:air");
        }
        int classCode = surfaceCodes.classCodeAt(x, z);
        MaterialProfilePlanV2.SurfaceAspect aspect = y == surfaceY
                ? MaterialProfilePlanV2.SurfaceAspect.SURFACE
                : y == minY + 1
                ? MaterialProfilePlanV2.SurfaceAspect.FLOOR
                : MaterialProfilePlanV2.SurfaceAspect.CEILING;
        // Subsurface uses FLOOR for the layer above bedrock and CEILING for remaining fill so all
        // three aspect hooks are exercised by offline schematic read-back without volume support.
        if (y < surfaceY && y > minY + 1) {
            aspect = MaterialProfilePlanV2.SurfaceAspect.CEILING;
        }
        return palette.resolveByCode(classCode, aspect);
    }
}
