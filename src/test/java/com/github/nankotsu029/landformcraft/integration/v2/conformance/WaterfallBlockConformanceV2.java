package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.WaterfallGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;

/**
 * V2-15-13 block-column of the intent-conformance portfolio for {@code WATERFALL}.
 *
 * <p>Everything here is measured from the <em>final canonical block stream</em> of a published
 * Release — the tiles an operator places — against the fall declared by the published Blueprint. The
 * plan artifacts ({@code hydrology/validation.json}) are deliberately not read: they are the
 * portfolio's separate plan-only column, and V2-19-01 forbids one standing in for the other.</p>
 *
 * <p>Four properties are measured, and together they distinguish a real fall from "a pond happened to
 * appear next to a river":</p>
 * <ol>
 *   <li><b>basin depth</b> — each plunge-basin column is cut to the pool floor
 *       {@link WaterfallGeneratorV2} declares and carries fluid up to the declared base bed, with air
 *       (never solid) above it;</li>
 *   <li><b>water continuity</b> — the basin's water cells form one 4-connected component in XZ;</li>
 *   <li><b>fall head</b> — every host-channel column adjacent to the basin stands with its bed above
 *       the pool's water surface, so the basin really is the foot of a descent rather than a
 *       widening of the channel, and the measured head equals the declared drop;</li>
 *   <li><b>leak envelope</b> — no basin water block touches a non-basin, non-host-channel air block
 *       at the same height, and every off-basin envelope column is solid at the water surface. The
 *       host channel is excluded by contract: it is the opening the fall arrives through.</li>
 * </ol>
 */
final class WaterfallBlockConformanceV2 {
    private WaterfallBlockConformanceV2() {
    }

    private static final int SCALE = 1_000_000;

    /** Pure measurement record: the same Release and Blueprint always yield the same values. */
    record MeasurementsV2(
            String featureId,
            int declaredDropBlocks,
            int basinCells,
            int basinCellsAtDeclaredFloorDepth,
            int basinCellsOpenAbove,
            int waterCells,
            int waterComponentCount,
            int waterComponentCountWithHostChannel,
            int hostChannelContactCells,
            int hostChannelContactCellsAbovePool,
            int measuredHeadBlocks,
            int leakCells,
            int envelopeCells,
            int envelopeCellsSolidAtWaterSurface
    ) {
    }

