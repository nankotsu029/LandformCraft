package com.github.nankotsu029.landformcraft.generator.v2;

import com.github.nankotsu029.landformcraft.model.IntGrid;
import com.github.nankotsu029.landformcraft.model.SurfaceMaterial;
import com.github.nankotsu029.landformcraft.model.SurfaceMaterialGrid;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.TerrainTile;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Read-only bridge from the frozen v1 column fields to the v2 semantic query boundary.
 *
 * <p>The adapter accepts either a whole {@link TerrainPlan} or one independently generated
 * {@link TerrainTile}. Coordinates exposed by both forms are global release-local coordinates.
 * Structures remain the separate v1 overlay and are intentionally not folded into this base
 * terrain adapter.</p>
 */
public final class V1TerrainQueryAdapter implements TerrainQuery {
    public static final String SUPPORTED_GENERATOR_VERSION = "3.0.0-phase6";
    private static final int SUBSOIL_DEPTH = 3;

    private final WorldBlueprint blueprint;
    private final QueryBounds queryBounds;
    private final int gridOriginX;
    private final int gridOriginZ;
    private final IntGrid heightMap;
    private final IntGrid waterDepthMap;
    private final SurfaceMaterialGrid surfaceMaterials;
    private final IntGrid featureMask;

    public V1TerrainQueryAdapter(TerrainPlan plan) {
        this(
                Objects.requireNonNull(plan, "plan").blueprint(),
                0,
                0,
                plan.heightMap(),
                plan.waterDepthMap(),
                plan.surfaceMaterials(),
                plan.featureMask()
        );
    }

    public static V1TerrainQueryAdapter forTile(WorldBlueprint blueprint, TerrainTile tile) {
        Objects.requireNonNull(tile, "tile");
        return new V1TerrainQueryAdapter(
                blueprint,
                tile.plan().originX(),
                tile.plan().originZ(),
                tile.heightMap(),
                tile.waterDepthMap(),
                tile.surfaceMaterials(),
                tile.featureMask()
        );
    }

    private V1TerrainQueryAdapter(
            WorldBlueprint blueprint,
            int gridOriginX,
            int gridOriginZ,
            IntGrid heightMap,
            IntGrid waterDepthMap,
            SurfaceMaterialGrid surfaceMaterials,
            IntGrid featureMask
    ) {
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.heightMap = Objects.requireNonNull(heightMap, "heightMap");
        this.waterDepthMap = Objects.requireNonNull(waterDepthMap, "waterDepthMap");
        this.surfaceMaterials = Objects.requireNonNull(surfaceMaterials, "surfaceMaterials");
        this.featureMask = Objects.requireNonNull(featureMask, "featureMask");
        requireCurrentV1(blueprint);
        requireMatchingDimensions(heightMap, waterDepthMap, surfaceMaterials, featureMask);
        if (gridOriginX < 0 || gridOriginZ < 0
                || (long) gridOriginX + heightMap.width() > blueprint.bounds().width()
                || (long) gridOriginZ + heightMap.length() > blueprint.bounds().length()) {
            throw new IllegalArgumentException("v1 query window lies outside blueprint bounds");
        }
        this.gridOriginX = gridOriginX;
        this.gridOriginZ = gridOriginZ;
        this.queryBounds = new QueryBounds(
                gridOriginX,
                gridOriginZ,
                heightMap.width(),
                heightMap.length(),
                blueprint.bounds().minY(),
                blueprint.bounds().maxY()
        );
    }

    @Override
    public QueryBounds bounds() {
        return queryBounds;
    }

    @Override
    public BlockClass blockClassAt(int x, int y, int z) {
        requireCoordinate(x, y, z);
        int surfaceY = heightAt(x, z);
        if (y <= surfaceY) {
            return BlockClass.SOLID;
        }
        if (waterDepthAt(x, z) > 0 && y <= blueprint.bounds().waterLevel()) {
            return BlockClass.FLUID;
        }
        return BlockClass.AIR;
    }

    @Override
    public SemanticMaterial semanticMaterialAt(int x, int y, int z) {
        requireCoordinate(x, y, z);
        if (blockClassAt(x, y, z) != BlockClass.SOLID) {
            return SemanticMaterial.NONE;
        }
        int surfaceY = heightAt(x, z);
        if (y == blueprint.bounds().minY()) {
            return SemanticMaterial.BEDROCK;
        }
        if (y < surfaceY - SUBSOIL_DEPTH + 1) {
            return SemanticMaterial.STONE;
        }
        SurfaceMaterial surface = surfaceMaterialAt(x, z);
        if (y < surfaceY) {
            return subsoil(surface);
        }
        return surface(surface);
    }

