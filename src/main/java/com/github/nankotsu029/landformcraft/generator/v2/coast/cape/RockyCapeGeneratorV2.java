package com.github.nankotsu029.landformcraft.generator.v2.coast.cape;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.RockyCapePlanV2;
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

/** V2-2-06 integer-only 2.5D relief, cliff, channel and sea-stack generator. */
public final class RockyCapeGeneratorV2 {
    public static final String VERSION = "rocky-cape-fixed-v1";
    public static final int FIXED_SCALE = 1_000_000;
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final int MAXIMUM_CORE_EXTENT = 256;
    public static final int MAXIMUM_HALO_XZ = 64;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 8L * 1024L * 1024L;
    private static final int EXPOSURE_METRIC_TOLERANCE = 75_000;

    private final RockyCapePlanV2 plan;
    private final CoastalFeaturePlanV2 coastalPlan;
    private final int width;
    private final int length;
    private final List<CoastalFeaturePlanV2.BlockPoint> ring;

    public RockyCapeGeneratorV2(
            RockyCapePlanV2 plan,
            CoastalFeaturePlanV2 coastalPlan,
            int width,
            int length
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.coastalPlan = Objects.requireNonNull(coastalPlan, "coastalPlan");
        if (!plan.featureId().equals(coastalPlan.featureId())
                || coastalPlan.kind() != TerrainIntentV2.FeatureKind.ROCKY_CAPE
                || coastalPlan.geometryRole() != CoastalFeaturePlanV2.GeometryRole.LAND_REGION
                || coastalPlan.geometry().rings().size() != 1) {
            throw failure("v2.cape-plan-binding", "rocky cape and coastal plans do not match");
        }
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000) {
            throw failure("v2.cape-dimensions", "dimensions must be within 1..1000");
        }
        this.width = width;
        this.length = length;
        this.ring = coastalPlan.geometry().rings().getFirst().points();
    }

    public RockyCapePlanV2 plan() { return plan; }
    public int width() { return width; }
    public int length() { return length; }

    public CapeSample sampleAt(int globalX, int globalZ, HardLandWaterSourceV2 hardSource) {
        requireCoordinate(globalX, globalZ);
        Objects.requireNonNull(hardSource, "hardSource");
        try {
            long x = Math.multiplyExact((long) globalX, FIXED_SCALE);
            long z = Math.multiplyExact((long) globalZ, FIXED_SCALE);
            CapeSample sample = seaStackSample(x, z);
            if (sample == null && RockyCapeGeometryV2.contains(ring, x, z)) {
                sample = channelSample(x, z);
                if (sample == null) sample = landSample(globalX, globalZ, x, z);
            }
            if (sample == null) return CapeSample.OUTSIDE;

            HardLandWaterSourceV2.Classification hard = Objects.requireNonNull(
                    hardSource.classificationAt(globalX, globalZ), "hard classification");
            boolean water = sample.region() == CapeRegion.CHANNEL;
            if (water && hard == HardLandWaterSourceV2.Classification.LAND) {
                throw failure("v2.cape-hard-mask-conflict", "HARD LAND_WATER_MASK marks cape channel as land");
            }
            if (!water && hard == HardLandWaterSourceV2.Classification.WATER) {
                throw failure("v2.cape-hard-mask-conflict", "HARD LAND_WATER_MASK marks cape relief as water");
            }
            return new CapeSample(sample.region(), sample.surfaceHeightMillionths(), sample.rockExposure(),
                    sample.descriptorIndex(), hard != HardLandWaterSourceV2.Classification.UNSPECIFIED);
        } catch (RockyCapeGenerationException exception) {
            throw exception;
        } catch (ArithmeticException exception) {
            throw new RockyCapeGenerationException(
                    "v2.cape-overflow", "rocky cape field arithmetic overflow", exception);
        }
    }

    public RockyCapeWindowV2 renderWindow(
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
        RockyCapeWindowV2.Bounds bounds = windowBounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength, haloXZ);
        int cells = Math.multiplyExact(bounds.width(), bounds.length());
        long retained = estimateWindowRetainedBytes(bounds.width(), bounds.length());
        if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
            throw failure("v2.cape-budget", "rocky cape window exceeds retained-memory budget");
        }
        int[][] fields = new int[CapeField.values().length][cells];
        for (int localZ = 0; localZ < bounds.length(); localZ++) {
            token.throwIfCancellationRequested();
            for (int localX = 0; localX < bounds.width(); localX++) {
                int globalX = bounds.originX() + localX;
                int globalZ = bounds.originZ() + localZ;
                CapeSample sample = sampleAt(globalX, globalZ, hardSource);
                int index = localZ * bounds.width() + localX;
                for (CapeField field : CapeField.values()) {
                    fields[field.ordinal()][index] = sample.rawValue(field);
                }
            }
        }
        return new RockyCapeWindowV2(bounds, fields, retained);
    }

    public Map<CapeField, String> fieldChecksums(
            HardLandWaterSourceV2 hardSource,
            CancellationToken token
    ) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardSource), token);
    }

    /** Canonical global row-major checksum over direct or tiled samples. */
    public Map<CapeField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<CapeField, MessageDigest> digests = new EnumMap<>(CapeField.class);
        for (CapeField field : CapeField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width);
            updateInt(digest, length);
            digests.put(field, digest);
        }
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                CapeSample sample = Objects.requireNonNull(source.sampleAt(x, z), "cape sample");
                for (CapeField field : CapeField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<CapeField, String> result = new EnumMap<>(CapeField.class);
        for (CapeField field : CapeField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Streaming Task-local metrics; the independent coastal validator remains V2-2-08. */
    public CapeMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long interior = 0L;
        long cliff = 0L;
        long channel = 0L;
        long stacks = 0L;
        long rockEligible = 0L;
        long rockExposed = 0L;
        int maximumSurface = Integer.MIN_VALUE;
        boolean[] channelSeen = new boolean[5];
        boolean[] stackSeen = new boolean[13];
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                CapeSample sample = sampleAt(x, z, HardLandWaterSourceV2.NONE);
                switch (sample.region()) {
                    case OUTSIDE -> { continue; }
                    case INTERIOR -> interior++;
                    case CLIFF -> cliff++;
                    case CHANNEL -> {
                        channel++;
                        channelSeen[sample.descriptorIndex()] = true;
                    }
                    case SEA_STACK -> {
                        stacks++;
                        stackSeen[sample.descriptorIndex() - 1_024] = true;
                    }
                }
                maximumSurface = Math.max(maximumSurface, sample.surfaceHeightMillionths());
                if (sample.region() != CapeRegion.CHANNEL) {
                    rockEligible++;
                    rockExposed += sample.rockExposure();
                }
            }
        }
        if (interior == 0L || cliff == 0L || channel == 0L || stacks == 0L) {
            throw failure("v2.cape-profile", "cape relief, cliff, channel, or sea stack has no raster cells");
        }
        for (RockyCapePlanV2.ChannelDescriptor descriptor : plan.channels()) {
            if (!channelSeen[descriptor.descriptorIndex()]) {
                throw failure("v2.cape-channel-raster", "a cape channel has no raster cells");
            }
        }
        for (RockyCapePlanV2.SeaStackDescriptor descriptor : plan.seaStacks()) {
            if (!stackSeen[descriptor.descriptorIndex() - 1_024]) {
                throw failure("v2.cape-stack-raster", "a cape sea stack has no raster cells");
            }
        }
        int exposure = Math.toIntExact(RockyCapeFixedMathV2.roundDivide(
                Math.multiplyExact(rockExposed, (long) FIXED_SCALE), rockEligible));
        if (exposure < plan.minimumRockExposureMillionths() - EXPOSURE_METRIC_TOLERANCE
                || exposure > plan.maximumRockExposureMillionths() + EXPOSURE_METRIC_TOLERANCE) {
            throw failure("v2.cape-rock-exposure", "cape rock exposure differs from the requested range");
        }
        int relief = Math.toIntExact(RockyCapeFixedMathV2.roundDivide(
                maximumSurface - (long) plan.waterLevel() * FIXED_SCALE, FIXED_SCALE));
        if (relief != plan.localReliefAboveSeaBlocks()) {
            throw failure("v2.cape-relief", "cape maximum relief differs from its frozen plan");
        }
        return new CapeMetrics(interior, cliff, channel, stacks, exposure, relief,
                plan.coastlineTurningCount(), plan.channels().size(), plan.seaStacks().size());
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        if (width < 1 || length < 1) throw new IllegalArgumentException("window dimensions must be positive");
        return Math.addExact(64L * 1024L, Math.multiplyExact(
                Math.multiplyExact((long) width, length), (long) CapeField.values().length * Integer.BYTES));
    }

    private CapeSample landSample(int globalX, int globalZ, long x, long z) {
        long distance = RockyCapeGeometryV2.distanceToBoundary(ring, x, z);
        long band = (long) plan.cliffBandWidthBlocks() * FIXED_SCALE;
        long interpolation = Math.min(distance, band);
        long rise = RockyCapeFixedMathV2.roundDivide(
                Math.multiplyExact(Math.multiplyExact(
                        (long) plan.localReliefAboveSeaBlocks() - plan.cliffHeightBlocks(), FIXED_SCALE),
                        interpolation), band);
        int height = Math.toIntExact(Math.addExact(
                Math.multiplyExact((long) plan.waterLevel() + plan.cliffHeightBlocks(), FIXED_SCALE), rise));
        CapeRegion region = distance < band ? CapeRegion.CLIFF : CapeRegion.INTERIOR;
        long hash = RockyCapeFixedMathV2.mix64(plan.exposurePatternSeed()
                ^ (long) globalX * 0x632b_e59b_d9b4_e019L
                ^ (long) globalZ * 0x9e37_79b9_7f4a_7c15L);
        int bucket = (int) Long.remainderUnsigned(hash, FIXED_SCALE);
        int exposed = bucket < plan.selectedRockExposureMillionths() ? 1 : 0;
        return new CapeSample(region, height, exposed, 0, false);
    }

    private CapeSample channelSample(long x, long z) {
        for (RockyCapePlanV2.ChannelDescriptor channel : plan.channels()) {
            long distance = RockyCapeGeometryV2.distanceToSegment(
                    channel.mouth(), channel.inlandEnd(), x, z);
            if (distance <= (long) channel.widthBlocks() * FIXED_SCALE / 2L) {
                int height = Math.multiplyExact(plan.waterLevel() - channel.depthBlocks(), FIXED_SCALE);
                return new CapeSample(CapeRegion.CHANNEL, height, 0, channel.descriptorIndex(), false);
            }
        }
        return null;
    }

    private CapeSample seaStackSample(long x, long z) {
        for (RockyCapePlanV2.SeaStackDescriptor stack : plan.seaStacks()) {
            long dx = x - stack.center().xMillionths();
            long dz = z - stack.center().zMillionths();
            long distance = RockyCapeFixedMathV2.integerSquareRoot(
                    Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz)));
            long radius = (long) stack.radiusBlocks() * FIXED_SCALE;
            if (distance <= radius) {
                long shoulder = Math.multiplyExact((long) plan.cliffHeightBlocks(), FIXED_SCALE);
                long additional = RockyCapeFixedMathV2.roundDivide(
                        Math.multiplyExact(Math.multiplyExact(
                                (long) stack.heightAboveSeaBlocks() - plan.cliffHeightBlocks(), FIXED_SCALE),
                                radius - distance), radius);
                int height = Math.toIntExact(Math.addExact(
                        Math.multiplyExact((long) plan.waterLevel(), FIXED_SCALE), shoulder + additional));
                return new CapeSample(CapeRegion.SEA_STACK, height, 1, stack.descriptorIndex(), false);
            }
        }
        return null;
    }

    private RockyCapeWindowV2.Bounds windowBounds(
            int coreOriginX, int coreOriginZ, int coreWidth, int coreLength, int haloXZ
    ) {
        if (coreOriginX < 0 || coreOriginZ < 0 || coreWidth < 1 || coreLength < 1
                || coreWidth > MAXIMUM_CORE_EXTENT || coreLength > MAXIMUM_CORE_EXTENT
                || (long) coreOriginX + coreWidth > width || (long) coreOriginZ + coreLength > length) {
            throw failure("v2.cape-window", "core window is outside bounds or exceeds 256 cells");
        }
        if (haloXZ < 0 || haloXZ > MAXIMUM_HALO_XZ || haloXZ < plan.supportRadiusXZ()) {
            throw failure("v2.cape-support", "window halo is below cape support or outside 0..64");
        }
        int originX = Math.max(0, coreOriginX - haloXZ);
        int originZ = Math.max(0, coreOriginZ - haloXZ);
        int endX = Math.min(width, Math.addExact(Math.addExact(coreOriginX, coreWidth), haloXZ));
        int endZ = Math.min(length, Math.addExact(Math.addExact(coreOriginZ, coreLength), haloXZ));
        return new RockyCapeWindowV2.Bounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength,
                originX, originZ, endX - originX, endZ - originZ, haloXZ);
    }

    private void requireCoordinate(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("coordinate outside rocky cape field");
        }
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

    private static RockyCapeGenerationException failure(String ruleId, String message) {
        return new RockyCapeGenerationException(ruleId, message);
    }

    public enum CapeField { REGION, SURFACE_HEIGHT, ROCK_EXPOSURE, DESCRIPTOR_INDEX }

    public enum CapeRegion {
        OUTSIDE(0), INTERIOR(1), CLIFF(2), CHANNEL(3), SEA_STACK(4);

        private final int rawValue;
        CapeRegion(int rawValue) { this.rawValue = rawValue; }
        public int rawValue() { return rawValue; }
    }

    public record CapeSample(
            CapeRegion region,
            int surfaceHeightMillionths,
            int rockExposure,
            int descriptorIndex,
            boolean hardConstraintMatched
    ) {
        private static final CapeSample OUTSIDE = new CapeSample(CapeRegion.OUTSIDE, NO_DATA, 0, 0, false);

        public CapeSample {
            Objects.requireNonNull(region, "region");
            if (rockExposure < 0 || rockExposure > 1 || descriptorIndex < 0 || descriptorIndex > 65_535) {
                throw new IllegalArgumentException("invalid rocky cape sample");
            }
        }

        public int rawValue(CapeField field) {
            return switch (field) {
                case REGION -> region.rawValue();
                case SURFACE_HEIGHT -> surfaceHeightMillionths;
                case ROCK_EXPOSURE -> rockExposure;
                case DESCRIPTOR_INDEX -> descriptorIndex;
            };
        }
    }

    public record CapeMetrics(
            long interiorCells,
            long cliffCells,
            long channelCells,
            long seaStackCells,
            int rockExposureMillionths,
            int reliefAboveSeaBlocks,
            int coastlineTurningCount,
            int channelDescriptorCount,
            int seaStackDescriptorCount
    ) { }

    @FunctionalInterface
    public interface CellSource {
        CapeSample sampleAt(int globalX, int globalZ);
    }
}
