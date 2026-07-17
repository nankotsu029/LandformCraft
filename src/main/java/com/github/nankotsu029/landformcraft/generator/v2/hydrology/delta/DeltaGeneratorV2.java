package com.github.nankotsu029.landformcraft.generator.v2.hydrology.delta;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.DeltaPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.math.BigInteger;
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

/** Fixed-point DELTA fan/distributary rasterizer with stable global-X/Z sampling. */
public final class DeltaGeneratorV2 {
    public static final String VERSION = "hydrology-delta-fixed-v1";
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 12L * 1024L * 1024L;

    private final DeltaPlanV2 plan;
    private final List<BranchRaster> branches;

    public DeltaGeneratorV2(DeltaPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        DeltaGraphValidatorV2.requireValid(plan, plan.branches());
        this.branches = plan.branches().stream().map(BranchRaster::from).toList();
    }

    public DeltaPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public DeltaSample sampleAt(int globalX, int globalZ, IntPredicate hardLandConflict) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        if (globalX < 0 || globalX >= width() || globalZ < 0 || globalZ >= length()) {
            throw new DeltaGenerationException("v2.delta-bounds", "sample is outside release-local bounds");
        }
        try {
            long cellX = Math.multiplyExact((long) globalX, TerrainIntentV2.FIXED_SCALE);
            long cellZ = Math.multiplyExact((long) globalZ, TerrainIntentV2.FIXED_SCALE);
            boolean fan = pointInRing(cellX, cellZ, plan.fanRing());
            NearestBranch nearest = nearestBranch(globalX, globalZ);
            boolean channel = fan && nearest.distanceMillionths()
                    <= Math.multiplyExact((long) nearest.branch().halfWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
            if (channel && hardLandConflict.test(index(globalX, globalZ))) {
                throw new DeltaGenerationException(
                        "v2.delta-hard-outlet-conflict",
                        "HARD land constraint conflicts with a delta distributary");
            }
            boolean sandbar = fan && plan.sandbars().stream().anyMatch(bar -> distanceMillionths(
                    globalX, globalZ,
                    roundBlock(bar.center().xMillionths()),
                    roundBlock(bar.center().zMillionths()))
                    <= (long) bar.radiusBlocks() * TerrainIntentV2.FIXED_SCALE);
            int boundaryDistance = boundaryDistance(globalX, globalZ);
            boolean shallowSea = fan && boundaryDistance <= plan.shallowSeaBandBlocks();
            int fanSurface = fan ? fanSurfaceMillionths(boundaryDistance) : NO_DATA;
            return new DeltaSample(
                    fan ? 1 : 0,
                    channel ? 1 : 0,
                    channel ? nearest.index() + 1 : 0,
                    fanSurface,
                    sandbar ? 1 : 0,
                    shallowSea
                            ? Math.multiplyExact(plan.selectedShallowSeaDepthBlocks(), TerrainIntentV2.FIXED_SCALE)
                            : NO_DATA,
                    channel ? nearest.branch().dischargeShareMillionths() : 0);
        } catch (ArithmeticException exception) {
            throw new DeltaGenerationException("v2.delta-overflow", "delta sample arithmetic overflow", exception);
        }
    }

