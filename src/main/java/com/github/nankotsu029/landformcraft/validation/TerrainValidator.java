package com.github.nankotsu029.landformcraft.validation;

import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.GridPosition;
import com.github.nankotsu029.landformcraft.model.TerrainFeature;
import com.github.nankotsu029.landformcraft.model.TerrainPlan;
import com.github.nankotsu029.landformcraft.model.TilePlan;
import com.github.nankotsu029.landformcraft.model.ValidationIssue;
import com.github.nankotsu029.landformcraft.model.ValidationResult;
import com.github.nankotsu029.landformcraft.model.ValidationSeverity;

import java.util.ArrayList;
import java.util.HashSet;
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
        return new ValidationResult(issues);
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
}
