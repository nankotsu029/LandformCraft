package com.github.nankotsu029.landformcraft.generator.v2.hydrology.core;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyFlowDirectionV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Integer-only global priority-flood routing with stable cell-ID tie-breaks. */
public final class DeterministicHydrologyRoutingSolverV2 {
    private static final int BLOCKED = Integer.MIN_VALUE;
    private static final int MINIMUM_ELEVATION = -512_000_000;
    private static final int MAXIMUM_ELEVATION = 1_024_000_000;
    private static final int CANCEL_INTERVAL_MASK = 4_095;
    private static final byte[] SURFACE_DOMAIN =
            "HYDROLOGY_PROVISIONAL_SURFACE_V1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FIELD_DOMAIN = "LFC_GRID_SEMANTIC_V1".getBytes(StandardCharsets.US_ASCII);
    private static final List<HydrologyFlowDirectionV2> NEIGHBORS = List.of(
            HydrologyFlowDirectionV2.NORTH,
            HydrologyFlowDirectionV2.NORTH_EAST,
            HydrologyFlowDirectionV2.EAST,
            HydrologyFlowDirectionV2.SOUTH_EAST,
            HydrologyFlowDirectionV2.SOUTH,
            HydrologyFlowDirectionV2.SOUTH_WEST,
            HydrologyFlowDirectionV2.WEST,
            HydrologyFlowDirectionV2.NORTH_WEST);

