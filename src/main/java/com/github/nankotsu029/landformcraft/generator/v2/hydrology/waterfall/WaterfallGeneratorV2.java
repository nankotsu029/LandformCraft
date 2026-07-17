package com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Fixed-point WATERFALL rasterizer for lip / base / plunge-pool 2.5D fields.
 * Falling water column and behind-fall cavity are out of scope.
 */
public final class WaterfallGeneratorV2 {
    public static final String VERSION = "hydrology-waterfall-fixed-v1";
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 8L * 1024L * 1024L;

    private final WaterfallPlanV2 plan;
    private final long lipHalfMillionths;
    private final long plungeRadiusMillionths;
    private final long plungeFloorMillionths;

    public WaterfallGeneratorV2(WaterfallPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.lipHalfMillionths = Math.multiplyExact(
                (long) (plan.lipWidthBlocks() + 1) / 2, WaterfallFixedMathV2.FIXED_SCALE);
        this.plungeRadiusMillionths = Math.multiplyExact(
                (long) plan.plungePoolRadiusBlocks(), WaterfallFixedMathV2.FIXED_SCALE);
        this.plungeFloorMillionths = Math.subtractExact(
                plan.baseBedYMillionths(),
                Math.multiplyExact((long) plan.plungePoolDepthBlocks(), WaterfallFixedMathV2.FIXED_SCALE));
    }

