package com.github.nankotsu029.landformcraft.generator.v2.coast.beach;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalRasterKernelV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalRasterException;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalRasterWindowV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.SandyBeachPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** V2-2-03 fixed-point SANDY_BEACH generator; it emits semantic fields, not block states. */
public final class SandyBeachGeneratorV2 {
    public static final String VERSION = "sandy-beach-fixed-v1";
    public static final int FIXED_SCALE = 1_000_000;
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 8L * 1024L * 1024L;

    private final SandyBeachPlanV2 plan;
    private final CoastalRasterKernelV2 coast;
    private final CoastalFeaturePlanV2.BlockPoint firstEndpoint;
    private final CoastalFeaturePlanV2.BlockPoint lastEndpoint;

    public SandyBeachGeneratorV2(SandyBeachPlanV2 plan, CoastalRasterKernelV2 coast) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.coast = Objects.requireNonNull(coast, "coast");
        if (!plan.featureId().equals(coast.plan().featureId())
                || plan.supportRadiusXZ() != coast.plan().supportRadiusXZ()) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-plan-binding", "beach and coastal raster plans do not match");
        }
        var points = coast.plan().geometry().paths().getFirst().points();
        firstEndpoint = points.getFirst();
        lastEndpoint = points.getLast();
    }

    public SandyBeachPlanV2 plan() {
        return plan;
    }

    public int width() {
        return coast.width();
    }

    public int length() {
        return coast.length();
    }

    public BeachSample sampleAt(int globalX, int globalZ, HardLandWaterSourceV2 hardSource) {
        Objects.requireNonNull(hardSource, "hardSource");
        try {
            CoastalRasterKernelV2.RasterSample coastal = coast.sampleAt(globalX, globalZ, hardSource);
            int signedDistance = coastal.signedDistanceMillionths();
            int localWidth = localWidthMillionths(globalX, globalZ);
            // The common coast kernel saturates at its declared support radius. Keep the outer
            // beach edge half-open so a saturated value cannot turn every farther land cell into
            // beach without changing the V2-2-02 kernel contract.
            boolean landBand = signedDistance >= 0 && signedDistance < localWidth;
            boolean nearshoreBand = signedDistance < 0
                    && -(long) signedDistance <= (long) plan.nearshoreDistanceBlocks() * FIXED_SCALE;
            if (coastal.hardConstrained()
                    && ((landBand && coastal.actualLandWater() == 0)
                    || (nearshoreBand && coastal.actualLandWater() == 1))) {
                throw new SandyBeachGenerationException(
                        "v2.sandy-beach-hard-mask-conflict",
                        "HARD LAND_WATER_MASK conflicts with the sandy beach band");
            }

            BeachBand band = BeachBand.OUTSIDE;
            int surface = NO_DATA;
            if (landBand && coastal.actualLandWater() == 1) {
                long foreshoreLimit = SandyBeachFixedMathV2.roundDivide(
                        Math.multiplyExact((long) localWidth, plan.foreshoreShareMillionths()), FIXED_SCALE);
                band = signedDistance <= foreshoreLimit ? BeachBand.FORESHORE : BeachBand.BACKSHORE;
                long rise = SandyBeachFixedMathV2.roundDivide(
                        Math.multiplyExact((long) signedDistance, plan.risePerBlockMillionths()), FIXED_SCALE);
                surface = checkedSurface(Math.addExact(
                        Math.multiplyExact((long) plan.waterLevel(), FIXED_SCALE), rise));
            } else if (nearshoreBand && coastal.actualLandWater() == 0
                    && coastal.nearshoreDepthMillionths() != CoastalRasterKernelV2.NEARSHORE_NO_DATA) {
                band = BeachBand.NEARSHORE;
                surface = checkedSurface(Math.subtractExact(
                        Math.multiplyExact((long) plan.waterLevel(), FIXED_SCALE),
                        coastal.nearshoreDepthMillionths()));
            }
            int widthValue = band == BeachBand.OUTSIDE ? NO_DATA : localWidth;
            int sand = band == BeachBand.FORESHORE || band == BeachBand.BACKSHORE ? 1 : 0;
            return new BeachSample(widthValue, surface, band, sand, coastal);
        } catch (ArithmeticException exception) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-overflow", "sandy beach arithmetic overflow", exception);
        }
    }

    public SandyBeachWindowV2 renderWindow(
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int haloXZ,
            HardLandWaterSourceV2 hardSource,
        CancellationToken token
    ) {
        Objects.requireNonNull(token, "token");
        try {
            CoastalRasterWindowV2.Bounds bounds = coast.windowBounds(
                    coreOriginX, coreOriginZ, coreWidth, coreLength, haloXZ);
            int cells = Math.multiplyExact(bounds.width(), bounds.length());
            long retained = estimateWindowRetainedBytes(bounds.width(), bounds.length());
            if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
                throw new SandyBeachGenerationException(
                        "v2.sandy-beach-budget", "beach window exceeds retained-memory budget");
            }
            int[][] fields = new int[BeachField.values().length][cells];
            for (int localZ = 0; localZ < bounds.length(); localZ++) {
                token.throwIfCancellationRequested();
                for (int localX = 0; localX < bounds.width(); localX++) {
                    int globalX = bounds.originX() + localX;
                    int globalZ = bounds.originZ() + localZ;
                    BeachSample sample = sampleAt(globalX, globalZ, hardSource);
                    int index = localZ * bounds.width() + localX;
                    for (BeachField field : BeachField.values()) {
                        fields[field.ordinal()][index] = sample.rawValue(field);
                    }
                }
            }
            return new SandyBeachWindowV2(bounds, fields, retained);
        } catch (CoastalRasterException exception) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-window", "invalid sandy beach window", exception);
        } catch (ArithmeticException exception) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-overflow", "beach window arithmetic overflow", exception);
        }
    }

    public Map<BeachField, String> fieldChecksums(
            HardLandWaterSourceV2 hardSource,
            CancellationToken token
    ) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardSource), token);
    }

    public Map<BeachField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<BeachField, MessageDigest> digests = new EnumMap<>(BeachField.class);
        for (BeachField field : BeachField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                BeachSample sample = Objects.requireNonNull(source.sampleAt(x, z), "beach sample");
                for (BeachField field : BeachField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<BeachField, String> result = new EnumMap<>(BeachField.class);
        for (BeachField field : BeachField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Streaming feature-local metrics used by this Task's acceptance gate, not the V2-2-08 validator. */
    public BeachMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long[] widthHistogram = new long[65];
        long shorelineCells = 0L;
        long foreshoreCells = 0L;
        long backshoreCells = 0L;
        long nearshoreCells = 0L;
        int maximumDepth = 0;
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                BeachSample sample = sampleAt(x, z, HardLandWaterSourceV2.NONE);
                switch (sample.band()) {
                    case FORESHORE -> foreshoreCells++;
                    case BACKSHORE -> backshoreCells++;
                    case NEARSHORE -> {
                        nearshoreCells++;
                        maximumDepth = Math.max(maximumDepth,
                                sample.coastalSample().nearshoreDepthMillionths());
                    }
                    case OUTSIDE -> { }
                }
                if (Math.abs((long) sample.coastalSample().signedDistanceMillionths()) <= 500_000L) {
                    int widthBlocks = Math.toIntExact(Math.max(1L, Math.min(64L,
                            SandyBeachFixedMathV2.roundDivide(localWidthMillionths(x, z), FIXED_SCALE))));
                    widthHistogram[widthBlocks]++;
                    shorelineCells++;
                }
            }
        }
        if (shorelineCells == 0L || foreshoreCells == 0L || backshoreCells == 0L || nearshoreCells == 0L) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-edge-corruption", "beach bands are truncated or disconnected");
        }
        int p50 = percentile50(widthHistogram, shorelineCells);
        if (p50 < plan.minimumWidthBlocks() || p50 > plan.maximumWidthBlocks()
                || maximumDepth != plan.nearshoreTargetDepthBlocks() * FIXED_SCALE) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-metric", "beach width or nearshore depth metric is outside its hard range");
        }
        return new BeachMetrics(
                p50, plan.selectedShoreSlopeDegreesMillionths(), maximumDepth,
                foreshoreCells, backshoreCells, nearshoreCells);
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        if (width < 1 || length < 1) throw new IllegalArgumentException("window dimensions must be positive");
        long cells = Math.multiplyExact((long) width, length);
        return Math.addExact(64L * 1024L,
                Math.multiplyExact(cells, (long) BeachField.values().length * Integer.BYTES));
    }

    private int localWidthMillionths(int globalX, int globalZ) {
        long x = Math.multiplyExact((long) globalX, FIXED_SCALE);
        long z = Math.multiplyExact((long) globalZ, FIXED_SCALE);
        long endpointDistance = Math.min(distance(x, z, firstEndpoint), distance(x, z, lastEndpoint));
        long taperDistance = Math.multiplyExact((long) plan.endpointTaperBlocks(), FIXED_SCALE);
        long fraction = Math.min(FIXED_SCALE,
                SandyBeachFixedMathV2.roundDivide(Math.multiplyExact(endpointDistance, FIXED_SCALE), taperDistance));
        long minimum = Math.multiplyExact((long) plan.minimumWidthBlocks(), FIXED_SCALE);
        long range = Math.multiplyExact((long) plan.maximumWidthBlocks() - plan.minimumWidthBlocks(), FIXED_SCALE);
        return Math.toIntExact(Math.addExact(minimum,
                SandyBeachFixedMathV2.roundDivide(Math.multiplyExact(range, fraction), FIXED_SCALE)));
    }

    private static long distance(long x, long z, CoastalFeaturePlanV2.BlockPoint endpoint) {
        long dx = Math.subtractExact(x, endpoint.xMillionths());
        long dz = Math.subtractExact(z, endpoint.zMillionths());
        return SandyBeachFixedMathV2.integerSquareRoot(
                Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz)));
    }

    private int checkedSurface(long surface) {
        long minimum = Math.multiplyExact((long) plan.minY(), FIXED_SCALE);
        long maximum = Math.multiplyExact((long) plan.maxY(), FIXED_SCALE);
        if (surface < minimum || surface > maximum) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-vertical-bounds", "generated beach surface exceeds vertical bounds");
        }
        return Math.toIntExact(surface);
    }

    private static int percentile50(long[] histogram, long count) {
        long rank = (count + 1L) / 2L;
        long cumulative = 0L;
        for (int value = 1; value < histogram.length; value++) {
            cumulative += histogram[value];
            if (cumulative >= rank) return value;
        }
        throw new IllegalStateException("empty width histogram");
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

    public enum BeachField {
        LOCAL_WIDTH,
        SURFACE_HEIGHT,
        BAND,
        SEMANTIC_SAND
    }

    public enum BeachBand {
        OUTSIDE(0),
        NEARSHORE(1),
        FORESHORE(2),
        BACKSHORE(3);

        private final int rawValue;

        BeachBand(int rawValue) {
            this.rawValue = rawValue;
        }

        public int rawValue() {
            return rawValue;
        }
    }

    public record BeachSample(
            int localWidthMillionths,
            int surfaceHeightMillionths,
            BeachBand band,
            int semanticSand,
            CoastalRasterKernelV2.RasterSample coastalSample
    ) {
        public BeachSample {
            Objects.requireNonNull(band, "band");
            Objects.requireNonNull(coastalSample, "coastalSample");
        }

        public int rawValue(BeachField field) {
            return switch (Objects.requireNonNull(field, "field")) {
                case LOCAL_WIDTH -> localWidthMillionths;
                case SURFACE_HEIGHT -> surfaceHeightMillionths;
                case BAND -> band.rawValue();
                case SEMANTIC_SAND -> semanticSand;
            };
        }
    }

    public record BeachMetrics(
            int widthP50Blocks,
            long shoreSlopeDegreesMillionths,
            int maximumNearshoreDepthMillionths,
            long foreshoreCells,
            long backshoreCells,
            long nearshoreCells
    ) { }

    @FunctionalInterface
    public interface CellSource {
        BeachSample sampleAt(int globalX, int globalZ);
    }
}
