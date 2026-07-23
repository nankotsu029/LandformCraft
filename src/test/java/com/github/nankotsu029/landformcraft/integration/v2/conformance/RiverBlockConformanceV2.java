package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;

/**
 * V2-19-05 block-column of the intent-conformance portfolio for {@code RIVER} /
 * {@code MEANDERING_RIVER}.
 *
 * <p>Everything here is measured from the <em>final canonical block stream</em> of a published
 * Release — the tiles an operator places — against the reach declared by the published Blueprint.
 * The plan artifacts ({@code hydrology/validation.json}, the routing bundle, the reconciliation
 * artifact) are deliberately not read: they are the portfolio's separate plan-only column, and
 * V2-19-01 forbids one standing in for the other.</p>
 *
 * <p>Four properties are measured, matching the Task's obligation:</p>
 * <ol>
 *   <li><b>bed depth</b> — each channel column is cut to the declared bed and carries exactly the
 *       declared water depth above it, with air (never solid) up to the old surface;</li>
 *   <li><b>water continuity</b> — the channel's water cells form one 4-connected component in XZ;
 *       a stepped bed makes 3D face-connectivity the wrong question for a 2.5D reach, so continuity
 *       is measured on the projection the reach is defined in;</li>
 *   <li><b>source→mouth reachability</b> — the declared source cell reaches the declared mouth cell
 *       inside that water component;</li>
 *   <li><b>leak envelope</b> — no water block of the channel touches a non-channel air block at the
 *       same height, and every off-channel neighbour column is untouched solid at the water surface.</li>
 * </ol>
 */
final class RiverBlockConformanceV2 {
    private RiverBlockConformanceV2() {
    }

    private static final int SCALE = 1_000_000;

    /** Pure measurement record: the same Release and Blueprint always yield the same values. */
    record MeasurementsV2(
            String featureId,
            int channelCells,
            int channelCellsAtDeclaredBedDepth,
            int channelCellsOpenAbove,
            int waterCells,
            int waterComponentCount,
            boolean sourceToMouthReachable,
            int leakCells,
            int envelopeCells,
            int envelopeCellsSolidAtWaterSurface,
            int declaredWaterDepthBlocks
    ) {
    }

    /** Measures the first declared reach of a published hydrology Release. */
    static MeasurementsV2 measure(Path releaseDirectory, WorldBlueprintV2 blueprint) throws IOException {
        List<MeanderingRiverPlanV2> plans = blueprint.meanderingRiverPlans();
        if (plans.isEmpty()) {
            throw new IOException("published Release declares no river reach to measure");
        }
        MeanderingRiverPlanV2 plan = plans.getFirst();
        MeanderingRiverGeneratorV2 generator = new MeanderingRiverGeneratorV2(plan);
        FeatureMaterializationV2.FinalBlockStreamV2 blocks =
                FeatureMaterializationV2.readFinalBlockStream(releaseDirectory);
        int width = blocks.width();
        int length = blocks.length();

        boolean[] channel = new boolean[width * length];
        int[] bedY = new int[width * length];
        int[] waterSurfaceY = new int[width * length];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                MeanderingRiverGeneratorV2.RiverSample sample = generator.sampleAt(x, z, index -> false);
                if (sample.channelMask() != 1) {
                    continue;
                }
                int index = z * width + x;
                channel[index] = true;
                bedY[index] = Math.floorDiv(sample.bedElevationMillionths(), SCALE);
                waterSurfaceY[index] = Math.floorDiv(sample.waterSurfaceMillionths(), SCALE);
            }
        }

        int declaredDepth = Math.toIntExact(plan.waterDepthMillionths() / SCALE);
        int channelCells = 0;
        int atDeclaredDepth = 0;
        int openAbove = 0;
        int waterCells = 0;
        int leakCells = 0;
        boolean[] waterCell = new boolean[width * length];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (!channel[index]) {
                    continue;
                }
                channelCells++;
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
                // The carve must leave the channel open to the sky: a solid block above the water
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
                if (bedIsSolid && waterColumnIsFluid && carvedAbove && water - bed == declaredDepth) {
                    atDeclaredDepth++;
                }
                for (int y = bed + 1; y <= water; y++) {
                    if (!blocks.isFluid(x, y, z)) {
                        continue;
                    }
                    leakCells += leaksAt(blocks, channel, width, length, x, y, z) ? 1 : 0;
                }
            }
        }

        int envelopeCells = 0;
        int envelopeSolid = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (channel[index] || !touchesChannel(channel, width, length, x, z)) {
                    continue;
                }
                envelopeCells++;
                int water = neighbourWaterSurface(channel, waterSurfaceY, width, length, x, z);
                if (blocks.isSolid(x, water, z)) {
                    envelopeSolid++;
                }
            }
        }

        Components components = Components.of(waterCell, width, length);
        boolean reachable = components.connects(
                cellOf(plan.centerline().getFirst(), width, length),
                cellOf(plan.centerline().getLast(), width, length));
        return new MeasurementsV2(plan.featureId(), channelCells, atDeclaredDepth, openAbove, waterCells,
                components.count(), reachable, leakCells, envelopeCells, envelopeSolid, declaredDepth);
    }

    /**
     * A water block leaks when a horizontally adjacent cell outside the channel is not solid at the
     * same height: the containment envelope is the terrain around the channel, and the release
     * boundary is deliberately not treated as one.
     */
    private static boolean leaksAt(
            FeatureMaterializationV2.FinalBlockStreamV2 blocks,
            boolean[] channel,
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
            if (nx < 0 || nz < 0 || nx >= width || nz >= length || channel[nz * width + nx]) {
                continue;
            }
            if (!blocks.isSolid(nx, y, nz)) {
                return true;
            }
        }
        return false;
    }

    private static boolean touchesChannel(boolean[] channel, int width, int length, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx >= width || nz >= length) {
                    continue;
                }
                if (channel[nz * width + nx]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Highest water surface among the channel cells this envelope cell touches. */
    private static int neighbourWaterSurface(
            boolean[] channel, int[] waterSurfaceY, int width, int length, int x, int z
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
                if (channel[index]) {
                    highest = Math.max(highest, waterSurfaceY[index]);
                }
            }
        }
        return highest;
    }

    private static int cellOf(MeanderingRiverPlanV2.CenterlineSample sample, int width, int length) {
        int x = clamp(Math.floorDiv(sample.xMillionths(), (long) SCALE), width);
        int z = clamp(Math.floorDiv(sample.zMillionths(), (long) SCALE), length);
        return z * width + x;
    }

    private static int clamp(long value, int extent) {
        return (int) Math.max(0, Math.min(extent - 1, value));
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

        boolean connects(int from, int to) {
            return component[from] != 0 && component[from] == component[to];
        }
    }
}
