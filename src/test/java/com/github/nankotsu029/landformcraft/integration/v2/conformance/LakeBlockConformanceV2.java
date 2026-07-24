package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.LakeGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;

/**
 * V2-15-11 block-column of the intent-conformance portfolio for {@code LAKE}.
 *
 * <p>Everything here is measured from the <em>final canonical block stream</em> of a published
 * Release — the tiles an operator places — against the basin declared by the published Blueprint.
 * The plan artifacts ({@code hydrology/validation.json}) are deliberately not read: they are the
 * portfolio's separate plan-only column, and V2-19-01 forbids one standing in for the other.</p>
 *
 * <p>Three properties are measured, matching the Task's obligation (a lake has no source/mouth
 * reachability the way a river does):</p>
 * <ol>
 *   <li><b>basin depth</b> — each basin column is cut to the bed {@link LakeGeneratorV2} declares for
 *       that cell and carries fluid up to the declared water surface, with air (never solid) up to
 *       the old surface;</li>
 *   <li><b>water continuity</b> — the basin's water cells form one 4-connected component in XZ;</li>
 *   <li><b>leak envelope</b> — no water block of the basin touches a non-basin air block at the same
 *       height, and every off-basin neighbour column is untouched solid at the water surface.</li>
 * </ol>
 */
final class LakeBlockConformanceV2 {
    private LakeBlockConformanceV2() {
    }

    private static final int SCALE = 1_000_000;

    /** Pure measurement record: the same Release and Blueprint always yield the same values. */
    record MeasurementsV2(
            String featureId,
            int basinCells,
            int basinCellsAtDeclaredBedDepth,
            int basinCellsOpenAbove,
            int waterCells,
            int waterComponentCount,
            int leakCells,
            int envelopeCells,
            int envelopeCellsSolidAtWaterSurface
    ) {
    }

