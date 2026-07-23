package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Pure semantic query over the frozen coastal production fields.
 *
 * <p>This is the Stage 9 hand-off used by the V2-15-08 sparse-volume pipeline. It translates the
 * existing surface resolver into semantic occupancy without retaining a 3D array. Column interval
 * methods scan only the bounded request Y span; the volume tile writer samples cells directly.</p>
 */
final class CoastalSurfaceTerrainQueryV2 implements TerrainQuery {
    private final QueryBounds bounds;
    private final TerrainBlockResolver resolver;

    /**
     * V2-19-05: the resolver is supplied rather than derived, so this query answers from exactly the
     * same block source the published tiles were written from — including a frozen river overlay.
     */
    CoastalSurfaceTerrainQueryV2(
            CoastalSurfaceFieldsV2 fields,
            TerrainBlockResolver resolver,
            int minY,
            int maxY
    ) {
        Objects.requireNonNull(fields, "fields");
        this.bounds = new QueryBounds(0, 0, fields.width(), fields.length(), minY, maxY);
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public QueryBounds bounds() {
        return bounds;
    }

    @Override
    public BlockClass blockClassAt(int x, int y, int z) {
        return semantic(x, y, z).blockClass();
    }

    @Override
    public SemanticMaterial semanticMaterialAt(int x, int y, int z) {
        return semantic(x, y, z).material();
    }

    @Override
    public FluidBody fluidBodyAt(int x, int y, int z) {
        return semantic(x, y, z).fluidBody();
    }

    @Override
    public List<VerticalInterval> solidIntervals(int x, int z) {
        return intervals(x, z, BlockClass.SOLID);
    }

    @Override
    public List<VerticalInterval> fluidIntervals(int x, int z) {
        return intervals(x, z, BlockClass.FLUID);
    }

    @Override
    public OptionalInt topWalkableSurface(int x, int z, WalkableSurfacePolicy policy) {
        Objects.requireNonNull(policy, "policy");
        List<VerticalInterval> solid = solidIntervals(x, z);
        if (solid.isEmpty()) {
            return OptionalInt.empty();
        }
        if (policy == WalkableSurfacePolicy.DRY_ONLY && !fluidIntervals(x, z).isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(solid.getLast().maxY());
    }

    @Override
    public OptionalInt surfaceBelow(int x, int y, int z) {
        requireCoordinate(x, y, z);
        int result = Integer.MIN_VALUE;
        for (VerticalInterval interval : solidIntervals(x, z)) {
            if (interval.contains(y) && y < interval.maxY()) {
                return OptionalInt.empty();
            }
            if (interval.maxY() <= y) {
                result = Math.max(result, interval.maxY());
            }
        }
        return result == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(result);
    }

    @Override
    public OptionalInt ceilingAbove(int x, int y, int z) {
        requireCoordinate(x, y, z);
        for (VerticalInterval interval : solidIntervals(x, z)) {
            if (interval.minY() > y) {
                return OptionalInt.of(interval.minY());
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public int featureMembershipAt(int x, int y, int z) {
        requireCoordinate(x, y, z);
        return 0;
    }

    TerrainBlockResolver blockResolver() {
        return resolver;
    }

    private List<VerticalInterval> intervals(int x, int z, BlockClass target) {
        requireColumn(x, z);
        List<VerticalInterval> result = new ArrayList<>();
        int y = bounds.minY();
        while (y <= bounds.maxY()) {
            if (blockClassAt(x, y, z) != target) {
                y++;
                continue;
            }
            int start = y++;
            while (y <= bounds.maxY() && blockClassAt(x, y, z) == target) {
                y++;
            }
            result.add(new VerticalInterval(start, y - 1));
        }
        return List.copyOf(result);
    }

    private CellSemantic semantic(int x, int y, int z) {
        requireCoordinate(x, y, z);
        return switch (resolver.blockStateAt(x, y, z)) {
            case "minecraft:air" -> new CellSemantic(BlockClass.AIR, SemanticMaterial.NONE, FluidBody.NONE);
            case "minecraft:water" -> new CellSemantic(BlockClass.FLUID, SemanticMaterial.NONE, FluidBody.WATER);
            case "minecraft:bedrock" -> solid(SemanticMaterial.BEDROCK);
            case "minecraft:stone", "minecraft:cobblestone", "minecraft:stone_bricks" ->
                    solid(SemanticMaterial.STONE);
            case "minecraft:dirt" -> solid(SemanticMaterial.DIRT);
            case "minecraft:grass_block" -> solid(SemanticMaterial.GRASS);
            case "minecraft:sand" -> solid(SemanticMaterial.SAND);
            case "minecraft:sandstone" -> solid(SemanticMaterial.SANDSTONE);
            case "minecraft:gravel" -> solid(SemanticMaterial.GRAVEL);
            case "minecraft:mud" -> solid(SemanticMaterial.MUD);
            case "minecraft:snow_block" -> solid(SemanticMaterial.SNOW);
            default -> throw new IllegalStateException("coastal surface resolver returned an unknown block state");
        };
    }

    private static CellSemantic solid(SemanticMaterial material) {
        return new CellSemantic(BlockClass.SOLID, material, FluidBody.NONE);
    }

    private void requireColumn(int x, int z) {
        if (!bounds.containsColumn(x, z)) {
            throw new IndexOutOfBoundsException("column outside coastal terrain query bounds");
        }
    }

    private void requireCoordinate(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) {
            throw new IndexOutOfBoundsException("coordinate outside coastal terrain query bounds");
        }
    }

    private record CellSemantic(BlockClass blockClass, SemanticMaterial material, FluidBody fluidBody) {
    }
}
