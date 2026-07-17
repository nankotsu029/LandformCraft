package com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TidalChannelPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

/** Fixed-point TIDAL_CHANNEL_NETWORK rasterizer with stable global-X/Z sampling. */
public final class TidalChannelGeneratorV2 {
    public static final String VERSION = "hydrology-tidal-fixed-v1";
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 12L * 1024L * 1024L;

    private final TidalChannelPlanV2 plan;
    private final List<EdgeRaster> edges;

    public TidalChannelGeneratorV2(TidalChannelPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        TidalGraphValidatorV2.requireValid(plan, plan.edges());
        this.edges = plan.edges().stream().map(EdgeRaster::from).toList();
    }

    public TidalChannelPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public TidalSample sampleAt(int globalX, int globalZ, IntPredicate hardLandConflict) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        if (globalX < 0 || globalX >= width() || globalZ < 0 || globalZ >= length()) {
            throw new TidalGenerationException("v2.tidal-bounds", "sample is outside release-local bounds");
        }
        try {
            NearestEdge nearest = nearestEdge(globalX, globalZ);
            boolean channel = nearest.distanceMillionths()
                    <= Math.multiplyExact((long) nearest.edge().halfWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
            if (channel && hardLandConflict.test(index(globalX, globalZ))) {
                throw new TidalGenerationException(
                        "v2.tidal-hard-no-data",
                        "HARD land constraint conflicts with a tidal channel");
            }
            int depth = channel
                    ? Math.multiplyExact(nearest.edge().depthBlocks(), TerrainIntentV2.FIXED_SCALE)
                    : NO_DATA;
            return new TidalSample(
                    channel ? 1 : 0,
                    channel ? nearest.index() + 1 : 0,
                    depth,
                    channel ? 1 : 0);
        } catch (ArithmeticException exception) {
            throw new TidalGenerationException("v2.tidal-overflow", "tidal sample arithmetic overflow", exception);
        }
    }

