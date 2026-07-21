package com.github.nankotsu029.landformcraft.validation;

import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.GridPosition;
import com.github.nankotsu029.landformcraft.model.TerrainFeature;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.StructurePlan;
import com.github.nankotsu029.landformcraft.model.StructureType;
import com.github.nankotsu029.landformcraft.model.TilePlan;
import com.github.nankotsu029.landformcraft.model.ValidationIssue;
import com.github.nankotsu029.landformcraft.model.ValidationResult;
import com.github.nankotsu029.landformcraft.model.ValidationSeverity;
import com.github.nankotsu029.landformcraft.structure.BuiltInStructureAssetCatalog;
import com.github.nankotsu029.landformcraft.structure.StructureAsset;
import com.github.nankotsu029.landformcraft.structure.StructureRotation;
import com.github.nankotsu029.landformcraft.structure.StructurePlacementKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

/** Checks generated 2D terrain invariants before preview or export. */
public final class TerrainValidator {
    private static final int MAX_REPORTED_CELL_ERRORS = 100;

    public ValidationResult validate(TerrainPlan plan) {
        List<ValidationIssue> issues = new ArrayList<>();
        validateCells(plan, issues);
        validateTiles(plan, issues);
        validateRiverPresence(plan, issues);
        validateWaterConnectivity(plan, issues);
        validateRiverFlow(plan, issues);
        validateStructures(plan, issues);
        return new ValidationResult(issues);
    }

    private static void validateStructures(TerrainPlan plan, List<ValidationIssue> issues) {
        BuiltInStructureAssetCatalog catalog = new BuiltInStructureAssetCatalog();
        EnumMap<StructureType, Integer> actualCounts = new EnumMap<>(StructureType.class);
        for (int index = 0; index < plan.structures().size(); index++) {
            StructurePlan placement = plan.structures().get(index);
            actualCounts.merge(placement.type(), 1, Integer::sum);
            if (placement.preferredZoneFallback()) {
                issues.add(warning("structure-preferred-zone-fallback",
                        placement.type() + " was placed outside its preferred zone after all safe preferred-zone "
                                + "candidates failed water, cliff, slope, bounds, or spacing checks"));
            }
            StructureAsset asset;
            try {
                asset = catalog.requireById(placement.assetId());
            } catch (IllegalArgumentException exception) {
                issues.add(error("unknown-structure-asset", exception.getMessage(), GridPosition.GLOBAL));
                continue;
            }
            if (asset.type() != placement.type()
                    || !asset.semanticChecksum().equals(placement.assetChecksum())
                    || !asset.minecraftVersion().equals(placement.minecraftVersion())
                    || asset.rotatedWidth(placement.rotation()) != placement.sizeX()
                    || asset.height() != placement.sizeY()
                    || asset.rotatedLength(placement.rotation()) != placement.sizeZ()
                    || asset.terrainFollowing() != placement.terrainFollowing()) {
                issues.add(error("structure-asset-mismatch",
                        "placement metadata does not match asset: " + placement.assetId(), GridPosition.GLOBAL));
                continue;
            }
            if (!structureWithinBounds(plan, placement)) {
                issues.add(error("structure-out-of-bounds",
                        "structure exceeds generation bounds: " + placement.assetId(), GridPosition.GLOBAL));
                continue;
            }
            validateStructureFootprint(plan, placement, asset, issues);
            for (int otherIndex = 0; otherIndex < index; otherIndex++) {
                if (structuresCollide(placement, plan.structures().get(otherIndex))) {
                    issues.add(error("structure-collision",
                            "structure footprints overlap or lack the required separation", GridPosition.GLOBAL));
                    break;
                }
            }
        }
        EnumMap<StructureType, Integer> requestedCounts = new EnumMap<>(StructureType.class);
        plan.blueprint().intent().structures().forEach(intent ->
                requestedCounts.merge(intent.type(), intent.count(), Integer::sum));
        requestedCounts.forEach((type, requested) -> {
            int actual = actualCounts.getOrDefault(type, 0);
            if (actual < requested) {
                issues.add(warning("structure-not-placed",
                        "placed " + actual + " of " + requested + " requested " + type
                                + " structures; no remaining candidate satisfied preferred zone, water, cliff, "
                                + "slope, bounds, and spacing constraints"));
            }
        });
    }

