package com.github.nankotsu029.landformcraft.generator;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.QuarterTurn;
import com.github.nankotsu029.landformcraft.model.StructureIntent;
import com.github.nankotsu029.landformcraft.model.StructurePlan;
import com.github.nankotsu029.landformcraft.model.TerrainFeature;
import com.github.nankotsu029.landformcraft.model.TerrainZone;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;
import com.github.nankotsu029.landformcraft.structure.BuiltInStructureAssetCatalog;
import com.github.nankotsu029.landformcraft.structure.StructureAsset;
import com.github.nankotsu029.landformcraft.structure.StructurePlacementKind;

import java.util.ArrayList;
import java.util.List;

/** Deterministically selects only terrain-safe, non-overlapping small structure placements. */
public final class StructurePlanner {
    private static final int COLLISION_PADDING = 1;
    private static final QuarterTurn[] ROTATIONS = QuarterTurn.values();

    private final BuiltInStructureAssetCatalog catalog = new BuiltInStructureAssetCatalog();

    public List<StructurePlan> plan(
            WorldBlueprint blueprint,
            int[] heights,
            int[] waterDepths,
            int[] featureMasks,
            CancellationToken token
    ) {
        int width = blueprint.bounds().width();
        int length = blueprint.bounds().length();
        int columns = Math.multiplyExact(width, length);
        if (heights.length != columns || waterDepths.length != columns || featureMasks.length != columns) {
            throw new IllegalArgumentException("structure planning grids do not match blueprint bounds");
        }
        List<StructurePlan> placed = new ArrayList<>();
        int requestOrdinal = 0;
        for (StructureIntent intent : blueprint.intent().structures()) {
            StructureAsset asset = catalog.requireByType(intent.type());
            for (int ordinal = 0; ordinal < intent.count(); ordinal++) {
                token.throwIfCancellationRequested();
                StructurePlan placement = findPlacement(
                        blueprint, asset, intent.preferredZone(), heights, waterDepths, featureMasks,
                        placed, requestOrdinal++, token
                );
                if (placement != null) {
                    placed.add(placement);
                }
            }
        }
        return List.copyOf(placed);
    }

    private static StructurePlan findPlacement(
            WorldBlueprint blueprint,
            StructureAsset asset,
            String preferredZone,
            int[] heights,
            int[] waterDepths,
            int[] featureMasks,
            List<StructurePlan> placed,
            int ordinal,
            CancellationToken token
    ) {
        int width = blueprint.bounds().width();
        int length = blueprint.bounds().length();
        int columns = Math.multiplyExact(width, length);
        long mixed = mix64(blueprint.seed() ^ ((long) asset.type().ordinal() << 32) ^ ordinal);
        int start = Math.floorMod(mixed, columns);
        int rotationStart = Math.floorMod(mixed >>> 32, ROTATIONS.length);
        for (int pass = 0; pass < 2; pass++) {
            boolean requirePreferredZone = pass == 0;
            for (int offset = 0; offset < columns; offset++) {
                if ((offset & 1023) == 0) {
                    token.throwIfCancellationRequested();
                }
                int index = (start + offset) % columns;
                int candidateX = index % width;
                int candidateZ = index / width;
                for (int rotationOffset = 0; rotationOffset < ROTATIONS.length; rotationOffset++) {
                    QuarterTurn rotation = ROTATIONS[(rotationStart + rotationOffset) % ROTATIONS.length];
                    int sizeX = asset.rotatedWidth(rotation);
                    int sizeZ = asset.rotatedLength(rotation);
                    if (candidateX < 1 || candidateZ < 1
                            || candidateX + sizeX >= width || candidateZ + sizeZ >= length) {
                        continue;
                    }
                    int centerX = candidateX + sizeX / 2;
                    int centerZ = candidateZ + sizeZ / 2;
                    if (requirePreferredZone
                            && !preferredZone.equals(dominantZoneId(blueprint, centerX, centerZ))) {
                        continue;
                    }
                    Footprint footprint = inspect(
                            blueprint, asset, rotation, candidateX, candidateZ, heights, waterDepths, featureMasks
                    );
                    if (!footprint.valid() || collides(candidateX, candidateZ, sizeX, sizeZ, placed)) {
                        continue;
                    }
                    int anchorY = anchorY(blueprint, asset, footprint);
                    if (anchorY < blueprint.bounds().minY()
                            || maximumPlacedY(asset, anchorY, footprint) > blueprint.bounds().maxY()) {
                        continue;
                    }
                    return new StructurePlan(
                            asset.assetId(), asset.semanticChecksum(), asset.minecraftVersion(), asset.type(),
                            candidateX, anchorY, candidateZ, rotation,
                            sizeX, asset.height(), sizeZ, asset.terrainFollowing(), !requirePreferredZone
                    );
                }
            }
        }
        return null;
    }

