package com.github.nankotsu029.landformcraft.generator.v2;

import java.util.List;
import java.util.OptionalInt;

/**
 * Pure, global-coordinate view of final semantic terrain.
 *
 * <p>The contract deliberately contains no Bukkit, Paper, WorldEdit, or Minecraft block-state
 * types. Export adapters resolve the semantic result through a separately versioned palette.</p>
 */
public interface TerrainQuery {
    QueryBounds bounds();

    BlockClass blockClassAt(int x, int y, int z);

    SemanticMaterial semanticMaterialAt(int x, int y, int z);

    FluidBody fluidBodyAt(int x, int y, int z);

    List<VerticalInterval> solidIntervals(int x, int z);

    List<VerticalInterval> fluidIntervals(int x, int z);

    OptionalInt topWalkableSurface(int x, int z, WalkableSurfacePolicy policy);

    OptionalInt surfaceBelow(int x, int y, int z);

    OptionalInt ceilingAbove(int x, int y, int z);

    int featureMembershipAt(int x, int y, int z);

    enum BlockClass {
        SOLID,
        FLUID,
        AIR
    }

    /** Minimal semantic vocabulary needed to reproduce the frozen v1 material profile. */
    enum SemanticMaterial {
        NONE,
        BEDROCK,
        STONE,
        DIRT,
        GRASS,
        SAND,
        SANDSTONE,
        GRAVEL,
        MUD,
        SNOW
    }

    enum FluidBody {
        NONE,
        WATER
    }

    enum WalkableSurfacePolicy {
        DRY_ONLY,
        ALLOW_SUBMERGED
    }

    /** Inclusive vertical interval. */
    record VerticalInterval(int minY, int maxY) {
        public VerticalInterval {
            if (minY > maxY) {
                throw new IllegalArgumentException("minY must not exceed maxY");
            }
        }

        public boolean contains(int y) {
            return y >= minY && y <= maxY;
        }
    }

    /** Horizontal query window in global coordinates and its inclusive vertical limits. */
    record QueryBounds(
            int originX,
            int originZ,
            int width,
            int length,
            int minY,
            int maxY
    ) {
        public QueryBounds {
            if (originX < 0 || originZ < 0 || width < 1 || length < 1 || minY > maxY) {
                throw new IllegalArgumentException("invalid query bounds");
            }
        }

        public boolean contains(int x, int y, int z) {
            return containsColumn(x, z) && y >= minY && y <= maxY;
        }

        public boolean containsColumn(int x, int z) {
            return x >= originX && x < originX + width
                    && z >= originZ && z < originZ + length;
        }
    }
}
