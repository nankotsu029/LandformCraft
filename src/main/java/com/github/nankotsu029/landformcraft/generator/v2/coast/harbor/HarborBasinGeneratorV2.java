package com.github.nankotsu029.landformcraft.generator.v2.coast.harbor;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.HarborBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** V2-2-04 integer-only local 2.5D HARBOR_BASIN generator. */
public final class HarborBasinGeneratorV2 {
    public static final String VERSION = "harbor-basin-fixed-v1";
    public static final int FIXED_SCALE = 1_000_000;
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final int MAXIMUM_CORE_EXTENT = 256;
    public static final int MAXIMUM_HALO_XZ = 64;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 8L * 1024L * 1024L;

    private final HarborBasinPlanV2 plan;
    private final CoastalFeaturePlanV2 coastalPlan;
    private final int width;
    private final int length;
    private final long midpointX;
    private final long midpointZ;
    private final int openingAxisX;
    private final int openingAxisZ;

    public HarborBasinGeneratorV2(
            HarborBasinPlanV2 plan,
            CoastalFeaturePlanV2 coastalPlan,
            int width,
            int length
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.coastalPlan = Objects.requireNonNull(coastalPlan, "coastalPlan");
        if (!plan.featureId().equals(coastalPlan.featureId())
                || coastalPlan.kind() != TerrainIntentV2.FeatureKind.HARBOR_BASIN
                || coastalPlan.geometryRole() != CoastalFeaturePlanV2.GeometryRole.WATER_REGION) {
            throw failure("v2.harbor-basin-plan-binding", "harbor and coastal plans do not match");
        }
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000) {
            throw failure("v2.harbor-basin-dimensions", "dimensions must be within 1..1000");
        }
        this.width = width;
        this.length = length;
        try {
            midpointX = HarborBasinFixedMathV2.roundDivide(Math.addExact(
                    plan.entranceFirst().xMillionths(), plan.entranceSecond().xMillionths()), 2L);
            midpointZ = HarborBasinFixedMathV2.roundDivide(Math.addExact(
                    plan.entranceFirst().zMillionths(), plan.entranceSecond().zMillionths()), 2L);
            openingAxisX = Math.toIntExact(HarborBasinFixedMathV2.roundDivide(
                    Math.multiplyExact(Math.subtractExact(
                            plan.entranceSecond().xMillionths(), plan.entranceFirst().xMillionths()),
                            (long) FIXED_SCALE), plan.openingWidthMillionths()));
            openingAxisZ = Math.toIntExact(HarborBasinFixedMathV2.roundDivide(
                    Math.multiplyExact(Math.subtractExact(
                            plan.entranceSecond().zMillionths(), plan.entranceFirst().zMillionths()),
                            (long) FIXED_SCALE), plan.openingWidthMillionths()));
        } catch (ArithmeticException exception) {
            throw new HarborBasinGenerationException(
                    "v2.harbor-basin-overflow", "harbor plan arithmetic overflow", exception);
        }
    }

    public HarborBasinPlanV2 plan() {
        return plan;
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public HarborSample sampleAt(int globalX, int globalZ, HardLandWaterSourceV2 hardSource) {
        requireCoordinate(globalX, globalZ);
        Objects.requireNonNull(hardSource, "hardSource");
        try {
            long x = Math.multiplyExact((long) globalX, FIXED_SCALE);
            long z = Math.multiplyExact((long) globalZ, FIXED_SCALE);
            HarborRegion region;
            if (inEntranceCorridor(x, z)) {
                region = HarborRegion.ENTRANCE_CORRIDOR;
            } else if (HarborBasinPlanCompilerV2.contains(coastalPlan.geometry().rings(), x, z)) {
                region = HarborRegion.INTERIOR;
            } else {
                region = HarborRegion.OUTSIDE;
            }
            if (region == HarborRegion.OUTSIDE) {
                return new HarborSample(region, 0, NO_DATA, NO_DATA, false);
            }
            HardLandWaterSourceV2.Classification hard = Objects.requireNonNull(
                    hardSource.classificationAt(globalX, globalZ), "hard classification");
            if (hard == HardLandWaterSourceV2.Classification.LAND) {
                throw failure("v2.harbor-basin-hard-mask-conflict",
                        "HARD LAND_WATER_MASK marks harbor water as land");
            }
            int depth = region == HarborRegion.ENTRANCE_CORRIDOR
                    ? Math.multiplyExact(plan.minimumDepthBlocks(), FIXED_SCALE)
                    : depthAt(x, z);
            int bottom = Math.toIntExact(Math.subtractExact(
                    Math.multiplyExact((long) plan.waterLevel(), FIXED_SCALE), depth));
            return new HarborSample(region, 1, depth, bottom,
                    hard == HardLandWaterSourceV2.Classification.WATER);
        } catch (ArithmeticException exception) {
            throw new HarborBasinGenerationException(
                    "v2.harbor-basin-overflow", "harbor field arithmetic overflow", exception);
        }
    }

    public HarborBasinWindowV2 renderWindow(
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int haloXZ,
            HardLandWaterSourceV2 hardSource,
            CancellationToken token
    ) {
        Objects.requireNonNull(hardSource, "hardSource");
        Objects.requireNonNull(token, "token");
        HarborBasinWindowV2.Bounds bounds = windowBounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength, haloXZ);
        try {
            int cells = Math.multiplyExact(bounds.width(), bounds.length());
            long retained = estimateWindowRetainedBytes(bounds.width(), bounds.length());
            if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
                throw failure("v2.harbor-basin-budget", "harbor window exceeds retained-memory budget");
            }
            int[][] fields = new int[HarborField.values().length][cells];
            for (int localZ = 0; localZ < bounds.length(); localZ++) {
                token.throwIfCancellationRequested();
                for (int localX = 0; localX < bounds.width(); localX++) {
                    int globalX = bounds.originX() + localX;
                    int globalZ = bounds.originZ() + localZ;
                    HarborSample sample = sampleAt(globalX, globalZ, hardSource);
                    int index = localZ * bounds.width() + localX;
                    for (HarborField field : HarborField.values()) {
                        fields[field.ordinal()][index] = sample.rawValue(field);
                    }
                }
            }
            return new HarborBasinWindowV2(bounds, fields, retained);
        } catch (ArithmeticException exception) {
            throw new HarborBasinGenerationException(
                    "v2.harbor-basin-overflow", "harbor window arithmetic overflow", exception);
        }
    }

    public Map<HarborField, String> fieldChecksums(
            HardLandWaterSourceV2 hardSource,
            CancellationToken token
    ) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardSource), token);
    }

    /** Canonical global row-major checksum over direct or tiled samples. */
    public Map<HarborField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<HarborField, MessageDigest> digests = new EnumMap<>(HarborField.class);
        for (HarborField field : HarborField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width);
            updateInt(digest, length);
            digests.put(field, digest);
        }
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                HarborSample sample = Objects.requireNonNull(source.sampleAt(x, z), "harbor sample");
                for (HarborField field : HarborField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<HarborField, String> result = new EnumMap<>(HarborField.class);
        for (HarborField field : HarborField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Streaming Task-local hard metrics; the independent validator remains V2-2-08. */
    public HarborMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long interior = 0L;
        long entrance = 0L;
        long outside = 0L;
        long[] histogram = new long[65];
        int maximumDepth = 0;
        long waterCells = 0L;
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                HarborSample sample = sampleAt(x, z, HardLandWaterSourceV2.NONE);
                switch (sample.region()) {
                    case INTERIOR -> interior++;
                    case ENTRANCE_CORRIDOR -> entrance++;
                    case OUTSIDE -> outside++;
                }
                if (sample.water() == 1) {
                    int depthBlocks = Math.toIntExact(Math.max(1L, Math.min(64L,
                            HarborBasinFixedMathV2.roundDivide(sample.depthMillionths(), FIXED_SCALE))));
                    histogram[depthBlocks]++;
                    waterCells++;
                    maximumDepth = Math.max(maximumDepth, sample.depthMillionths());
                }
            }
        }
        if (interior == 0L || entrance == 0L || outside == 0L || waterCells == 0L) {
            throw failure("v2.harbor-basin-topology", "harbor interior, opening corridor, or exterior is missing");
        }
        int p50 = percentile50(histogram, waterCells);
        if (p50 < plan.minimumDepthBlocks() || p50 > plan.maximumDepthBlocks()
                || maximumDepth != plan.maximumDepthBlocks() * FIXED_SCALE) {
            throw failure("v2.harbor-basin-metric", "navigable depth is outside its hard range or truncated");
        }
        return new HarborMetrics(p50, maximumDepth, interior, entrance, outside);
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        if (width < 1 || length < 1) throw new IllegalArgumentException("window dimensions must be positive");
        long cells = Math.multiplyExact((long) width, length);
        return Math.addExact(64L * 1024L,
                Math.multiplyExact(cells, (long) HarborField.values().length * Integer.BYTES));
    }

    private HarborBasinWindowV2.Bounds windowBounds(
            int coreOriginX, int coreOriginZ, int coreWidth, int coreLength, int haloXZ
    ) {
        if (coreOriginX < 0 || coreOriginZ < 0 || coreWidth < 1 || coreLength < 1
                || coreWidth > MAXIMUM_CORE_EXTENT || coreLength > MAXIMUM_CORE_EXTENT
                || (long) coreOriginX + coreWidth > width || (long) coreOriginZ + coreLength > length) {
            throw failure("v2.harbor-basin-window", "core window is outside bounds or exceeds 256 cells");
        }
        if (haloXZ < 0 || haloXZ > MAXIMUM_HALO_XZ) {
            throw failure("v2.harbor-basin-support", "window halo is outside 0..64");
        }
        int originX = Math.max(0, coreOriginX - haloXZ);
        int originZ = Math.max(0, coreOriginZ - haloXZ);
        int endX = Math.min(width, Math.addExact(Math.addExact(coreOriginX, coreWidth), haloXZ));
        int endZ = Math.min(length, Math.addExact(Math.addExact(coreOriginZ, coreLength), haloXZ));
        return new HarborBasinWindowV2.Bounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength,
                originX, originZ, endX - originX, endZ - originZ, haloXZ);
    }

    private boolean inEntranceCorridor(long x, long z) {
        long dx = Math.subtractExact(x, midpointX);
        long dz = Math.subtractExact(z, midpointZ);
        long along = HarborBasinFixedMathV2.roundDivide(Math.addExact(
                Math.multiplyExact(dx, plan.outwardUnitXMillionths()),
                Math.multiplyExact(dz, plan.outwardUnitZMillionths())), FIXED_SCALE);
        if (along < 0L || along > (long) plan.entranceCorridorLengthBlocks() * FIXED_SCALE) return false;
        long across = HarborBasinFixedMathV2.roundDivide(Math.addExact(
                Math.multiplyExact(dx, openingAxisX), Math.multiplyExact(dz, openingAxisZ)), FIXED_SCALE);
        return Math.abs(across) <= plan.openingWidthMillionths() / 2L;
    }

    private int depthAt(long x, long z) {
        long distance = Long.MAX_VALUE;
        for (CoastalFeaturePlanV2.BlockRing ring : coastalPlan.geometry().rings()) {
            List<CoastalFeaturePlanV2.BlockPoint> points = ring.points();
            for (int index = 0; index < points.size() - 1; index++) {
                distance = Math.min(distance, distanceToSegment(x, z, points.get(index), points.get(index + 1)));
            }
        }
        long transition = Math.multiplyExact((long) plan.profileTransitionBlocks(), FIXED_SCALE);
        long fraction = Math.min(FIXED_SCALE, HarborBasinFixedMathV2.roundDivide(
                Math.multiplyExact(distance, FIXED_SCALE), transition));
        long minimum = Math.multiplyExact((long) plan.minimumDepthBlocks(), FIXED_SCALE);
        long range = Math.multiplyExact((long) plan.maximumDepthBlocks() - plan.minimumDepthBlocks(), FIXED_SCALE);
        return Math.toIntExact(Math.addExact(minimum,
                HarborBasinFixedMathV2.roundDivide(Math.multiplyExact(range, fraction), FIXED_SCALE)));
    }

    private static long distanceToSegment(
            long x, long z, CoastalFeaturePlanV2.BlockPoint a, CoastalFeaturePlanV2.BlockPoint b
    ) {
        long vx = Math.subtractExact(b.xMillionths(), a.xMillionths());
        long vz = Math.subtractExact(b.zMillionths(), a.zMillionths());
        long px = Math.subtractExact(x, a.xMillionths());
        long pz = Math.subtractExact(z, a.zMillionths());
        long lengthSquared = Math.addExact(Math.multiplyExact(vx, vx), Math.multiplyExact(vz, vz));
        long projection = Math.addExact(Math.multiplyExact(px, vx), Math.multiplyExact(pz, vz));
        if (projection <= 0L) return vectorLength(px, pz);
        if (projection >= lengthSquared) {
            return vectorLength(Math.subtractExact(x, b.xMillionths()), Math.subtractExact(z, b.zMillionths()));
        }
        long cross = Math.subtractExact(Math.multiplyExact(vx, pz), Math.multiplyExact(vz, px));
        return HarborBasinFixedMathV2.roundDivide(Math.abs(cross),
                HarborBasinFixedMathV2.integerSquareRoot(lengthSquared));
    }

    private static long vectorLength(long x, long z) {
        return HarborBasinFixedMathV2.integerSquareRoot(
                Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(z, z)));
    }

    private void requireCoordinate(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("coordinate outside harbor field");
        }
    }

    private static int percentile50(long[] histogram, long count) {
        long rank = (count + 1L) / 2L;
        long cumulative = 0L;
        for (int value = 1; value < histogram.length; value++) {
            cumulative += histogram[value];
            if (cumulative >= rank) return value;
        }
        throw new IllegalStateException("empty harbor depth histogram");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static HarborBasinGenerationException failure(String ruleId, String message) {
        return new HarborBasinGenerationException(ruleId, message);
    }

    public enum HarborField { REGION, WATER, DEPTH, BOTTOM_HEIGHT }

    public enum HarborRegion {
        OUTSIDE(0), INTERIOR(1), ENTRANCE_CORRIDOR(2);

        private final int rawValue;

        HarborRegion(int rawValue) {
            this.rawValue = rawValue;
        }

        public int rawValue() {
            return rawValue;
        }
    }

    public record HarborSample(
            HarborRegion region,
            int water,
            int depthMillionths,
            int bottomHeightMillionths,
            boolean hardConstrained
    ) {
        public HarborSample {
            Objects.requireNonNull(region, "region");
        }

        public int rawValue(HarborField field) {
            return switch (Objects.requireNonNull(field, "field")) {
                case REGION -> region.rawValue();
                case WATER -> water;
                case DEPTH -> depthMillionths;
                case BOTTOM_HEIGHT -> bottomHeightMillionths;
            };
        }
    }

    @FunctionalInterface
    public interface CellSource {
        HarborSample sampleAt(int globalX, int globalZ);
    }

    public record HarborMetrics(
            int navigableDepthP50Blocks,
            int maximumDepthMillionths,
            long interiorCells,
            long entranceCorridorCells,
            long outsideCells
    ) { }
}
