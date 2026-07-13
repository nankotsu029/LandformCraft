package com.github.nankotsu029.landformcraft.model;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record TerrainPlan(
        WorldBlueprint blueprint,
        IntGrid heightMap,
        IntGrid waterDepthMap,
        SurfaceMaterialGrid surfaceMaterials,
        IntGrid featureMask,
        List<TilePlan> tiles,
        List<StructurePlan> structures,
        String checksum
) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public TerrainPlan {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(heightMap, "heightMap");
        Objects.requireNonNull(waterDepthMap, "waterDepthMap");
        Objects.requireNonNull(surfaceMaterials, "surfaceMaterials");
        Objects.requireNonNull(featureMask, "featureMask");
        int width = blueprint.bounds().width();
        int length = blueprint.bounds().length();
        if (heightMap.width() != width || heightMap.length() != length
                || waterDepthMap.width() != width || waterDepthMap.length() != length
                || surfaceMaterials.width() != width || surfaceMaterials.length() != length
                || featureMask.width() != width || featureMask.length() != length) {
            throw new IllegalArgumentException("terrain grids must match blueprint bounds");
        }
        tiles = ModelValidation.immutableList(tiles, "tiles", 1_024);
        structures = ModelValidation.immutableList(structures, "structures", 256);
        checksum = ModelValidation.requireNonBlank(checksum, "checksum");
        if (!SHA_256.matcher(checksum).matches()) {
            throw new IllegalArgumentException("checksum must be a lowercase SHA-256 value");
        }
    }
}