    public DeltaWindowV2 renderWindow(
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
            throw new DeltaGenerationException("v2.delta-halo", "requested halo exceeds plan support radius");
        }
        if (coreWidth < 1 || coreLength < 1) {
            throw new DeltaGenerationException("v2.delta-window", "delta core window must be positive");
        }
        try {
            int originX = Math.subtractExact(coreOriginX, haloXZ);
            int originZ = Math.subtractExact(coreOriginZ, haloXZ);
            int windowWidth = Math.addExact(coreWidth, Math.multiplyExact(haloXZ, 2));
            int windowLength = Math.addExact(coreLength, Math.multiplyExact(haloXZ, 2));
            int endX = Math.addExact(originX, windowWidth);
            int endZ = Math.addExact(originZ, windowLength);
            if (originX < 0 || originZ < 0 || endX > width() || endZ > length()) {
                throw new DeltaGenerationException("v2.delta-window", "delta window exceeds world bounds");
            }
            int cells = Math.multiplyExact(windowWidth, windowLength);
            long retained = estimateWindowRetainedBytes(windowWidth, windowLength);
            if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
                throw new DeltaGenerationException("v2.delta-budget", "delta window exceeds retained-memory budget");
            }
            int[][] fields = new int[DeltaField.values().length][cells];
            for (int localZ = 0; localZ < windowLength; localZ++) {
                token.throwIfCancellationRequested();
                for (int localX = 0; localX < windowWidth; localX++) {
                    DeltaSample sample = sampleAt(originX + localX, originZ + localZ, hardLandConflict);
                    int offset = localZ * windowWidth + localX;
                    for (DeltaField field : DeltaField.values()) {
                        fields[field.ordinal()][offset] = sample.rawValue(field);
                    }
                }
            }
            return new DeltaWindowV2(originX, originZ, windowWidth, windowLength, fields, retained);
        } catch (ArithmeticException exception) {
            throw new DeltaGenerationException("v2.delta-overflow", "delta window arithmetic overflow", exception);
        }
    }

    public Map<DeltaField, String> fieldChecksums(IntPredicate hardLandConflict, CancellationToken token) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardLandConflict), token);
    }

    public Map<DeltaField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<DeltaField, MessageDigest> digests = new EnumMap<>(DeltaField.class);
        for (DeltaField field : DeltaField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                DeltaSample sample = Objects.requireNonNull(source.sampleAt(x, z), "delta sample");
                for (DeltaField field : DeltaField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<DeltaField, String> result = new EnumMap<>(DeltaField.class);
        for (DeltaField field : DeltaField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** EXPERIMENTAL V2-3-07 hard-metric hook over frozen graph and generated fields. */
    public DeltaMetrics evaluate(IntPredicate hardLandConflict, CancellationToken token) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        Objects.requireNonNull(token, "token");
        long fanCells = 0L;
        long channelCells = 0L;
        long sandbarCells = 0L;
        int minimumSurface = Integer.MAX_VALUE;
        int maximumSurface = Integer.MIN_VALUE;
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                DeltaSample sample = sampleAt(x, z, hardLandConflict);
                if (sample.fanMask() == 1) {
                    fanCells++;
                    minimumSurface = Math.min(minimumSurface, sample.fanSurfaceMillionths());
                    maximumSurface = Math.max(maximumSurface, sample.fanSurfaceMillionths());
                }
                if (sample.channelMask() == 1) channelCells++;
                if (sample.sandbarMask() == 1) sandbarCells++;
            }
        }
        boolean allMouthsReachSea = true;
        for (DeltaPlanV2.DistributaryBranch branch : plan.branches()) {
            DeltaPlanV2.FanPoint mouth = branch.path().getLast();
            int x = roundBlock(mouth.xMillionths());
            int z = roundBlock(mouth.zMillionths());
            if (hardLandConflict.test(index(x, z)) || sampleAt(x, z, hardLandConflict).channelMask() != 1) {
                allMouthsReachSea = false;
                break;
            }
        }
        long discharge = plan.branches().stream()
                .mapToLong(DeltaPlanV2.DistributaryBranch::dischargeShareMillionths).sum();
        long relief = fanCells == 0L ? 0L : (long) maximumSurface - minimumSurface;
        return new DeltaMetrics(
                plan.branches().size(),
                relief,
                allMouthsReachSea,
                discharge == TerrainIntentV2.FIXED_SCALE,
                fanCells,
                channelCells,
                sandbarCells,
                plan.estimatedRasterWorkUnits());
    }

    public static long estimateWindowRetainedBytes(int windowWidth, int windowLength) {
        if (windowWidth < 1 || windowLength < 1) {
            throw new IllegalArgumentException("delta window dimensions must be positive");
        }
        long cells = Math.multiplyExact((long) windowWidth, windowLength);
        return Math.addExact(Math.multiplyExact(cells, DeltaField.values().length * 4L), 64L * 1024L);
    }

    private NearestBranch nearestBranch(int x, int z) {
        long best = Long.MAX_VALUE;
        int bestIndex = 0;
        for (int index = 0; index < branches.size(); index++) {
            BranchRaster branch = branches.get(index);
            long distance = distanceToPathMillionths(x, z, branch);
            if (distance < best || (distance == best && index < bestIndex)) {
                best = distance;
                bestIndex = index;
            }
        }
        return new NearestBranch(plan.branches().get(bestIndex), bestIndex, best);
    }

    private static long distanceToPathMillionths(int x, int z, BranchRaster branch) {
        long result = Long.MAX_VALUE;
        for (SegmentRaster segment : branch.segments()) {
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
                DeltaFixedMathV2.roundDivide(Math.multiplyExact(numerator, TerrainIntentV2.FIXED_SCALE), denominator)));
        long projectedX = Math.addExact(Math.multiplyExact((long) segment.fromX(), TerrainIntentV2.FIXED_SCALE),
                Math.multiplyExact(dx, projection));
        long projectedZ = Math.addExact(Math.multiplyExact((long) segment.fromZ(), TerrainIntentV2.FIXED_SCALE),
                Math.multiplyExact(dz, projection));
        long cellX = Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE);
        long cellZ = Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE);
        return DeltaFixedMathV2.hypotMillionths(cellX - projectedX, cellZ - projectedZ);
    }

    private static boolean pointInRing(long x, long z, List<DeltaPlanV2.FanPoint> ring) {
        boolean inside = false;
        for (int current = 0, previous = ring.size() - 2; current < ring.size() - 1; previous = current++) {
            DeltaPlanV2.FanPoint a = ring.get(current);
            DeltaPlanV2.FanPoint b = ring.get(previous);
            BigInteger cross = BigInteger.valueOf(b.xMillionths() - a.xMillionths())
                    .multiply(BigInteger.valueOf(z - a.zMillionths()))
                    .subtract(BigInteger.valueOf(b.zMillionths() - a.zMillionths())
                            .multiply(BigInteger.valueOf(x - a.xMillionths())));
            if (cross.signum() == 0
                    && x >= Math.min(a.xMillionths(), b.xMillionths())
                    && x <= Math.max(a.xMillionths(), b.xMillionths())
                    && z >= Math.min(a.zMillionths(), b.zMillionths())
                    && z <= Math.max(a.zMillionths(), b.zMillionths())) {
                return true;
            }
            boolean crosses = (a.zMillionths() > z) != (b.zMillionths() > z);
            if (crosses) {
                BigInteger left = BigInteger.valueOf(x - a.xMillionths())
                        .multiply(BigInteger.valueOf(b.zMillionths() - a.zMillionths()));
                BigInteger right = BigInteger.valueOf(b.xMillionths() - a.xMillionths())
                        .multiply(BigInteger.valueOf(z - a.zMillionths()));
                boolean leftOf = b.zMillionths() > a.zMillionths()
                        ? left.compareTo(right) < 0 : left.compareTo(right) > 0;
                if (leftOf) inside = !inside;
            }
        }
        return inside;
    }

    private int boundaryDistance(int x, int z) {
        return switch (plan.receivingSeaBoundary()) {
            case NORTH -> z;
            case EAST -> width() - 1 - x;
            case SOUTH -> length() - 1 - z;
            case WEST -> x;
        };
    }

    private int fanSurfaceMillionths(int boundaryDistance) {
        int apexDistance = Math.max(1, boundaryDistance(
                roundBlock(plan.apex().xMillionths()), roundBlock(plan.apex().zMillionths())));
        int capped = Math.min(apexDistance, Math.max(0, boundaryDistance));
        long relief = Math.multiplyExact((long) plan.selectedFanReliefBlocks(), TerrainIntentV2.FIXED_SCALE);
        long offset = DeltaFixedMathV2.roundDivide(Math.multiplyExact(relief, capped), apexDistance);
        return Math.toIntExact(Math.addExact(
                Math.multiplyExact((long) plan.waterLevel(), TerrainIntentV2.FIXED_SCALE), offset));
    }

    private static long distanceMillionths(int x, int z, int otherX, int otherZ) {
        return DeltaFixedMathV2.hypotMillionths(
                Math.multiplyExact((long) x - otherX, TerrainIntentV2.FIXED_SCALE),
                Math.multiplyExact((long) z - otherZ, TerrainIntentV2.FIXED_SCALE));
    }

    private int index(int x, int z) {
        return Math.addExact(Math.multiplyExact(z, width()), x);
    }

    private static int roundBlock(long millionths) {
        return Math.toIntExact(DeltaFixedMathV2.roundDivide(millionths, TerrainIntentV2.FIXED_SCALE));
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

    public enum DeltaField {
        FAN_MASK,
        CHANNEL_MASK,
        BRANCH_INDEX,
        FAN_SURFACE,
        SANDBAR_MASK,
        SHALLOW_SEA_DEPTH,
        DISCHARGE_SHARE
    }

    public record DeltaSample(
            int fanMask,
            int channelMask,
            int branchIndex,
            int fanSurfaceMillionths,
            int sandbarMask,
            int shallowSeaDepthMillionths,
            int dischargeShareMillionths
    ) {
        public int rawValue(DeltaField field) {
            return switch (field) {
                case FAN_MASK -> fanMask;
                case CHANNEL_MASK -> channelMask;
                case BRANCH_INDEX -> branchIndex;
                case FAN_SURFACE -> fanSurfaceMillionths;
                case SANDBAR_MASK -> sandbarMask;
                case SHALLOW_SEA_DEPTH -> shallowSeaDepthMillionths;
                case DISCHARGE_SHARE -> dischargeShareMillionths;
            };
        }
    }

    public record DeltaMetrics(
            int activeDistributaryCount,
            long fanReliefMillionths,
            boolean allActiveMouthsMarineReachable,
            boolean flowConserved,
            long fanCells,
            long channelCells,
            long sandbarCells,
            long estimatedRasterWorkUnits
    ) {
    }

    @FunctionalInterface
    public interface CellSource {
        DeltaSample sampleAt(int globalX, int globalZ);
    }

    private record BranchRaster(List<SegmentRaster> segments) {
        static BranchRaster from(DeltaPlanV2.DistributaryBranch branch) {
            List<SegmentRaster> segments = new java.util.ArrayList<>(branch.path().size() - 1);
            for (int index = 1; index < branch.path().size(); index++) {
                DeltaPlanV2.FanPoint from = branch.path().get(index - 1);
                DeltaPlanV2.FanPoint to = branch.path().get(index);
                segments.add(new SegmentRaster(
                        roundBlock(from.xMillionths()), roundBlock(from.zMillionths()),
                        roundBlock(to.xMillionths()), roundBlock(to.zMillionths())));
            }
            return new BranchRaster(List.copyOf(segments));
        }
    }

    private record SegmentRaster(int fromX, int fromZ, int toX, int toZ) {
    }

    private record NearestBranch(
            DeltaPlanV2.DistributaryBranch branch,
            int index,
            long distanceMillionths
    ) {
    }
}