    /** Measures the first declared fall of a published hydrology Release. */
    static MeasurementsV2 measure(Path releaseDirectory, WorldBlueprintV2 blueprint) throws IOException {
        List<WaterfallPlanV2> plans = blueprint.waterfallPlans();
        if (plans.isEmpty()) {
            throw new IOException("published Release declares no waterfall to measure");
        }
        WaterfallPlanV2 plan = plans.getFirst();
        WaterfallGeneratorV2 generator = new WaterfallGeneratorV2(plan);
        List<MeanderingRiverGeneratorV2> hosts = blueprint.meanderingRiverPlans().stream()
                .map(MeanderingRiverGeneratorV2::new)
                .toList();
        if (hosts.isEmpty()) {
            throw new IOException("published Release declares a waterfall without its ON_PATH_OF host");
        }
        FeatureMaterializationV2.FinalBlockStreamV2 blocks =
                FeatureMaterializationV2.readFinalBlockStream(releaseDirectory);
        int width = blocks.width();
        int length = blocks.length();

        boolean[] hostChannel = new boolean[width * length];
        int[] hostBedY = new int[width * length];
        for (MeanderingRiverGeneratorV2 host : hosts) {
            for (int z = 0; z < length && z < host.length(); z++) {
                for (int x = 0; x < width && x < host.width(); x++) {
                    MeanderingRiverGeneratorV2.RiverSample sample = host.sampleAt(x, z, index -> false);
                    if (sample.channelMask() != 1) {
                        continue;
                    }
                    int index = z * width + x;
                    if (!hostChannel[index]) {
                        hostChannel[index] = true;
                        hostBedY[index] = Math.floorDiv(sample.bedElevationMillionths(), SCALE);
                    }
                }
            }
        }

        boolean[] basin = new boolean[width * length];
        int[] floorY = new int[width * length];
        int[] waterSurfaceY = new int[width * length];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (hostChannel[index]) {
                    continue;
                }
                WaterfallGeneratorV2.WaterfallSample sample = generator.sampleAt(x, z, cell -> false);
                if (sample.plungePoolMask() != 1 || sample.lipMask() == 1
                        || !downstreamOfCrest(plan, x, z)) {
                    continue;
                }
                basin[index] = true;
                floorY[index] = Math.floorDiv(sample.plungePoolFloor(), SCALE);
                waterSurfaceY[index] = Math.floorDiv(sample.baseElevation(), SCALE);
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
                int floor = floorY[index];
                int water = waterSurfaceY[index];
                boolean floorIsSolid = blocks.isSolid(x, floor, z);
                boolean waterColumnIsFluid = true;
                for (int y = floor + 1; y <= water; y++) {
                    if (blocks.isFluid(x, y, z)) {
                        waterCells++;
                    } else {
                        waterColumnIsFluid = false;
                    }
                }
                if (waterColumnIsFluid && water > floor) {
                    waterCell[index] = true;
                }
                boolean carvedAbove = true;
                for (int y = water + 1; y <= blocks.maxY(); y++) {
                    if (!blocks.isAir(x, y, z)) {
                        carvedAbove = false;
                    }
                }
                if (carvedAbove) {
                    openAbove++;
                }
                if (floorIsSolid && waterColumnIsFluid && carvedAbove) {
                    atDeclaredDepth++;
                }
                for (int y = floor + 1; y <= water; y++) {
                    if (!blocks.isFluid(x, y, z)) {
                        continue;
                    }
                    leakCells += leaksAt(blocks, basin, hostChannel, width, length, x, y, z) ? 1 : 0;
                }
            }
        }

        int contactCells = 0;
        int contactAbovePool = 0;
        int minimumHead = Integer.MAX_VALUE;
        int envelopeCells = 0;
        int envelopeSolid = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (basin[index] || !touchesBasin(basin, width, length, x, z)) {
                    continue;
                }
                int water = neighbourWaterSurface(basin, waterSurfaceY, width, length, x, z);
                if (hostChannel[index]) {
                    contactCells++;
                    if (hostBedY[index] > water) {
                        contactAbovePool++;
                        minimumHead = Math.min(minimumHead, hostBedY[index] - water);
                    }
                    continue;
                }
                envelopeCells++;
                if (blocks.isSolid(x, water, z)) {
                    envelopeSolid++;
                }
            }
        }

        // Hydrological continuity of the whole fall: the basin's own water is split into its two
        // banks by the host channel running through it, and the two banks are joined through the
        // host's own water. Measuring the union proves the pool is continuous with the reach that
        // feeds it rather than an isolated pond that happens to sit beside it.
        boolean[] waterWithHost = new boolean[width * length];
        for (int index = 0; index < waterWithHost.length; index++) {
            waterWithHost[index] = waterCell[index]
                    || (hostChannel[index] && columnCarriesFluid(blocks, index % width, index / width));
        }

        Components components = Components.of(waterCell, width, length);
        Components withHost = Components.of(waterWithHost, width, length);
        return new MeasurementsV2(
                plan.featureId(),
                plan.selectedDropBlocks(),
                basinCells, atDeclaredDepth, openAbove, waterCells, components.count(),
                withHost.count(),
                contactCells, contactAbovePool,
                minimumHead == Integer.MAX_VALUE ? 0 : minimumHead,
                leakCells, envelopeCells, envelopeSolid);
    }

    private static boolean columnCarriesFluid(
            FeatureMaterializationV2.FinalBlockStreamV2 blocks, int x, int z
    ) {
        for (int y = blocks.minY(); y <= blocks.maxY(); y++) {
            if (blocks.isFluid(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** The same downstream half-plane clip the production freeze applies at the lip crest. */
    private static boolean downstreamOfCrest(WaterfallPlanV2 plan, int globalX, int globalZ) {
        long cellX = (long) globalX * SCALE + SCALE / 2L;
        long cellZ = (long) globalZ * SCALE + SCALE / 2L;
        long fallX = plan.baseXMillionths() - plan.lipXMillionths();
        long fallZ = plan.baseZMillionths() - plan.lipZMillionths();
        long offsetX = Math.floorDiv(cellX - plan.lipXMillionths(), 1_000L);
        long offsetZ = Math.floorDiv(cellZ - plan.lipZMillionths(), 1_000L);
        return fallX * offsetX + fallZ * offsetZ >= 0L;
    }

    /**
     * A water block leaks when a horizontally adjacent cell outside the basin and outside the host
     * channel is not solid at the same height. The host channel is the declared opening the fall
     * arrives through; the release boundary is deliberately not treated as a containment surface.
     */
    private static boolean leaksAt(
            FeatureMaterializationV2.FinalBlockStreamV2 blocks,
            boolean[] basin,
            boolean[] hostChannel,
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
            if (nx < 0 || nz < 0 || nx >= width || nz >= length) {
                continue;
            }
            int index = nz * width + nx;
            if (basin[index] || hostChannel[index]) {
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

    /** Highest pool water surface among the basin cells this neighbour touches. */
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
        private final int count;

        private Components(int count) {
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
            return new Components(count);
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