    private static boolean structureWithinBounds(TerrainPlan plan, StructurePlan placement) {
        GenerationBounds bounds = plan.blueprint().bounds();
        if (placement.anchorX() < 0 || placement.anchorZ() < 0
                || placement.anchorX() + placement.sizeX() > bounds.width()
                || placement.anchorZ() + placement.sizeZ() > bounds.length()
                || placement.anchorY() < bounds.minY()) {
            return false;
        }
        int highest = placement.anchorY() + placement.sizeY() - 1;
        if (placement.terrainFollowing()) {
            highest = Integer.MIN_VALUE;
            for (int z = placement.anchorZ(); z < placement.anchorZ() + placement.sizeZ(); z++) {
                for (int x = placement.anchorX(); x < placement.anchorX() + placement.sizeX(); x++) {
                    highest = Math.max(highest, plan.heightMap().get(x, z) + placement.sizeY());
                }
            }
        }
        return highest <= bounds.maxY();
    }

    private static void validateStructureFootprint(
            TerrainPlan plan,
            StructurePlan placement,
            StructureAsset asset,
            List<ValidationIssue> issues
    ) {
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
                var rotated = StructureRotation.rotate(asset, placement.rotation(), localX, localZ);
                int x = placement.anchorX() + rotated.x();
                int z = placement.anchorZ() + rotated.z();
                boolean wet = plan.waterDepthMap().get(x, z) > 0;
                int height = plan.heightMap().get(x, z);
                if (!wet || asset.placementKind() == StructurePlacementKind.DRY_FLAT
                        || asset.placementKind() == StructurePlacementKind.DRY_FOLLOWING) {
                    minimumHeight = Math.min(minimumHeight, height);
                    maximumHeight = Math.max(maximumHeight, height);
                }
                water += wet ? 1 : 0;
                cliffs += TerrainFeature.CLIFF.isPresent(plan.featureMask().get(x, z))
                        && (!wet || asset.placementKind() == StructurePlacementKind.DRY_FLAT
                        || asset.placementKind() == StructurePlacementKind.DRY_FOLLOWING) ? 1 : 0;
                entranceDry += localZ == 0 && !wet ? 1 : 0;
                farWater += localZ >= asset.length() - 2 && wet ? 1 : 0;
                farDry += localZ == asset.length() - 1 && !wet ? 1 : 0;
                centerWater += Math.abs(localZ - asset.length() / 2) <= 1 && wet ? 1 : 0;
            }
        }
        if (minimumHeight == Integer.MAX_VALUE) {
            minimumHeight = plan.blueprint().bounds().waterLevel();
            maximumHeight = plan.blueprint().bounds().waterLevel();
        }
        if (cliffs > 0 || maximumHeight - minimumHeight > asset.maximumSlope()) {
            issues.add(error("structure-cliff-collision",
                    "structure intersects a cliff or exceeds its slope limit", placement.anchorX(), placement.anchorZ()));
        }
        int cells = asset.width() * asset.length();
        boolean waterValid;
        if (asset.placementKind() == StructurePlacementKind.WATER_EDGE) {
            waterValid = entranceDry >= Math.max(1, asset.width() / 2)
                    && farWater >= Math.max(2, asset.width()) && water >= cells / 4
                    && maximumHeight <= plan.blueprint().bounds().waterLevel() + 4;
        } else if (asset.placementKind() == StructurePlacementKind.WATER_CROSSING) {
            waterValid = entranceDry >= Math.max(1, asset.width() - 1)
                    && farDry >= Math.max(1, asset.width() - 1)
                    && centerWater >= asset.width() * 2 && water >= cells / 4
                    && maximumHeight <= plan.blueprint().bounds().waterLevel() + 4;
        } else {
            waterValid = water == 0;
        }
        if (!waterValid) {
            issues.add(error("structure-water-collision",
                    "structure does not satisfy its land/water placement rule",
                    placement.anchorX(), placement.anchorZ()));
        }
    }

    private static boolean structuresCollide(StructurePlan left, StructurePlan right) {
        int padding = 1;
        return left.anchorX() - padding <= right.anchorX() + right.sizeX() - 1 + padding
                && left.anchorX() + left.sizeX() - 1 + padding >= right.anchorX() - padding
                && left.anchorZ() - padding <= right.anchorZ() + right.sizeZ() - 1 + padding
                && left.anchorZ() + left.sizeZ() - 1 + padding >= right.anchorZ() - padding;
    }

    private static void validateCells(TerrainPlan plan, List<ValidationIssue> issues) {
        GenerationBounds bounds = plan.blueprint().bounds();
        int reported = 0;
        for (int z = 0; z < bounds.length() && reported < MAX_REPORTED_CELL_ERRORS; z++) {
            for (int x = 0; x < bounds.width() && reported < MAX_REPORTED_CELL_ERRORS; x++) {
                int height = plan.heightMap().get(x, z);
                int waterDepth = plan.waterDepthMap().get(x, z);
                if (height < bounds.minY() || height > bounds.maxY()) {
                    issues.add(error("height-out-of-bounds", "height is outside blueprint bounds", x, z));
                    reported++;
                } else if (waterDepth != Math.max(0, bounds.waterLevel() - height)) {
                    issues.add(error("water-depth-mismatch", "water depth does not match height", x, z));
                    reported++;
                }
            }
        }
    }

    private static void validateTiles(TerrainPlan plan, List<ValidationIssue> issues) {
        int expected = plan.blueprint().tileCountX() * plan.blueprint().tileCountZ();
        if (plan.tiles().size() != expected) {
            issues.add(error("tile-count-mismatch", "tile count does not cover the blueprint", GridPosition.GLOBAL));
            return;
        }
        Set<String> ids = new HashSet<>();
        Set<Long> coordinates = new HashSet<>();
        long coveredColumns = 0L;
        for (TilePlan tile : plan.tiles()) {
            coveredColumns += (long) tile.width() * tile.length();
            if (!ids.add(tile.id())) {
                issues.add(error("duplicate-tile-id", "duplicate tile id: " + tile.id(), GridPosition.GLOBAL));
            }
            if (tile.originX() + tile.width() > plan.blueprint().bounds().width()
                    || tile.originZ() + tile.length() > plan.blueprint().bounds().length()) {
                issues.add(error("tile-out-of-bounds", "tile exceeds blueprint bounds: " + tile.id(), GridPosition.GLOBAL));
            }
            long coordinate = ((long) tile.zIndex() << 32) | Integer.toUnsignedLong(tile.xIndex());
            if (!coordinates.add(coordinate)) {
                issues.add(error("duplicate-tile-coordinate", "duplicate tile coordinate: " + tile.id(),
                        GridPosition.GLOBAL));
            }
            if (tile.xIndex() >= plan.blueprint().tileCountX()
                    || tile.zIndex() >= plan.blueprint().tileCountZ()) {
                issues.add(error("tile-grid-mismatch", "tile index is outside the blueprint grid: " + tile.id(),
                        GridPosition.GLOBAL));
                continue;
            }
            int expectedOriginX = tile.xIndex() * plan.blueprint().tileSize();
            int expectedOriginZ = tile.zIndex() * plan.blueprint().tileSize();
            int expectedWidth = Math.min(
                    plan.blueprint().tileSize(), plan.blueprint().bounds().width() - expectedOriginX
            );
            int expectedLength = Math.min(
                    plan.blueprint().tileSize(), plan.blueprint().bounds().length() - expectedOriginZ
            );
            String expectedId = String.format(java.util.Locale.ROOT, "tile-%02d-%02d", tile.xIndex(), tile.zIndex());
            if (expectedWidth < 1 || expectedLength < 1
                    || !tile.id().equals(expectedId)
                    || tile.originX() != expectedOriginX || tile.originZ() != expectedOriginZ
                    || tile.width() != expectedWidth || tile.length() != expectedLength) {
                issues.add(error("tile-grid-mismatch", "tile does not match the blueprint grid: " + tile.id(),
                        GridPosition.GLOBAL));
            }
        }
        if (coveredColumns != plan.blueprint().bounds().columnCount()) {
            issues.add(error("tile-coverage-mismatch", "tiles do not cover every terrain column exactly once", GridPosition.GLOBAL));
        }
    }

    private static void validateRiverPresence(TerrainPlan plan, List<ValidationIssue> issues) {
        if (plan.blueprint().intent().water().riverCount() == 0) {
            return;
        }
        boolean found = false;
        for (int z = 0; z < plan.featureMask().length() && !found; z++) {
            for (int x = 0; x < plan.featureMask().width(); x++) {
                if (TerrainFeature.RIVER.isPresent(plan.featureMask().get(x, z))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            issues.add(error("river-missing", "intent requested a river but no river cells were generated", GridPosition.GLOBAL));
        }
    }

    private static void validateWaterConnectivity(TerrainPlan plan, List<ValidationIssue> issues) {
        boolean[] connected = boundaryConnectedWater(plan);
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int features = plan.featureMask().get(x, z);
                boolean allowedIsolatedFeature = TerrainFeature.LAKE.isPresent(features);
                if (plan.waterDepthMap().get(x, z) > 0 && !connected[index] && !allowedIsolatedFeature) {
                    issues.add(error(
                            "isolated-water",
                            "water cell is disconnected from a boundary and is not a declared lake",
                            x,
                            z
                    ));
                    return;
                }
            }
        }
    }

    private static void validateRiverFlow(TerrainPlan plan, List<ValidationIssue> issues) {
        if (plan.blueprint().intent().water().riverCount() == 0) {
            return;
        }
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        boolean[] drains = new boolean[Math.multiplyExact(width, length)];
        int[] queue = new int[drains.length];
        int tail = seedRiverOutlets(plan, boundaryConnectedWater(plan), drains, queue);
        int head = 0;
        while (head < tail) {
            int index = queue[head++];
            int x = index % width;
            int z = index / width;
            tail = visitUpstreamRiver(plan, drains, queue, tail, x, z, x - 1, z);
            tail = visitUpstreamRiver(plan, drains, queue, tail, x, z, x + 1, z);
            tail = visitUpstreamRiver(plan, drains, queue, tail, x, z, x, z - 1);
            tail = visitUpstreamRiver(plan, drains, queue, tail, x, z, x, z + 1);
        }
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (TerrainFeature.RIVER.isPresent(plan.featureMask().get(x, z)) && !drains[index]) {
                    issues.add(error(
                            "river-flow-reversal",
                            "river cell cannot reach a boundary without flowing uphill out of a pit",
                            x,
                            z
                    ));
                    return;
                }
            }
        }
    }

    private static int seedBoundaryWater(TerrainPlan plan, boolean[] visited, int[] queue) {
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        int tail = 0;
        for (int x = 0; x < width; x++) {
            tail = addWaterSeed(plan, visited, queue, tail, x, 0);
            tail = addWaterSeed(plan, visited, queue, tail, x, length - 1);
        }
        for (int z = 1; z < length - 1; z++) {
            tail = addWaterSeed(plan, visited, queue, tail, 0, z);
            tail = addWaterSeed(plan, visited, queue, tail, width - 1, z);
        }
        return tail;
    }

    private static int seedBoundaryRiver(TerrainPlan plan, boolean[] visited, int[] queue) {
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        int tail = 0;
        for (int x = 0; x < width; x++) {
            tail = addRiverSeed(plan, visited, queue, tail, x, 0);
            tail = addRiverSeed(plan, visited, queue, tail, x, length - 1);
        }
        for (int z = 1; z < length - 1; z++) {
            tail = addRiverSeed(plan, visited, queue, tail, 0, z);
            tail = addRiverSeed(plan, visited, queue, tail, width - 1, z);
        }
        return tail;
    }

    private static int seedRiverOutlets(
            TerrainPlan plan,
            boolean[] boundaryWater,
            boolean[] visited,
            int[] queue
    ) {
        int tail = seedBoundaryRiver(plan, visited, queue);
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        for (int z = 1; z < length - 1; z++) {
            for (int x = 1; x < width - 1; x++) {
                if (!TerrainFeature.RIVER.isPresent(plan.featureMask().get(x, z))) {
                    continue;
                }
                if (isSeaOutlet(plan, boundaryWater, x - 1, z)
                        || isSeaOutlet(plan, boundaryWater, x + 1, z)
                        || isSeaOutlet(plan, boundaryWater, x, z - 1)
                        || isSeaOutlet(plan, boundaryWater, x, z + 1)) {
                    tail = addRiverSeed(plan, visited, queue, tail, x, z);
                }
            }
        }
        return tail;
    }

    private static boolean isSeaOutlet(TerrainPlan plan, boolean[] boundaryWater, int x, int z) {
        int index = z * plan.heightMap().width() + x;
        return boundaryWater[index]
                && plan.waterDepthMap().get(x, z) > 0
                && !TerrainFeature.RIVER.isPresent(plan.featureMask().get(x, z));
    }

    private static boolean[] boundaryConnectedWater(TerrainPlan plan) {
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        boolean[] connected = new boolean[Math.multiplyExact(width, length)];
        int[] queue = new int[connected.length];
        int tail = seedBoundaryWater(plan, connected, queue);
        int head = 0;
        while (head < tail) {
            int index = queue[head++];
            int x = index % width;
            int z = index / width;
            tail = visitWaterNeighbor(plan, connected, queue, tail, x - 1, z);
            tail = visitWaterNeighbor(plan, connected, queue, tail, x + 1, z);
            tail = visitWaterNeighbor(plan, connected, queue, tail, x, z - 1);
            tail = visitWaterNeighbor(plan, connected, queue, tail, x, z + 1);
        }
        return connected;
    }

    private static int addWaterSeed(
            TerrainPlan plan,
            boolean[] visited,
            int[] queue,
            int tail,
            int x,
            int z
    ) {
        int index = z * plan.heightMap().width() + x;
        if (!visited[index] && plan.waterDepthMap().get(x, z) > 0) {
            visited[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private static int addRiverSeed(
            TerrainPlan plan,
            boolean[] visited,
            int[] queue,
            int tail,
            int x,
            int z
    ) {
        int index = z * plan.heightMap().width() + x;
        if (!visited[index] && TerrainFeature.RIVER.isPresent(plan.featureMask().get(x, z))) {
            visited[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private static int visitWaterNeighbor(
            TerrainPlan plan,
            boolean[] visited,
            int[] queue,
            int tail,
            int x,
            int z
    ) {
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        if (x < 0 || x >= width || z < 0 || z >= length) {
            return tail;
        }
        int index = z * width + x;
        if (!visited[index] && plan.waterDepthMap().get(x, z) > 0) {
            visited[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private static int visitUpstreamRiver(
            TerrainPlan plan,
            boolean[] visited,
            int[] queue,
            int tail,
            int currentX,
            int currentZ,
            int neighborX,
            int neighborZ
    ) {
        int width = plan.heightMap().width();
        int length = plan.heightMap().length();
        if (neighborX < 0 || neighborX >= width || neighborZ < 0 || neighborZ >= length) {
            return tail;
        }
        int index = neighborZ * width + neighborX;
        if (!visited[index]
                && TerrainFeature.RIVER.isPresent(plan.featureMask().get(neighborX, neighborZ))
                && plan.heightMap().get(neighborX, neighborZ) >= plan.heightMap().get(currentX, currentZ)) {
            visited[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private static ValidationIssue error(String code, String message, int x, int z) {
        return error(code, message, new GridPosition(x, z));
    }

    private static ValidationIssue error(String code, String message, GridPosition position) {
        return new ValidationIssue(ValidationSeverity.ERROR, code, message, position);
    }

    private static ValidationIssue warning(String code, String message) {
        return new ValidationIssue(ValidationSeverity.WARNING, code, message, GridPosition.GLOBAL);
    }
}