    public WaterfallPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public WaterfallSample sampleAt(int globalX, int globalZ, IntPredicate hardLandConflict) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        if (globalX < 0 || globalX >= width() || globalZ < 0 || globalZ >= length()) {
            throw new WaterfallGenerationException("v2.waterfall-bounds", "sample is outside release-local bounds");
        }
        try {
            long cellX = Math.multiplyExact((long) globalX, WaterfallFixedMathV2.FIXED_SCALE)
                    + WaterfallFixedMathV2.FIXED_SCALE / 2L;
            long cellZ = Math.multiplyExact((long) globalZ, WaterfallFixedMathV2.FIXED_SCALE)
                    + WaterfallFixedMathV2.FIXED_SCALE / 2L;
            long lipDistance = WaterfallFixedMathV2.hypotMillionths(
                    Math.subtractExact(cellX, plan.lipXMillionths()),
                    Math.subtractExact(cellZ, plan.lipZMillionths()));
            long baseDistance = WaterfallFixedMathV2.hypotMillionths(
                    Math.subtractExact(cellX, plan.baseXMillionths()),
                    Math.subtractExact(cellZ, plan.baseZMillionths()));
            boolean lip = lipDistance <= lipHalfMillionths;
            boolean plunge = baseDistance <= plungeRadiusMillionths;
            boolean base = plunge && baseDistance <= Math.max(WaterfallFixedMathV2.FIXED_SCALE, lipHalfMillionths);
            if (!lip && !plunge) {
                return emptySample();
            }
            if (hardLandConflict.test(index(globalX, globalZ))) {
                throw new WaterfallGenerationException(
                        "v2.waterfall-owner-conflict",
                        "HARD land constraint conflicts with waterfall raster");
            }
            int lipElevation = lip ? Math.toIntExact(plan.lipBedYMillionths()) : NO_DATA;
            int baseElevation = (base || plunge) ? Math.toIntExact(plan.baseBedYMillionths()) : NO_DATA;
            int plungeFloor = plunge ? Math.toIntExact(plungeFloorMillionths) : NO_DATA;
            int bed = lip ? Math.toIntExact(plan.lipBedYMillionths())
                    : plunge ? Math.toIntExact(plungeFloorMillionths)
                    : NO_DATA;
            return new WaterfallSample(
                    lip ? 1 : 0,
                    base ? 1 : 0,
                    plunge ? 1 : 0,
                    lipElevation,
                    baseElevation,
                    plungeFloor,
                    bed);
        } catch (ArithmeticException exception) {
            throw new WaterfallGenerationException(
                    "v2.waterfall-overflow", "waterfall sample arithmetic overflow", exception);
        }
    }

    public WaterfallWindowV2 renderWindow(
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int haloXZ,
            IntPredicate hardLandConflict,
            CancellationToken token
    ) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        if (haloXZ < 0 || haloXZ > plan.supportRadiusXZ()) {
            throw new WaterfallGenerationException("v2.waterfall-halo", "requested halo exceeds plan support radius");
        }
        try {
            int originX = Math.subtractExact(coreOriginX, haloXZ);
            int originZ = Math.subtractExact(coreOriginZ, haloXZ);
            int windowWidth = Math.addExact(coreWidth, Math.multiplyExact(haloXZ, 2));
            int windowLength = Math.addExact(coreLength, Math.multiplyExact(haloXZ, 2));
            if (originX < 0 || originZ < 0
                    || originX + windowWidth > width()
                    || originZ + windowLength > length()) {
                throw new WaterfallGenerationException("v2.waterfall-window", "waterfall window exceeds world bounds");
            }
            long retained = estimateWindowRetainedBytes(windowWidth, windowLength);
            if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
                throw new WaterfallGenerationException(
                        "v2.waterfall-budget", "waterfall window exceeds retained-memory budget");
            }
            int cells = Math.multiplyExact(windowWidth, windowLength);
            int[][] fields = new int[WaterfallField.values().length][cells];
            for (int localZ = 0; localZ < windowLength; localZ++) {
                token.throwIfCancellationRequested();
                for (int localX = 0; localX < windowWidth; localX++) {
                    int globalX = originX + localX;
                    int globalZ = originZ + localZ;
                    WaterfallSample sample = sampleAt(globalX, globalZ, hardLandConflict);
                    int index = localZ * windowWidth + localX;
                    for (WaterfallField field : WaterfallField.values()) {
                        fields[field.ordinal()][index] = sample.rawValue(field);
                    }
                }
            }
            return new WaterfallWindowV2(originX, originZ, windowWidth, windowLength, fields, retained);
        } catch (ArithmeticException exception) {
            throw new WaterfallGenerationException(
                    "v2.waterfall-overflow", "waterfall window arithmetic overflow", exception);
        }
    }

    public Map<WaterfallField, String> fieldChecksums(IntPredicate hardLandConflict, CancellationToken token) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardLandConflict), token);
    }

    public Map<WaterfallField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<WaterfallField, MessageDigest> digests = new EnumMap<>(WaterfallField.class);
        for (WaterfallField field : WaterfallField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                WaterfallSample sample = Objects.requireNonNull(source.sampleAt(x, z), "waterfall sample");
                for (WaterfallField field : WaterfallField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<WaterfallField, String> result = new EnumMap<>(WaterfallField.class);
        for (WaterfallField field : WaterfallField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** EXPERIMENTAL validator hooks for V2-3-06 Acceptance metrics. */
    public WaterfallMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long lipCells = 0L;
        long baseCells = 0L;
        long plungeCells = 0L;
        long measuredDrop = Math.subtractExact(plan.lipBedYMillionths(), plan.baseBedYMillionths());
        boolean dropInRange = measuredDrop
                >= Math.multiplyExact((long) plan.minimumDropBlocks(), WaterfallFixedMathV2.FIXED_SCALE)
                && measuredDrop
                <= Math.multiplyExact((long) plan.maximumDropBlocks(), WaterfallFixedMathV2.FIXED_SCALE);
        boolean discontinuityAllowed = plan.downstreamCenterline().getFirst().bedYMillionths()
                < plan.upstreamCenterline().getLast().bedYMillionths();
        boolean upstreamMonotonic = true;
        for (int index = 1; index < plan.upstreamCenterline().size(); index++) {
            if (plan.upstreamCenterline().get(index).bedYMillionths()
                    > plan.upstreamCenterline().get(index - 1).bedYMillionths()) {
                upstreamMonotonic = false;
                break;
            }
        }
        boolean downstreamMonotonic = true;
        for (int index = 1; index < plan.downstreamCenterline().size(); index++) {
            if (plan.downstreamCenterline().get(index).bedYMillionths()
                    > plan.downstreamCenterline().get(index - 1).bedYMillionths()) {
                downstreamMonotonic = false;
                break;
            }
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                WaterfallSample sample = sampleAt(x, z, index -> false);
                if (sample.lipMask() == 1) lipCells++;
                if (sample.baseMask() == 1) baseCells++;
                if (sample.plungePoolMask() == 1) plungeCells++;
            }
        }
        return new WaterfallMetrics(
                dropInRange,
                discontinuityAllowed,
                upstreamMonotonic && downstreamMonotonic,
                lipCells > 0L && baseCells > 0L && plungeCells > 0L,
                plan.behindFallClearanceBlocks() == 0,
                lipCells,
                baseCells,
                plungeCells,
                plan.selectedDropBlocks(),
                plan.lipWidthBlocks(),
                plan.plungePoolRadiusBlocks(),
                measuredDrop);
    }

    public static long estimateWindowRetainedBytes(int windowWidth, int windowLength) {
        long cells = Math.multiplyExact((long) windowWidth, windowLength);
        return Math.addExact(Math.multiplyExact(cells, WaterfallField.values().length * 4L), 64L * 1024L);
    }

    private WaterfallSample emptySample() {
        return new WaterfallSample(0, 0, 0, NO_DATA, NO_DATA, NO_DATA, NO_DATA);
    }

    private int index(int globalX, int globalZ) {
        return globalZ * width() + globalX;
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

    public enum WaterfallField {
        LIP_MASK,
        BASE_MASK,
        PLUNGE_POOL_MASK,
        LIP_ELEVATION,
        BASE_ELEVATION,
        PLUNGE_POOL_FLOOR,
        BED_ELEVATION
    }

    public record WaterfallSample(
            int lipMask,
            int baseMask,
            int plungePoolMask,
            int lipElevation,
            int baseElevation,
            int plungePoolFloor,
            int bedElevation
    ) {
        int rawValue(WaterfallField field) {
            return switch (field) {
                case LIP_MASK -> lipMask;
                case BASE_MASK -> baseMask;
                case PLUNGE_POOL_MASK -> plungePoolMask;
                case LIP_ELEVATION -> lipElevation;
                case BASE_ELEVATION -> baseElevation;
                case PLUNGE_POOL_FLOOR -> plungePoolFloor;
                case BED_ELEVATION -> bedElevation;
            };
        }
    }

    public record WaterfallMetrics(
            boolean dropInRange,
            boolean bedDiscontinuityAllowed,
            boolean reachBedsMonotonic,
            boolean lipBasePlungePresent,
            boolean volumeClearanceDeferred,
            long lipCells,
            long baseCells,
            long plungePoolCells,
            int selectedDropBlocks,
            int lipWidthBlocks,
            int plungePoolRadiusBlocks,
            long measuredDropMillionths
    ) {
    }

    @FunctionalInterface
    public interface CellSource {
        WaterfallSample sampleAt(int globalX, int globalZ);
    }
}
