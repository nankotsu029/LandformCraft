package com.github.nankotsu029.landformcraft.generator;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.CardinalDirection;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.IntGrid;
import com.github.nankotsu029.landformcraft.model.SurfaceMaterial;
import com.github.nankotsu029.landformcraft.model.SurfaceMaterialGrid;
import com.github.nankotsu029.landformcraft.model.StructurePlan;
import com.github.nankotsu029.landformcraft.model.TerrainFeature;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.TerrainTile;
import com.github.nankotsu029.landformcraft.model.TerrainZone;
import com.github.nankotsu029.landformcraft.model.TerrainZoneType;
import com.github.nankotsu029.landformcraft.model.TilePlan;
import com.github.nankotsu029.landformcraft.model.Topology;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Deterministic low-resolution-to-tiled terrain engine using global coordinates and calculation margins. */
public final class TerrainGenerator {
    public static final int TILE_MARGIN = 16;
    private static final int[][] EROSION_NEIGHBORS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private final LogicalLayoutGenerator layoutGenerator = new LogicalLayoutGenerator();
    private final StructurePlanner structurePlanner = new StructurePlanner();

    public TerrainPlan generate(WorldBlueprint blueprint, CancellationToken cancellationToken) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        LogicalTerrainLayout layout = layoutGenerator.generate(blueprint, cancellationToken);
        GenerationBounds bounds = blueprint.bounds();
        GeneratedRegion region = generateRegion(
                blueprint, layout, 0, 0, bounds.width(), bounds.length(), 0, cancellationToken
        );
        List<StructurePlan> structures = structurePlanner.plan(
                blueprint, region.heights(), region.waterDepths(),
                region.featureMasks(), cancellationToken
        );
        List<TilePlan> tiles = createTiles(blueprint, region);
        String checksum = TerrainChecksum.terrain(
                blueprint,
                region.heights(),
                region.waterDepths(),
                region.materialOrdinals(),
                region.featureMasks(),
                structures
        );
        return new TerrainPlan(
                blueprint,
                new IntGrid(bounds.width(), bounds.length(), region.heights()),
                new IntGrid(bounds.width(), bounds.length(), region.waterDepths()),
                new SurfaceMaterialGrid(bounds.width(), bounds.length(), region.materials()),
                new IntGrid(bounds.width(), bounds.length(), region.featureMasks()),
                tiles,
                structures,
                checksum
        );
    }

    /** Generates one tile with a margin and returns only the center region. */
    public TerrainTile generateTile(
            WorldBlueprint blueprint,
            int xIndex,
            int zIndex,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (xIndex < 0 || xIndex >= blueprint.tileCountX()
                || zIndex < 0 || zIndex >= blueprint.tileCountZ()) {
            throw new IllegalArgumentException("tile index outside blueprint bounds");
        }
        int originX = xIndex * blueprint.tileSize();
        int originZ = zIndex * blueprint.tileSize();
        int width = Math.min(blueprint.tileSize(), blueprint.bounds().width() - originX);
        int length = Math.min(blueprint.tileSize(), blueprint.bounds().length() - originZ);
        LogicalTerrainLayout layout = layoutGenerator.generate(blueprint, cancellationToken);
        GeneratedRegion region = generateRegion(
                blueprint, layout, originX, originZ, width, length, TILE_MARGIN, cancellationToken
        );
        String checksum = TerrainChecksum.tile(
                originX,
                originZ,
                width,
                length,
                width,
                originX,
                originZ,
                region.heights(),
                region.waterDepths(),
                region.materialOrdinals(),
                region.featureMasks()
        );
        TilePlan tilePlan = new TilePlan(
                tileId(xIndex, zIndex),
                xIndex,
                zIndex,
                originX,
                originZ,
                width,
                length,
                TILE_MARGIN,
                checksum
        );
        return new TerrainTile(
                tilePlan,
                new IntGrid(width, length, region.heights()),
                new IntGrid(width, length, region.waterDepths()),
                new SurfaceMaterialGrid(width, length, region.materials()),
                new IntGrid(width, length, region.featureMasks())
        );
    }

    private static GeneratedRegion generateRegion(
            WorldBlueprint blueprint,
            LogicalTerrainLayout layout,
            int originX,
            int originZ,
            int width,
            int length,
            int margin,
            CancellationToken token
    ) {
        GenerationBounds bounds = blueprint.bounds();
        int expandedOriginX = Math.max(0, originX - margin);
        int expandedOriginZ = Math.max(0, originZ - margin);
        int expandedEndX = Math.min(bounds.width(), originX + width + margin);
        int expandedEndZ = Math.min(bounds.length(), originZ + length + margin);
        int expandedWidth = expandedEndX - expandedOriginX;
        int expandedLength = expandedEndZ - expandedOriginZ;
        int[] expandedHeights = new int[Math.multiplyExact(expandedWidth, expandedLength)];
        int[] expandedFeatures = new int[expandedHeights.length];

        for (int localZ = 0; localZ < expandedLength; localZ++) {
            token.throwIfCancellationRequested();
            int globalZ = expandedOriginZ + localZ;
            for (int localX = 0; localX < expandedWidth; localX++) {
                int globalX = expandedOriginX + localX;
                Cell cell = cellAt(blueprint, layout, globalX, globalZ);
                int index = localZ * expandedWidth + localX;
                expandedHeights[index] = cell.height();
                expandedFeatures[index] = cell.featureMask();
            }
        }

        int[] heights = new int[Math.multiplyExact(width, length)];
        int[] waterDepths = new int[heights.length];
        int[] featureMasks = new int[heights.length];
        SurfaceMaterial[] materials = new SurfaceMaterial[heights.length];
        byte[] materialOrdinals = new byte[heights.length];
        int localOriginX = originX - expandedOriginX;
        int localOriginZ = originZ - expandedOriginZ;

        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                int expandedX = localOriginX + x;
                int expandedZ = localOriginZ + z;
                int expandedIndex = expandedZ * expandedWidth + expandedX;
                int resultIndex = z * width + x;
                int height = expandedHeights[expandedIndex];
                int waterDepth = Math.max(0, bounds.waterLevel() - height);
                int slope = slopeAt(
                        expandedHeights, expandedWidth, expandedLength, expandedX, expandedZ
                );
                int surfaceSlope = waterDepth == 0
                        ? drySurfaceSlopeAt(expandedHeights, expandedWidth, expandedLength,
                        expandedX, expandedZ, bounds.waterLevel())
                        : slope;
                int globalX = originX + x;
                int globalZ = originZ + z;
                SurfaceMaterial material = materialAt(
                        blueprint, globalX, globalZ, height, waterDepth, slope
                );
                TerrainZone zone = dominantZone(blueprint, globalX, globalZ);
                int featureMask = expandedFeatures[expandedIndex];
                if (surfaceSlope >= 8) {
                    featureMask |= TerrainFeature.CLIFF.mask();
                }
                if (waterDepth == 0 && height <= bounds.waterLevel() + 2) {
                    featureMask |= TerrainFeature.COAST.mask();
                }
                boolean forestZone = zone != null && zone.type() == TerrainZoneType.FOREST;
                if (material == SurfaceMaterial.GRASS
                        && (forestZone || DeterministicNoise.value(
                                blueprint.seed() ^ 0x73A5L, globalX / 11.0, globalZ / 11.0
                        ) > 0.18)) {
                    featureMask |= TerrainFeature.VEGETATION.mask();
                }
                heights[resultIndex] = height;
                waterDepths[resultIndex] = waterDepth;
                featureMasks[resultIndex] = featureMask;
                materials[resultIndex] = material;
                materialOrdinals[resultIndex] = (byte) material.ordinal();
            }
        }
        return new GeneratedRegion(heights, waterDepths, featureMasks, materials, materialOrdinals);
    }

    private static Cell cellAt(WorldBlueprint blueprint, LogicalTerrainLayout layout, int x, int z) {
        GenerationBounds bounds = blueprint.bounds();
        int height = erodedHeightAt(blueprint, layout, x, z);
        int featureMask = 0;
        TerrainIntent intent = blueprint.intent();
        for (int river = 0; river < intent.water().riverCount(); river++) {
            double offset = (river - (intent.water().riverCount() - 1) / 2.0) * 0.18;
            double center = riverCenter(blueprint, x, z, offset);
            double distance = riverDistance(blueprint, x, z, center);
            double halfWidth = 1.8 + Math.min(bounds.width(), bounds.length()) / 220.0;
            if (distance <= halfWidth && height >= bounds.waterLevel() - 1) {
                height = Math.min(height, bounds.waterLevel() - 2);
                featureMask |= TerrainFeature.RIVER.mask();
            }
        }
        for (int lake = 0; lake < intent.water().lakeCount(); lake++) {
            double centerX = (0.25 + pseudoUnit(blueprint.seed(), lake, 17) * 0.5) * bounds.width();
            double centerZ = (0.25 + pseudoUnit(blueprint.seed(), lake, 31) * 0.5) * bounds.length();
            double radius = Math.max(3.0, Math.min(bounds.width(), bounds.length()) * (0.035 + lake * 0.002));
            if (Math.hypot(x - centerX, z - centerZ) <= radius) {
                height = Math.min(height, bounds.waterLevel() - 3);
                featureMask |= TerrainFeature.LAKE.mask();
            }
        }
        return new Cell(clamp(height, bounds.minY(), bounds.maxY()), featureMask);
    }

    private static int erodedHeightAt(WorldBlueprint blueprint, LogicalTerrainLayout layout, int x, int z) {
        int center = rawHeightAt(blueprint, layout, x, z);
        int waterLevel = blueprint.bounds().waterLevel();
        if (center <= waterLevel + 1) {
            return center;
        }
        int weightedSum = center * 4;
        int weight = 4;
        for (int[] offset : EROSION_NEIGHBORS) {
            int neighborX = clamp(x + offset[0], 0, blueprint.bounds().width() - 1);
            int neighborZ = clamp(z + offset[1], 0, blueprint.bounds().length() - 1);
            int neighbor = rawHeightAt(blueprint, layout, neighborX, neighborZ);
            if (neighbor > waterLevel) {
                weightedSum += neighbor;
                weight++;
            }
        }
        return Math.round(weightedSum / (float) weight);
    }

    private static int rawHeightAt(WorldBlueprint blueprint, LogicalTerrainLayout layout, int x, int z) {
        GenerationBounds bounds = blueprint.bounds();
        TerrainIntent intent = blueprint.intent();
        double normalizedX = (x + 0.5) / bounds.width();
        double normalizedZ = (z + 0.5) / bounds.length();
        double fineDetail = DeterministicNoise.fractal(
                blueprint.seed() ^ 0xD37A11L, x / 24.0, z / 24.0, 3
        );
        double continental = layout.continentalAt(normalizedX, normalizedZ)
                + fineDetail * 0.035 * (0.4 + intent.coastline().irregularity());
        double threshold = 1.0 - intent.landRatio();
        if (hasSea(intent.topology()) && continental < threshold) {
            double divisor = Math.max(0.05, threshold);
            double depthRatio = clamp((threshold - continental) / divisor, 0.0, 1.0);
            int depth = 1 + (int) Math.round(intent.water().maximumSeaDepth() * depthRatio);
            return bounds.waterLevel() - depth;
        }
        double relief = clamp(
                layout.reliefAt(normalizedX, normalizedZ) + fineDetail * 0.055,
                intent.relief().minimum(),
                intent.relief().maximum()
        );
        int available = Math.max(1, bounds.maxY() - bounds.waterLevel());
        return bounds.waterLevel() + Math.max(1, (int) Math.round(relief * available * 0.72));
    }

    private static SurfaceMaterial materialAt(
            WorldBlueprint blueprint,
            int x,
            int z,
            int height,
            int waterDepth,
            int slope
    ) {
        GenerationBounds bounds = blueprint.bounds();
        TerrainZone zone = dominantZone(blueprint, x, z);
        if (waterDepth > 0) {
            return waterDepth <= 3 ? SurfaceMaterial.SAND : SurfaceMaterial.GRAVEL;
        }
        if (height >= bounds.maxY() - Math.max(4, bounds.verticalSpan() / 12)) {
            return SurfaceMaterial.SNOW;
        }
        if (slope >= 5 || zone != null && (zone.type() == TerrainZoneType.ROCKY_COAST
                || zone.type() == TerrainZoneType.CLIFFS)
                && height <= bounds.waterLevel() + 18) {
            return SurfaceMaterial.STONE;
        }
        if (height <= bounds.waterLevel() + 2
                || zone != null && zone.type() == TerrainZoneType.SANDY_BEACH
                && height <= bounds.waterLevel() + 24) {
            return SurfaceMaterial.SAND;
        }
        if (zone != null && zone.type() == TerrainZoneType.WETLAND
                && height <= bounds.waterLevel() + 6) {
            return SurfaceMaterial.MUD;
        }
        double wetness = DeterministicNoise.value(blueprint.seed() ^ 0xBEEFL, x / 19.0, z / 19.0);
        return wetness > 0.58 ? SurfaceMaterial.MUD : SurfaceMaterial.GRASS;
    }

    private static TerrainZone dominantZone(WorldBlueprint blueprint, int x, int z) {
        double normalizedX = (x + 0.5) / blueprint.bounds().width();
        double normalizedZ = (z + 0.5) / blueprint.bounds().length();
        TerrainZone best = null;
        double bestScore = 0.06;
        for (TerrainZone zone : blueprint.intent().zones()) {
            double transitionNoise = DeterministicNoise.value(
                    blueprint.seed() ^ zone.id().hashCode(), x / 28.0, z / 28.0
            ) * 0.035;
            double score = LogicalLayoutGenerator.zoneScore(zone, normalizedX, normalizedZ) + transitionNoise;
            if (score > bestScore) {
                bestScore = score;
                best = zone;
            }
        }
        return best;
    }

    private static boolean hasSea(Topology topology) {
        return topology == Topology.COAST || topology == Topology.COAST_WITH_RIVER
                || topology == Topology.ISLAND || topology == Topology.ARCHIPELAGO;
    }

    private static double riverCenter(WorldBlueprint blueprint, int x, int z, double offset) {
        GenerationBounds bounds = blueprint.bounds();
        CardinalDirection side = blueprint.intent().seaSides().stream().findFirst().orElse(CardinalDirection.EAST);
        double progress = side == CardinalDirection.EAST || side == CardinalDirection.WEST
                ? x / (double) Math.max(1, bounds.width() - 1)
                : z / (double) Math.max(1, bounds.length() - 1);
        double meander = DeterministicNoise.value(blueprint.seed() ^ 0xA11CE5L, progress * 4.0, 0.5) * 0.12;
        double crossSize = side == CardinalDirection.EAST || side == CardinalDirection.WEST
                ? bounds.length() : bounds.width();
        return (0.5 + offset + meander) * crossSize;
    }

    private static double riverDistance(WorldBlueprint blueprint, int x, int z, double center) {
        CardinalDirection side = blueprint.intent().seaSides().stream().findFirst().orElse(CardinalDirection.EAST);
        return Math.abs((side == CardinalDirection.EAST || side == CardinalDirection.WEST ? z : x) - center);
    }

    private static double pseudoUnit(long seed, int index, int salt) {
        return (DeterministicNoise.value(seed ^ salt, index * 1.37, salt * 0.17) + 1.0) * 0.5;
    }

    private static int slopeAt(int[] heights, int width, int length, int x, int z) {
        int center = heights[z * width + x];
        int west = heights[z * width + Math.max(0, x - 1)];
        int east = heights[z * width + Math.min(width - 1, x + 1)];
        int north = heights[Math.max(0, z - 1) * width + x];
        int south = heights[Math.min(length - 1, z + 1) * width + x];
        return Math.max(Math.max(Math.abs(center - west), Math.abs(center - east)),
                Math.max(Math.abs(center - north), Math.abs(center - south)));
    }

    private static int drySurfaceSlopeAt(
            int[] heights, int width, int length, int x, int z, int waterLevel
    ) {
        int center = heights[z * width + x];
        int result = 0;
        int[][] neighbors = {
                {Math.max(0, x - 1), z}, {Math.min(width - 1, x + 1), z},
                {x, Math.max(0, z - 1)}, {x, Math.min(length - 1, z + 1)}
        };
        for (int[] neighbor : neighbors) {
            int height = heights[neighbor[1] * width + neighbor[0]];
            if (height >= waterLevel) {
                result = Math.max(result, Math.abs(center - height));
            }
        }
        return result;
    }

    private static List<TilePlan> createTiles(WorldBlueprint blueprint, GeneratedRegion region) {
        List<TilePlan> result = new ArrayList<>();
        int mapWidth = blueprint.bounds().width();
        for (int zIndex = 0; zIndex < blueprint.tileCountZ(); zIndex++) {
            for (int xIndex = 0; xIndex < blueprint.tileCountX(); xIndex++) {
                int originX = xIndex * blueprint.tileSize();
                int originZ = zIndex * blueprint.tileSize();
                int width = Math.min(blueprint.tileSize(), blueprint.bounds().width() - originX);
                int length = Math.min(blueprint.tileSize(), blueprint.bounds().length() - originZ);
                String checksum = TerrainChecksum.tile(
                        originX,
                        originZ,
                        width,
                        length,
                        mapWidth,
                        0,
                        0,
                        region.heights(),
                        region.waterDepths(),
                        region.materialOrdinals(),
                        region.featureMasks()
                );
                result.add(new TilePlan(
                        tileId(xIndex, zIndex),
                        xIndex,
                        zIndex,
                        originX,
                        originZ,
                        width,
                        length,
                        TILE_MARGIN,
                        checksum
                ));
            }
        }
        return List.copyOf(result);
    }

    private static String tileId(int xIndex, int zIndex) {
        return String.format(Locale.ROOT, "tile-%02d-%02d", xIndex, zIndex);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Cell(int height, int featureMask) {
    }

    private record GeneratedRegion(
            int[] heights,
            int[] waterDepths,
            int[] featureMasks,
            SurfaceMaterial[] materials,
            byte[] materialOrdinals
    ) {
    }
}