    public HydrologyRoutingResultV2 solve(
            HydrologyRoutingRequestV2 request,
            HydrologyRoutingRequestV2.ExecutionProfile profile,
            CancellationToken token
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(token, "token");
        token.throwIfCancellationRequested();

        int width = request.width();
        int length = request.length();
        int cellCount = Math.multiplyExact(width, length);
        preflight(request, cellCount);
        int[] surface = snapshotSurface(request, profile, token);
        String surfaceChecksum = surfaceChecksum(surface, width, length, token);
        long routableCells = 0L;
        for (int index = 0; index < surface.length; index++) {
            if ((index & CANCEL_INTERVAL_MASK) == 0) token.throwIfCancellationRequested();
            if (surface[index] != BLOCKED) routableCells++;
        }
        if (routableCells == 0L) {
            throw failure("v2.hydrology-routing-unroutable", "provisional surface has no routable cells");
        }

        byte[] direction = new byte[cellCount];
        Arrays.fill(direction, (byte) HydrologyFlowDirectionV2.NO_DATA.code());
        int[] filledElevation = new int[cellCount];
        Arrays.fill(filledElevation, BLOCKED);
        int[] basin = new int[cellCount];
        int[] visitOrder = new int[cellCount];
        int[] accumulation = new int[cellCount];
        PrimitiveCellHeap heap = new PrimitiveCellHeap(cellCount, filledElevation);

        List<HydrologyRoutingArtifactV2.Outlet> outlets = request.outletCandidates();
        for (int index = 0; index < outlets.size(); index++) {
            HydrologyRoutingArtifactV2.Outlet outlet = outlets.get(index);
            int cellId = outlet.cellId(width);
            if (surface[cellId] == BLOCKED) {
                String rule = outlet.kind() == HydrologyRoutingArtifactV2.OutletKind.HARD
                        ? "v2.hydrology-routing-hard-outlet"
                        : "v2.hydrology-routing-unroutable";
                throw failure(rule, "routing outlet lies on a non-routable provisional-surface cell");
            }
            filledElevation[cellId] = surface[cellId];
            basin[cellId] = index + 1;
            direction[cellId] = (byte) HydrologyFlowDirectionV2.TERMINAL.code();
            heap.push(cellId);
        }

        int visited = 0;
        long neighborEvaluations = 0L;
        while (!heap.isEmpty()) {
            if ((visited & CANCEL_INTERVAL_MASK) == 0) token.throwIfCancellationRequested();
            int current = heap.pop();
            visitOrder[visited++] = current;
            int currentX = current % width;
            int currentZ = current / width;
            for (HydrologyFlowDirectionV2 neighborDirection : NEIGHBORS) {
                neighborEvaluations = Math.addExact(neighborEvaluations, 1L);
                int neighborX = currentX + neighborDirection.deltaX();
                int neighborZ = currentZ + neighborDirection.deltaZ();
                if (neighborX < 0 || neighborX >= width || neighborZ < 0 || neighborZ >= length) {
                    continue;
                }
                int neighbor = neighborZ * width + neighborX;
                if (surface[neighbor] == BLOCKED || filledElevation[neighbor] != BLOCKED) {
                    continue;
                }
                filledElevation[neighbor] = Math.max(surface[neighbor], filledElevation[current]);
                basin[neighbor] = basin[current];
                direction[neighbor] = (byte) neighborDirection.opposite().code();
                heap.push(neighbor);
            }
        }
        if (visited != routableCells) {
            throw failure("v2.hydrology-routing-unroutable",
                    "at least one routable component has no declared global outlet");
        }

        for (int index = 0; index < visited; index++) {
            accumulation[visitOrder[index]] = 1;
        }
        long accumulationEdges = 0L;
        try {
            for (int index = visited - 1; index >= 0; index--) {
                if ((index & CANCEL_INTERVAL_MASK) == 0) token.throwIfCancellationRequested();
                int cell = visitOrder[index];
                HydrologyFlowDirectionV2 flow = HydrologyFlowDirectionV2.fromCode(
                        Byte.toUnsignedInt(direction[cell]));
                if (flow == HydrologyFlowDirectionV2.TERMINAL) continue;
                int x = cell % width;
                int z = cell / width;
                int downstreamX = Math.addExact(x, flow.deltaX());
                int downstreamZ = Math.addExact(z, flow.deltaZ());
                int downstream = Math.addExact(Math.multiplyExact(downstreamZ, width), downstreamX);
                accumulation[downstream] = Math.addExact(accumulation[downstream], accumulation[cell]);
                accumulationEdges = Math.addExact(accumulationEdges, 1L);
            }
        } catch (ArithmeticException exception) {
            throw failure("v2.hydrology-routing-overflow", "routing accumulation overflow", exception);
        }

        long[] basinAreas = new long[outlets.size() + 1];
        for (int index = 0; index < cellCount; index++) {
            if ((index & CANCEL_INTERVAL_MASK) == 0) token.throwIfCancellationRequested();
            if (surface[index] != BLOCKED) {
                basinAreas[basin[index]] = Math.addExact(basinAreas[basin[index]], 1L);
            }
        }
        List<HydrologyRoutingArtifactV2.BasinSummary> basins = new ArrayList<>(outlets.size());
        for (int index = 0; index < outlets.size(); index++) {
            HydrologyRoutingArtifactV2.Outlet outlet = outlets.get(index);
            int numericId = index + 1;
            int outletCell = outlet.cellId(width);
            if (basinAreas[numericId] != accumulation[outletCell]) {
                throw failure("v2.hydrology-routing-graph",
                        "basin area does not match outlet flow accumulation");
            }
            basins.add(new HydrologyRoutingArtifactV2.BasinSummary(
                    HydrologyRoutingArtifactV2.basinId(numericId), numericId, outlet.outletId(), outletCell,
                    surface[outletCell], basinAreas[numericId], accumulation[outletCell]));
        }

        long cpuWorkUnits;
        try {
            cpuWorkUnits = Math.addExact(
                    Math.addExact(
                            Math.addExact(cellCount, neighborEvaluations),
                            Math.addExact(heap.comparisons(), accumulationEdges)),
                    Math.multiplyExact((long) cellCount, 3L));
        } catch (ArithmeticException exception) {
            throw failure("v2.hydrology-routing-overflow", "routing work accounting overflow", exception);
        }
        long peakWorkingBytes = estimatePeakWorkingBytes(cellCount);
        long retainedResultBytes = estimateRetainedResultBytes(cellCount, basins.size());
        HydrologyRoutingResultV2.Metrics metrics = new HydrologyRoutingResultV2.Metrics(
                cellCount, routableCells, cpuWorkUnits, peakWorkingBytes, retainedResultBytes,
                heap.maximumSize(), request.resourceBudget());

        String directionChecksum = fieldSemanticChecksum(
                HydrologyRoutingArtifactV2.FLOW_DIRECTION_FIELD_ID,
                FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_DIRECTION,
                FieldArtifactDescriptorV2.FieldValueType.U8,
                width, length, 255,
                index -> Byte.toUnsignedInt(direction[index]), token);
        String accumulationChecksum = fieldSemanticChecksum(
                HydrologyRoutingArtifactV2.FLOW_ACCUMULATION_FIELD_ID,
                FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_ACCUMULATION,
                FieldArtifactDescriptorV2.FieldValueType.I32,
                width, length, 0,
                index -> accumulation[index], token);
        String graphChecksum = HydrologyRoutingArtifactV2.computeGraphChecksum(
                width, length, request.hydrologyPlan().canonicalChecksum(), surfaceChecksum,
                request.hydrologyPlan().fixedPriors().priorChecksum(), outlets, basins);
        String routingChecksum = HydrologyRoutingArtifactV2.computeRoutingChecksum(
                graphChecksum, directionChecksum, accumulationChecksum);

        return new HydrologyRoutingResultV2(
                width, length, request.hydrologyPlan().canonicalChecksum(), surfaceChecksum,
                request.hydrologyPlan().fixedPriors().priorChecksum(), outlets, basins,
                direction, accumulation, metrics, graphChecksum,
                directionChecksum, accumulationChecksum, routingChecksum);
    }