    private static Footprint inspect(
            WorldBlueprint blueprint,
            StructureAsset asset,
            QuarterTurn rotation,
            int anchorX,
            int anchorZ,
            int[] heights,
            int[] waterDepths,
            int[] featureMasks
    ) {
        int width = blueprint.bounds().width();
        int minimumHeight = Integer.MAX_VALUE;
        int maximumHeight = Integer.MIN_VALUE;
        int water = 0;
        int cliffs = 0;
        int entranceDry = 0;
        int farWater = 0;
        int farDry = 0;
        int centerWater = 0;
        for (int localZ = 0; localZ < asset.length(); localZ++) {
            for (int localX = 0; localX < asset.width(); localX++) {
                Rotated rotated = rotate(asset, rotation, localX, localZ);
                int x = anchorX + rotated.x();
                int z = anchorZ + rotated.z();
                int index = z * width + x;
                boolean wet = waterDepths[index] > 0;
                int height = heights[index];
                if (!wet || asset.placementKind() == StructurePlacementKind.DRY_FLAT
                        || asset.placementKind() == StructurePlacementKind.DRY_FOLLOWING) {
                    minimumHeight = Math.min(minimumHeight, height);
                    maximumHeight = Math.max(maximumHeight, height);
                }
                if (wet) {
                    water++;
                }
                if (TerrainFeature.CLIFF.isPresent(featureMasks[index])
                        && (!wet || asset.placementKind() == StructurePlacementKind.DRY_FLAT
                        || asset.placementKind() == StructurePlacementKind.DRY_FOLLOWING)) {
                    cliffs++;
                }
                if (localZ == 0 && !wet) {
                    entranceDry++;
                }
                if (localZ >= asset.length() - 2 && wet) {
                    farWater++;
                }
                if (localZ == asset.length() - 1 && !wet) {
                    farDry++;
                }
                if (Math.abs(localZ - asset.length() / 2) <= 1 && wet) {
                    centerWater++;
                }
            }
        }
        if (minimumHeight == Integer.MAX_VALUE) {
            minimumHeight = blueprint.bounds().waterLevel();
            maximumHeight = blueprint.bounds().waterLevel();
        }
        int cells = asset.width() * asset.length();
        int slope = maximumHeight - minimumHeight;
        boolean valid = cliffs == 0 && slope <= asset.maximumSlope();
        if (asset.placementKind() == StructurePlacementKind.WATER_EDGE) {
            valid &= entranceDry >= Math.max(1, asset.width() / 2)
                    && farWater >= Math.max(2, asset.width())
                    && water >= cells / 4
                    && maximumHeight <= blueprint.bounds().waterLevel() + 4;
        } else if (asset.placementKind() == StructurePlacementKind.WATER_CROSSING) {
            valid &= entranceDry >= Math.max(1, asset.width() - 1)
                    && farDry >= Math.max(1, asset.width() - 1)
                    && centerWater >= asset.width() * 2
                    && water >= cells / 4
                    && maximumHeight <= blueprint.bounds().waterLevel() + 4;
        } else {
            valid &= water == 0;
        }
        return new Footprint(valid, minimumHeight, maximumHeight);
    }

    private static int anchorY(WorldBlueprint blueprint, StructureAsset asset, Footprint footprint) {
        return switch (asset.placementKind()) {
            case WATER_EDGE -> Math.max(blueprint.bounds().waterLevel() + 1, footprint.maximumHeight() + 1) - 3;
            case WATER_CROSSING -> Math.max(blueprint.bounds().waterLevel() + 1, footprint.maximumHeight() + 1) - 1;
            case DRY_FLAT, DRY_FOLLOWING -> footprint.maximumHeight() + 1;
        };
    }

    private static int maximumPlacedY(StructureAsset asset, int anchorY, Footprint footprint) {
        return asset.terrainFollowing()
                ? footprint.maximumHeight() + asset.height()
                : anchorY + asset.height() - 1;
    }

    private static boolean collides(int x, int z, int sizeX, int sizeZ, List<StructurePlan> placed) {
        int minX = x - COLLISION_PADDING;
        int minZ = z - COLLISION_PADDING;
        int maxX = x + sizeX - 1 + COLLISION_PADDING;
        int maxZ = z + sizeZ - 1 + COLLISION_PADDING;
        for (StructurePlan existing : placed) {
            int existingMinX = existing.anchorX() - COLLISION_PADDING;
            int existingMinZ = existing.anchorZ() - COLLISION_PADDING;
            int existingMaxX = existing.anchorX() + existing.sizeX() - 1 + COLLISION_PADDING;
            int existingMaxZ = existing.anchorZ() + existing.sizeZ() - 1 + COLLISION_PADDING;
            if (minX <= existingMaxX && maxX >= existingMinX
                    && minZ <= existingMaxZ && maxZ >= existingMinZ) {
                return true;
            }
        }
        return false;
    }

    private static String dominantZoneId(WorldBlueprint blueprint, int x, int z) {
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
        return best == null ? "" : best.id();
    }

    public static Rotated rotate(StructureAsset asset, QuarterTurn rotation, int x, int z) {
        return switch (rotation) {
            case NONE -> new Rotated(x, z);
            case CLOCKWISE_90 -> new Rotated(asset.length() - 1 - z, x);
            case CLOCKWISE_180 -> new Rotated(asset.width() - 1 - x, asset.length() - 1 - z);
            case CLOCKWISE_270 -> new Rotated(z, asset.width() - 1 - x);
        };
    }

    private static long mix64(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ mixed >>> 30) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ mixed >>> 27) * 0x94D049BB133111EBL;
        return mixed ^ mixed >>> 31;
    }

    public record Rotated(int x, int z) {
    }

    private record Footprint(boolean valid, int minimumHeight, int maximumHeight) {
    }
}