    public TidalWindowV2 renderWindow(
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int haloXZ,
            IntPredicate hardLandConflict,
            CancellationToken token
    ) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        Objects.requireNonNull(token, "token");
        if (haloXZ < 0 || haloXZ > plan.supportRadiusXZ()) {
            throw new TidalGenerationException("v2.tidal-halo", "requested halo exceeds plan support radius");
        }
        if (coreWidth < 1 || coreLength < 1) {
            throw new TidalGenerationException("v2.tidal-window", "tidal core window must be positive");
        }
        try {
            int originX = Math.subtractExact(coreOriginX, haloXZ);
            int originZ = Math.subtractExact(coreOriginZ, haloXZ);
            int windowWidth = Math.addExact(coreWidth, Math.multiplyExact(haloXZ, 2));
            int windowLength = Math.addExact(coreLength, Math.multiplyExact(haloXZ, 2));
            int endX = Math.addExact(originX, windowWidth);
            int endZ = Math.addExact(originZ, windowLength);
            if (originX < 0 || originZ < 0 || endX > width() || endZ > length()) {
                throw new TidalGenerationException("v2.tidal-window", "tidal window exceeds world bounds");
            }
            long retained = estimateWindowRetainedBytes(windowWidth, windowLength);
            if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
                throw new TidalGenerationException("v2.tidal-budget", "tidal window exceeds retained-memory budget");
            }
            int cells = Math.multiplyExact(windowWidth, windowLength);
            int[][] fields = new int[TidalField.values().length][cells];
            for (int localZ = 0; localZ < windowLength; localZ++) {
                token.throwIfCancellationRequested();
                for (int localX = 0; localX < windowWidth; localX++) {
                    TidalSample sample = sampleAt(originX + localX, originZ + localZ, hardLandConflict);
                    int offset = localZ * windowWidth + localX;
                    for (TidalField field : TidalField.values()) {
                        fields[field.ordinal()][offset] = sample.rawValue(field);
                    }
                }
            }
            return new TidalWindowV2(originX, originZ, windowWidth, windowLength, fields, retained);
        } catch (ArithmeticException exception) {
            throw new TidalGenerationException("v2.tidal-overflow", "tidal window arithmetic overflow", exception);
        }
    }

    public Map<TidalField, String> fieldChecksums(IntPredicate hardLandConflict, CancellationToken token) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardLandConflict), token);
    }

    public Map<TidalField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<TidalField, MessageDigest> digests = new EnumMap<>(TidalField.class);
        for (TidalField field : TidalField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                TidalSample sample = Objects.requireNonNull(source.sampleAt(x, z), "tidal sample");
                for (TidalField field : TidalField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<TidalField, String> result = new EnumMap<>(TidalField.class);
        for (TidalField field : TidalField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** EXPERIMENTAL V2-3-08 hard-metric hook over frozen graph and generated fields. */
    public TidalMetrics evaluate(IntPredicate hardLandConflict, CancellationToken token) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        Objects.requireNonNull(token, "token");
        long channelCells = 0L;
        long depthCells = 0L;
        boolean marineReachable = true;
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                TidalSample sample = sampleAt(x, z, hardLandConflict);
                if (sample.channelMask() == 1) {
                    channelCells++;
                    if (sample.depthCorridorMillionths() != NO_DATA) depthCells++;
                    if (sample.marineConnection() != 1) marineReachable = false;
                }
            }
        }
        for (TidalChannelPlanV2.ChannelNode node : plan.nodes()) {
            if (!node.marine()) continue;
            int x = roundBlock(node.point().xMillionths());
            int z = roundBlock(node.point().zMillionths());
            if (hardLandConflict.test(index(x, z)) || sampleAt(x, z, hardLandConflict).channelMask() != 1) {
                marineReachable = false;
                break;
            }
        }
        return new TidalMetrics(
                plan.edges().size(),
                plan.nodes().stream().filter(TidalChannelPlanV2.ChannelNode::marine).count(),
                marineReachable,
                channelCells,
                depthCells,
                plan.estimatedRasterWorkUnits());
    }

    public static long estimateWindowRetainedBytes(int windowWidth, int windowLength) {
        if (windowWidth < 1 || windowLength < 1) {
            throw new IllegalArgumentException("tidal window dimensions must be positive");
        }
        long cells = Math.multiplyExact((long) windowWidth, windowLength);
        return Math.addExact(Math.multiplyExact(cells, TidalField.values().length * 4L), 64L * 1024L);
    }

    private NearestEdge nearestEdge(int x, int z) {
        long best = Long.MAX_VALUE;
        int bestIndex = 0;
        for (int index = 0; index < edges.size(); index++) {
            long distance = distanceToPathMillionths(x, z, edges.get(index));
            if (distance < best || (distance == best && index < bestIndex)) {
                best = distance;
                bestIndex = index;
            }
        }
        return new NearestEdge(plan.edges().get(bestIndex), bestIndex, best);
    }

    private static long distanceToPathMillionths(int x, int z, EdgeRaster edge) {
        long result = Long.MAX_VALUE;
        for (SegmentRaster segment : edge.segments()) {
            result = Math.min(result, distanceToSegmentMillionths(x, z, segment));
        }
        return result;
    }

    private static long distanceToSegmentMillionths(int x, int z, SegmentRaster segment) {
        long dx = segment.toX() - segment.fromX();
        long dz = segment.toZ() - segment.fromZ();
        long px = x - segment.fromX();
        long pz = z - segment.fromZ();
        long denominator = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long numerator = Math.addExact(Math.multiplyExact(px, dx), Math.multiplyExact(pz, dz));
        long projection = denominator == 0L ? 0L : Math.max(0L, Math.min(
                TerrainIntentV2.FIXED_SCALE,
                TidalFixedMathV2.roundDivide(Math.multiplyExact(numerator, TerrainIntentV2.FIXED_SCALE), denominator)));
        long projectedX = Math.addExact(Math.multiplyExact((long) segment.fromX(), TerrainIntentV2.FIXED_SCALE),
                Math.multiplyExact(dx, projection));
        long projectedZ = Math.addExact(Math.multiplyExact((long) segment.fromZ(), TerrainIntentV2.FIXED_SCALE),
                Math.multiplyExact(dz, projection));
        long cellX = Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE);
        long cellZ = Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE);
        return TidalFixedMathV2.hypotMillionths(cellX - projectedX, cellZ - projectedZ);
    }

    private int index(int x, int z) {
        return Math.addExact(Math.multiplyExact(z, width()), x);
    }

    private static int roundBlock(long millionths) {
        return Math.toIntExact(TidalFixedMathV2.roundDivide(millionths, TerrainIntentV2.FIXED_SCALE));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    public enum TidalField {
        CHANNEL_MASK,
        BRANCH_INDEX,
        DEPTH_CORRIDOR,
        MARINE_CONNECTION
    }

    @FunctionalInterface
    public interface CellSource {
        TidalSample sampleAt(int globalX, int globalZ);
    }

    public record TidalSample(
            int channelMask,
            int branchIndex,
            int depthCorridorMillionths,
            int marineConnection
    ) {
        int rawValue(TidalField field) {
            return switch (field) {
                case CHANNEL_MASK -> channelMask;
                case BRANCH_INDEX -> branchIndex;
                case DEPTH_CORRIDOR -> depthCorridorMillionths;
                case MARINE_CONNECTION -> marineConnection;
            };
        }
    }

    public record TidalMetrics(
            int branchCount,
            long marineNodeCount,
            boolean allMarineEndpointsConnected,
            long channelCells,
            long depthCorridorCells,
            long estimatedRasterWorkUnits
    ) {
    }

    private record NearestEdge(TidalChannelPlanV2.ChannelEdge edge, int index, long distanceMillionths) {
    }

    private record EdgeRaster(List<SegmentRaster> segments) {
        static EdgeRaster from(TidalChannelPlanV2.ChannelEdge edge) {
            List<SegmentRaster> segments = new java.util.ArrayList<>(edge.path().size() - 1);
            for (int index = 0; index < edge.path().size() - 1; index++) {
                TidalChannelPlanV2.ChannelPoint from = edge.path().get(index);
                TidalChannelPlanV2.ChannelPoint to = edge.path().get(index + 1);
                segments.add(new SegmentRaster(
                        roundBlock(from.xMillionths()),
                        roundBlock(from.zMillionths()),
                        roundBlock(to.xMillionths()),
                        roundBlock(to.zMillionths())));
            }
            return new EdgeRaster(List.copyOf(segments));
        }
    }

    private record SegmentRaster(int fromX, int fromZ, int toX, int toZ) {
    }
}