    private static int[] snapshotSurface(
            HydrologyRoutingRequestV2 request,
            HydrologyRoutingRequestV2.ExecutionProfile profile,
            CancellationToken token
    ) {
        int width = request.width();
        int length = request.length();
        int[] result = new int[Math.multiplyExact(width, length)];
        Arrays.fill(result, BLOCKED);
        List<Tile> tiles = tiles(width, length, profile.tileSize());
        if (profile.tileOrder() == HydrologyRoutingRequestV2.TileOrder.REVERSE) {
            Collections.reverse(tiles);
        }
        AtomicInteger cursor = new AtomicInteger();
        AtomicInteger threadIds = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "landformcraft-hydrology-" + threadIds.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                profile.workerCount(), profile.workerCount(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(profile.workerCount()), factory, new ThreadPoolExecutor.AbortPolicy());
        List<CompletableFuture<Void>> workers = new ArrayList<>(profile.workerCount());
        try {
            for (int worker = 0; worker < profile.workerCount(); worker++) {
                workers.add(CompletableFuture.runAsync(() -> {
                    int tileIndex;
                    while ((tileIndex = cursor.getAndIncrement()) < tiles.size()) {
                        token.throwIfCancellationRequested();
                        snapshotTile(request.surface(), tiles.get(tileIndex), width, result, token);
                    }
                }, executor));
            }
            CompletableFuture.allOf(workers.toArray(CompletableFuture[]::new)).join();
            return result;
        } catch (CompletionException exception) {
            workers.forEach(worker -> worker.cancel(true));
            Throwable cause = exception.getCause();
            if (cause instanceof CancellationException cancellation) throw cancellation;
            if (cause instanceof HydrologyRoutingException routing) throw routing;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw failure("v2.hydrology-routing-input", "failed to snapshot provisional surface", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void snapshotTile(
            ProvisionalSurfaceV2 source,
            Tile tile,
            int globalWidth,
            int[] target,
            CancellationToken token
    ) {
        for (int z = tile.originZ(); z < tile.originZ() + tile.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = tile.originX(); x < tile.originX() + tile.width(); x++) {
                if (!source.routableAt(x, z)) continue;
                long elevation = source.elevationMillionthsAt(x, z);
                int exact;
                try {
                    exact = Math.toIntExact(elevation);
                } catch (ArithmeticException exception) {
                    throw failure("v2.hydrology-routing-overflow",
                            "provisional surface elevation does not fit signed I32", exception);
                }
                if (exact < MINIMUM_ELEVATION || exact > MAXIMUM_ELEVATION) {
                    throw failure("v2.hydrology-routing-range",
                            "provisional surface elevation lies outside -512..1024 blocks");
                }
                target[z * globalWidth + x] = exact;
            }
        }
    }

    private static List<Tile> tiles(int width, int length, int tileSize) {
        List<Tile> result = new ArrayList<>();
        for (int originZ = 0; originZ < length; originZ += tileSize) {
            for (int originX = 0; originX < width; originX += tileSize) {
                result.add(new Tile(
                        originX, originZ, Math.min(tileSize, width - originX),
                        Math.min(tileSize, length - originZ)));
            }
        }
        return result;
    }

    private static String surfaceChecksum(int[] surface, int width, int length, CancellationToken token) {
        MessageDigest digest = sha256();
        digest.update(SURFACE_DOMAIN);
        updateInt(digest, width);
        updateInt(digest, length);
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                int value = surface[z * width + x];
                updateInt(digest, value == BLOCKED ? 0 : 1);
                if (value != BLOCKED) updateInt(digest, value);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String fieldSemanticChecksum(
            String fieldId,
            FieldArtifactDescriptorV2.FieldSemantic semantic,
            FieldArtifactDescriptorV2.FieldValueType valueType,
            int width,
            int length,
            int noData,
            IndexedValue values,
            CancellationToken token
    ) {
        MessageDigest digest = sha256();
        digest.update(FIELD_DOMAIN);
        updateString(digest, fieldId);
        updateInt(digest, semanticCode(semantic));
        updateInt(digest, valueTypeCode(valueType));
        updateInt(digest, width);
        updateInt(digest, length);
        updateInt(digest, 1); // RELEASE_LOCAL_XZ
        updateInt(digest, 1); // NEAREST
        updateLong(digest, 1L);
        updateLong(digest, 0L);
        updateInt(digest, 1);
        updateInt(digest, noData);
        int cells = Math.multiplyExact(width, length);
        for (int index = 0; index < cells; index++) {
            if ((index & CANCEL_INTERVAL_MASK) == 0) token.throwIfCancellationRequested();
            updateInt(digest, values.valueAt(index));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static int semanticCode(FieldArtifactDescriptorV2.FieldSemantic semantic) {
        return switch (semantic) {
            case HYDROLOGY_FLOW_DIRECTION -> 10;
            case HYDROLOGY_FLOW_ACCUMULATION -> 11;
            default -> throw new IllegalArgumentException("not a hydrology routing field semantic");
        };
    }

    private static int valueTypeCode(FieldArtifactDescriptorV2.FieldValueType valueType) {
        return switch (valueType) {
            case U8 -> 1;
            case U16 -> 2;
            case I32 -> 3;
        };
    }

    private static long estimatePeakWorkingBytes(int cells) {
        return Math.addExact(Math.multiplyExact((long) cells, 32L), 4L * 1024L * 1024L);
    }

    private static long estimateRetainedResultBytes(int cells, int basinCount) {
        return Math.addExact(
                Math.addExact(Math.multiplyExact((long) cells, 5L), Math.multiplyExact((long) basinCount, 128L)),
                64L * 1024L);
    }

    private static void preflight(HydrologyRoutingRequestV2 request, int cells) {
        var budget = request.resourceBudget();
        long requiredCpu = Math.multiplyExact((long) cells, 64L);
        long requiredWorking = estimatePeakWorkingBytes(cells);
        long requiredRetained = estimateRetainedResultBytes(cells, request.outletCandidates().size());
        long requiredArtifact = Math.addExact(Math.multiplyExact((long) cells, 5L), 64L * 1024L);
        if (requiredCpu > budget.maximumCpuWorkUnits()
                || requiredWorking > budget.maximumWorkingBytes()
                || requiredRetained > budget.maximumRetainedResultBytes()
                || requiredArtifact > budget.maximumFieldArtifactBytes()) {
            throw failure("v2.hydrology-routing-budget",
                    "global routing exceeds its declared CPU, resident, retained, or artifact budget");
        }
    }

    private static HydrologyRoutingException failure(String ruleId, String message) {
        return new HydrologyRoutingException(ruleId, message);
    }

    private static HydrologyRoutingException failure(String ruleId, String message, Throwable cause) {
        return new HydrologyRoutingException(ruleId, message, cause);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateLong(MessageDigest digest, long value) {
        updateInt(digest, (int) (value >>> 32));
        updateInt(digest, (int) value);
    }

    @FunctionalInterface
    private interface IndexedValue {
        int valueAt(int index);
    }

    private record Tile(int originX, int originZ, int width, int length) {
    }

    private static final class PrimitiveCellHeap {
        private final int[] cells;
        private final int[] priorities;
        private int size;
        private int maximumSize;
        private long comparisons;

        private PrimitiveCellHeap(int maximumCells, int[] priorities) {
            this.cells = new int[maximumCells];
            this.priorities = priorities;
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private void push(int cell) {
            if (size >= cells.length) {
                throw failure("v2.hydrology-routing-budget", "routing heap exceeds global cell budget");
            }
            int cursor = size++;
            maximumSize = Math.max(maximumSize, size);
            while (cursor > 0) {
                int parent = (cursor - 1) >>> 1;
                if (!less(cell, cells[parent])) break;
                cells[cursor] = cells[parent];
                cursor = parent;
            }
            cells[cursor] = cell;
        }

        private int pop() {
            int result = cells[0];
            int tail = cells[--size];
            if (size == 0) return result;
            int cursor = 0;
            while (true) {
                int left = cursor * 2 + 1;
                if (left >= size) break;
                int right = left + 1;
                int child = right < size && less(cells[right], cells[left]) ? right : left;
                if (!less(cells[child], tail)) break;
                cells[cursor] = cells[child];
                cursor = child;
            }
            cells[cursor] = tail;
            return result;
        }

        private boolean less(int first, int second) {
            comparisons = Math.addExact(comparisons, 1L);
            int elevation = Integer.compare(priorities[first], priorities[second]);
            return elevation < 0 || elevation == 0 && first < second;
        }

        private int maximumSize() {
            return maximumSize;
        }

        private long comparisons() {
            return comparisons;
        }
    }
}