    @Override
    public FluidBody fluidBodyAt(int x, int y, int z) {
        requireCoordinate(x, y, z);
        return blockClassAt(x, y, z) == BlockClass.FLUID ? FluidBody.WATER : FluidBody.NONE;
    }

    @Override
    public List<VerticalInterval> solidIntervals(int x, int z) {
        requireColumn(x, z);
        return List.of(new VerticalInterval(blueprint.bounds().minY(), heightAt(x, z)));
    }

    @Override
    public List<VerticalInterval> fluidIntervals(int x, int z) {
        requireColumn(x, z);
        int startY = heightAt(x, z) + 1;
        int waterLevel = blueprint.bounds().waterLevel();
        if (waterDepthAt(x, z) <= 0 || startY > waterLevel) {
            return List.of();
        }
        return List.of(new VerticalInterval(startY, waterLevel));
    }

    @Override
    public OptionalInt topWalkableSurface(int x, int z, WalkableSurfacePolicy policy) {
        Objects.requireNonNull(policy, "policy");
        requireColumn(x, z);
        if (policy == WalkableSurfacePolicy.DRY_ONLY && !fluidIntervals(x, z).isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(heightAt(x, z));
    }

    @Override
    public OptionalInt surfaceBelow(int x, int y, int z) {
        requireCoordinate(x, y, z);
        int surfaceY = heightAt(x, z);
        return y >= surfaceY ? OptionalInt.of(surfaceY) : OptionalInt.empty();
    }

    @Override
    public OptionalInt ceilingAbove(int x, int y, int z) {
        requireCoordinate(x, y, z);
        return OptionalInt.empty();
    }

    @Override
    public int featureMembershipAt(int x, int y, int z) {
        requireCoordinate(x, y, z);
        return featureMask.get(localX(x), localZ(z));
    }

    private int heightAt(int x, int z) {
        return heightMap.get(localX(x), localZ(z));
    }

    private int waterDepthAt(int x, int z) {
        return waterDepthMap.get(localX(x), localZ(z));
    }

    private SurfaceMaterial surfaceMaterialAt(int x, int z) {
        return surfaceMaterials.get(localX(x), localZ(z));
    }

    private int localX(int x) {
        return x - gridOriginX;
    }

    private int localZ(int z) {
        return z - gridOriginZ;
    }

    private void requireColumn(int x, int z) {
        if (!queryBounds.containsColumn(x, z)) {
            throw new IndexOutOfBoundsException("column coordinate outside v1 query window");
        }
    }

    private void requireCoordinate(int x, int y, int z) {
        if (!queryBounds.contains(x, y, z)) {
            throw new IndexOutOfBoundsException("block coordinate outside v1 query window");
        }
    }

    private static void requireCurrentV1(WorldBlueprint blueprint) {
        if (blueprint.schemaVersion() != 1
                || !SUPPORTED_GENERATOR_VERSION.equals(blueprint.generatorVersion())) {
            throw new IllegalArgumentException(
                    "v1 adapter requires schema 1 and generator " + SUPPORTED_GENERATOR_VERSION
            );
        }
    }

    private static void requireMatchingDimensions(
            IntGrid heightMap,
            IntGrid waterDepthMap,
            SurfaceMaterialGrid surfaceMaterials,
            IntGrid featureMask
    ) {
        int width = heightMap.width();
        int length = heightMap.length();
        if (waterDepthMap.width() != width || waterDepthMap.length() != length
                || surfaceMaterials.width() != width || surfaceMaterials.length() != length
                || featureMask.width() != width || featureMask.length() != length) {
            throw new IllegalArgumentException("v1 query grids must have matching dimensions");
        }
    }

    private static SemanticMaterial surface(SurfaceMaterial material) {
        return switch (material) {
            case GRASS -> SemanticMaterial.GRASS;
            case SAND -> SemanticMaterial.SAND;
            case STONE -> SemanticMaterial.STONE;
            case GRAVEL -> SemanticMaterial.GRAVEL;
            case MUD -> SemanticMaterial.MUD;
            case SNOW -> SemanticMaterial.SNOW;
        };
    }

    private static SemanticMaterial subsoil(SurfaceMaterial material) {
        return switch (material) {
            case GRASS, MUD -> SemanticMaterial.DIRT;
            case SAND -> SemanticMaterial.SANDSTONE;
            case STONE, GRAVEL, SNOW -> SemanticMaterial.STONE;
        };
    }
}