    /** Measures the first declared basin of a published hydrology Release. */
    static MeasurementsV2 measure(Path releaseDirectory, WorldBlueprintV2 blueprint) throws IOException {
        List<LakePlanV2> plans = blueprint.lakePlans();
        if (plans.isEmpty()) {
            throw new IOException("published Release declares no lake basin to measure");
        }
        LakePlanV2 plan = plans.getFirst();
        LakeGeneratorV2 generator = new LakeGeneratorV2(plan);
        FeatureMaterializationV2.FinalBlockStreamV2 blocks =
                FeatureMaterializationV2.readFinalBlockStream(releaseDirectory);
        int width = blocks.width();
        int length = blocks.length();

        boolean[] basin = new boolean[width * length];
        int[] bedY = new int[width * length];
        int[] waterSurfaceY = new int[width * length];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                LakeGeneratorV2.LakeSample sample = generator.sampleAt(x, z, index -> false);
                if (sample.basinMask() != 1) {
                    continue;
                }
                int index = z * width + x;
                basin[index] = true;
                bedY[index] = Math.floorDiv(sample.floorHeightMillionths(), SCALE);
                waterSurfaceY[index] = Math.floorDiv(sample.surfaceMillionths(), SCALE);
            }
        }

        int basinCells = 0;
        int atDeclaredDepth = 0;
        int openAbove = 0;
        int waterCells = 0;
        int leakCells = 0;
        boolean[] waterCell = new boolean[width * length];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (!basin[index]) {
                    continue;
                }
                basinCells++;
                int bed = bedY[index];
                int water = waterSurfaceY[index];
                boolean bedIsSolid = blocks.isSolid(x, bed, z);
                boolean waterColumnIsFluid = true;
                for (int y = bed + 1; y <= water; y++) {
                    if (blocks.isFluid(x, y, z)) {
                        waterCells++;
                    } else {
                        waterColumnIsFluid = false;
                    }
                }
                if (waterColumnIsFluid && water > bed) {
                    waterCell[index] = true;
                }
                // The carve must leave the basin open to the sky: a solid block above the water
                // surface would mean the bed was cut but the terrain above it was left as a roof.
                boolean carvedAbove = true;
                for (int y = water + 1; y <= blocks.maxY(); y++) {
                    if (!blocks.isAir(x, y, z)) {
                        carvedAbove = false;
                    }
                }
                if (carvedAbove) {
                    openAbove++;
                }
                if (bedIsSolid && waterColumnIsFluid && carvedAbove) {
                    atDeclaredDepth++;
                }
                for (int y = bed + 1; y <= water; y++) {
                    if (!blocks.isFluid(x, y, z)) {
                        continue;
                    }
                    leakCells += leaksAt(blocks, basin, width, length, x, y, z) ? 1 : 0;
                }
            }
        }

        int envelopeCells = 0;
        int envelopeSolid = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (basin[index] || !touchesBasin(basin, width, length, x, z)) {
                    continue;
                }
                envelopeCells++;
                int water = neighbourWaterSurface(basin, waterSurfaceY, width, length, x, z);
                if (blocks.isSolid(x, water, z)) {
                    envelopeSolid++;
                }
            }
        }

        Components components = Components.of(waterCell, width, length);
        return new MeasurementsV2(plan.featureId(), basinCells, atDeclaredDepth, openAbove, waterCells,
                components.count(), leakCells, envelopeCells, envelopeSolid);
    }

    /**
     * A water block leaks when a horizontally adjacent cell outside the basin is not solid at the
     * same height: the containment envelope is the terrain around the basin, and the release
     * boundary is deliberately not treated as one.
     */
    private static boolean leaksAt(
            FeatureMaterializationV2.FinalBlockStreamV2 blocks,
            boolean[] basin,
            int width,
            int length,
            int x,
            int y,
            int z
    ) {
        int[][] neighbours = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
        for (int[] neighbour : neighbours) {
            int nx = neighbour[0];
            int nz = neighbour[1];
            if (nx < 0 || nz < 0 || nx >= width || nz >= length || basin[nz * width + nx]) {
                continue;
            }
            if (!blocks.isSolid(nx, y, nz)) {
                return true;
            }
        }
        return false;
    }

    private static boolean touchesBasin(boolean[] basin, int width, int length, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx >= width || nz >= length) {
                    continue;
                }
                if (basin[nz * width + nx]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Highest water surface among the basin cells this envelope cell touches. */
    private static int neighbourWaterSurface(
            boolean[] basin, int[] waterSurfaceY, int width, int length, int x, int z
    ) {
        int highest = Integer.MIN_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx >= width || nz >= length) {
                    continue;
                }
                int index = nz * width + nx;
                if (basin[index]) {
                    highest = Math.max(highest, waterSurfaceY[index]);
                }
            }
        }
        return highest;
    }

    /** 4-connected components of the measured water cells. */
    private static final class Components {
        private final int[] component;
        private final int count;

        private Components(int[] component, int count) {
            this.component = component;
            this.count = count;
        }

        static Components of(boolean[] cells, int width, int length) {
            int[] component = new int[cells.length];
            int count = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int start = 0; start < cells.length; start++) {
                if (!cells[start] || component[start] != 0) {
                    continue;
                }
                count++;
                component[start] = count;
                queue.add(start);
                while (!queue.isEmpty()) {
                    int cell = queue.poll();
                    int x = cell % width;
                    int z = cell / width;
                    enqueue(cells, component, queue, width, length, x + 1, z, count);
                    enqueue(cells, component, queue, width, length, x - 1, z, count);
                    enqueue(cells, component, queue, width, length, x, z + 1, count);
                    enqueue(cells, component, queue, width, length, x, z - 1, count);
                }
            }
            return new Components(component, count);
        }

        private static void enqueue(boolean[] cells, int[] component, ArrayDeque<Integer> queue,
                                    int width, int length, int x, int z, int label) {
            if (x < 0 || z < 0 || x >= width || z >= length) {
                return;
            }
            int index = z * width + x;
            if (cells[index] && component[index] == 0) {
                component[index] = label;
                queue.add(index);
            }
        }

        int count() {
            return count;
        }
    }
}
