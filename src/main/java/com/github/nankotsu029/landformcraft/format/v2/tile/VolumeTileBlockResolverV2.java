package com.github.nankotsu029.landformcraft.format.v2.tile;

import com.github.nankotsu029.landformcraft.format.v2.minecraft.EnvironmentBlockStateCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainQuery;

import java.util.Objects;

/**
 * V2-5-16 volume export adapter. Bridges a volume-composed {@link TerrainQuery}
 * (kernel {@link TerrainQuery#QUERY_KERNEL_VOLUME_V1}) to canonical Minecraft block states for
 * offline Sponge v3 read-back. Air cavities, fluids, and independent solids resolve directly from
 * the composed occupancy; nothing is invented for missing semantic material or non-water fluid.
 *
 * <p>This is separate from the frozen v1 {@code V1TerrainBlockResolver}: it requires the volume
 * kernel and only emits states from the shared {@link EnvironmentBlockStateCatalogV2} allowlist,
 * keeping semantic material and Minecraft block state distinct per the architecture boundary.</p>
 */
public final class VolumeTileBlockResolverV2 implements TerrainBlockResolver {
    private final TerrainQuery volume;

    public VolumeTileBlockResolverV2(TerrainQuery volume) {
        this.volume = Objects.requireNonNull(volume, "volume");
        if (!TerrainQuery.QUERY_KERNEL_VOLUME_V1.equals(volume.queryKernelVersion())) {
            throw new IllegalArgumentException(
                    "volume tile export requires the " + TerrainQuery.QUERY_KERNEL_VOLUME_V1 + " query kernel");
        }
    }

    @Override
    public String blockStateAt(int x, int y, int z) {
        String state = switch (volume.blockClassAt(x, y, z)) {
            case AIR -> "minecraft:air";
            case FLUID -> fluidState(volume.fluidBodyAt(x, y, z));
            case SOLID -> solidState(volume.semanticMaterialAt(x, y, z));
        };
        return EnvironmentBlockStateCatalogV2.requireKnown(state);
    }

    private static String fluidState(TerrainQuery.FluidBody fluid) {
        if (fluid == TerrainQuery.FluidBody.WATER) {
            return "minecraft:water";
        }
        throw new IllegalStateException("volume fluid cell has no semantic fluid body");
    }

    private static String solidState(TerrainQuery.SemanticMaterial material) {
        return switch (material) {
            case BEDROCK -> "minecraft:bedrock";
            case STONE -> "minecraft:stone";
            case DIRT -> "minecraft:dirt";
            case GRASS -> "minecraft:grass_block";
            case SAND -> "minecraft:sand";
            case SANDSTONE -> "minecraft:sandstone";
            case GRAVEL -> "minecraft:gravel";
            case MUD -> "minecraft:mud";
            case SNOW -> "minecraft:snow_block";
            case NONE -> throw new IllegalStateException("volume solid cell has no semantic material");
        };
    }
}
