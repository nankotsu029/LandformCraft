package com.github.nankotsu029.landformcraft.generator.v2.hydrology.river;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;

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

/**
 * Fixed-point RIVER / MEANDERING_RIVER rasterizer.
 * Meander only offsets the centerline corridor; graph topology remains one source→mouth reach.
 */
public final class MeanderingRiverGeneratorV2 {
    public static final String VERSION = "hydrology-river-fixed-v1";
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 12L * 1024L * 1024L;
    public static final int WATER_BODY_CODE = 1;

    private final MeanderingRiverPlanV2 plan;
    private final List<MeanderingRiverPlanV2.CenterlineSample> samples;

    public MeanderingRiverGeneratorV2(MeanderingRiverPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.samples = plan.centerline();
    }

    public MeanderingRiverPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public RiverSample sampleAt(int globalX, int globalZ, IntPredicate hardRouteConflict) {
        Objects.requireNonNull(hardRouteConflict, "hardRouteConflict");
        if (globalX < 0 || globalX >= width() || globalZ < 0 || globalZ >= length()) {
            throw new RiverGenerationException("v2.river-bounds", "sample is outside release-local bounds");
        }
        try {
            long cellX = Math.multiplyExact((long) globalX, RiverFixedMathV2.FIXED_SCALE)
                    + RiverFixedMathV2.FIXED_SCALE / 2L;
            long cellZ = Math.multiplyExact((long) globalZ, RiverFixedMathV2.FIXED_SCALE)
                    + RiverFixedMathV2.FIXED_SCALE / 2L;
            Nearest nearest = nearestOnCenterline(cellX, cellZ);
            long distance = nearest.distanceMillionths();
            int halfWidth = nearest.sample().localHalfWidthBlocks();
            long channelLimit = Math.multiplyExact((long) halfWidth, RiverFixedMathV2.FIXED_SCALE);
            long bankLimit = Math.multiplyExact(
                    (long) halfWidth + plan.bankWidthBlocks(), RiverFixedMathV2.FIXED_SCALE);
            long floodLimit = Math.multiplyExact((long) plan.floodplainWidthBlocks(), RiverFixedMathV2.FIXED_SCALE);
            long corridorLimit = Math.multiplyExact(
                    (long) plan.meanderCorridorHalfWidthBlocks(), RiverFixedMathV2.FIXED_SCALE);

            boolean channel = distance <= channelLimit;
            boolean bank = !channel && distance <= bankLimit;
            boolean floodplain = !channel && !bank && distance <= floodLimit;
            boolean corridor = distance <= corridorLimit;
            if (channel && hardRouteConflict.test(index(globalX, globalZ))) {
                throw new RiverGenerationException(
                        "v2.river-hard-route-conflict",
                        "HARD route constraint conflicts with the river channel raster");
            }

            int localWidth = channel || bank || floodplain
                    ? Math.multiplyExact(halfWidth * 2, RiverFixedMathV2.FIXED_SCALE)
                    : NO_DATA;
            int bed = channel ? Math.toIntExact(nearest.sample().bedYMillionths()) : NO_DATA;
            int surface = channel
                    ? Math.toIntExact(Math.addExact(nearest.sample().bedYMillionths(), plan.waterDepthMillionths()))
                    : NO_DATA;
            int depth = channel ? Math.toIntExact(plan.waterDepthMillionths()) : NO_DATA;
            int waterBody = channel ? WATER_BODY_CODE : 0;
            return new RiverSample(
                    channel ? 1 : 0,
                    bank ? 1 : 0,
                    floodplain ? 1 : 0,
                    corridor ? 1 : 0,
                    localWidth,
                    channel || bank || floodplain ? plan.selectedDischargeIndex() : 0,
                    bed,
                    surface,
                    depth,
                    waterBody);
        } catch (ArithmeticException exception) {
            throw new RiverGenerationException("v2.river-overflow", "river sample arithmetic overflow", exception);
        }
    }

    public RiverWindowV2 renderWindow(
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int haloXZ,
            IntPredicate hardRouteConflict,
            CancellationToken token
    ) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(hardRouteConflict, "hardRouteConflict");
        if (haloXZ < 0 || haloXZ > plan.supportRadiusXZ()) {
            throw new RiverGenerationException("v2.river-halo", "requested halo exceeds plan support radius");
        }
        try {
            int originX = Math.subtractExact(coreOriginX, haloXZ);
            int originZ = Math.subtractExact(coreOriginZ, haloXZ);
            int windowWidth = Math.addExact(coreWidth, Math.multiplyExact(haloXZ, 2));
            int windowLength = Math.addExact(coreLength, Math.multiplyExact(haloXZ, 2));
            if (originX < 0 || originZ < 0
                    || originX + windowWidth > width()
                    || originZ + windowLength > length()) {
                throw new RiverGenerationException("v2.river-window", "river window exceeds world bounds");
            }
            int cells = Math.multiplyExact(windowWidth, windowLength);
            long retained = estimateWindowRetainedBytes(windowWidth, windowLength);
            if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
                throw new RiverGenerationException("v2.river-budget", "river window exceeds retained-memory budget");
            }
            int[][] fields = new int[RiverField.values().length][cells];
            for (int localZ = 0; localZ < windowLength; localZ++) {
                token.throwIfCancellationRequested();
                for (int localX = 0; localX < windowWidth; localX++) {
                    int globalX = originX + localX;
                    int globalZ = originZ + localZ;
                    RiverSample sample = sampleAt(globalX, globalZ, hardRouteConflict);
                    int index = localZ * windowWidth + localX;
                    for (RiverField field : RiverField.values()) {
                        fields[field.ordinal()][index] = sample.rawValue(field);
                    }
                }
            }
            return new RiverWindowV2(originX, originZ, windowWidth, windowLength, fields, retained);
        } catch (ArithmeticException exception) {
            throw new RiverGenerationException("v2.river-overflow", "river window arithmetic overflow", exception);
        }
    }

    public Map<RiverField, String> fieldChecksums(IntPredicate hardRouteConflict, CancellationToken token) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardRouteConflict), token);
    }

    public Map<RiverField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<RiverField, MessageDigest> digests = new EnumMap<>(RiverField.class);
        for (RiverField field : RiverField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                RiverSample sample = Objects.requireNonNull(source.sampleAt(x, z), "river sample");
                for (RiverField field : RiverField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<RiverField, String> result = new EnumMap<>(RiverField.class);
        for (RiverField field : RiverField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** EXPERIMENTAL validator hooks for V2-3-03 Acceptance metrics. */
    public RiverMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long channelCells = 0L;
        long bankCells = 0L;
        long floodplainCells = 0L;
        long corridorCells = 0L;
        long outsideCorridorChannel = 0L;
        boolean monotonic = true;
        for (int index = 1; index < samples.size(); index++) {
            if (samples.get(index).bedYMillionths() > samples.get(index - 1).bedYMillionths()) {
                monotonic = false;
                break;
            }
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                RiverSample sample = sampleAt(x, z, index -> false);
                if (sample.channelMask() == 1) {
                    channelCells++;
                    if (sample.meanderCorridor() == 0) outsideCorridorChannel++;
                }
                if (sample.bankMask() == 1) bankCells++;
                if (sample.floodplainMask() == 1) floodplainCells++;
                if (sample.meanderCorridor() == 1) corridorCells++;
            }
        }
        MeanderingRiverPlanV2.CenterlineSample mouth = samples.getLast();
        int mouthX = Math.toIntExact(RiverFixedMathV2.roundDivide(
                mouth.xMillionths(), RiverFixedMathV2.FIXED_SCALE));
        int mouthZ = Math.toIntExact(RiverFixedMathV2.roundDivide(
                mouth.zMillionths(), RiverFixedMathV2.FIXED_SCALE));
        boolean reachedMouth = mouthX >= 0 && mouthX < width() && mouthZ >= 0 && mouthZ < length()
                && sampleAt(mouthX, mouthZ, index -> false).channelMask() == 1;
        MeanderingRiverPlanV2.CenterlineSample source = samples.getFirst();
        int sourceX = Math.toIntExact(RiverFixedMathV2.roundDivide(
                source.xMillionths(), RiverFixedMathV2.FIXED_SCALE));
        int sourceZ = Math.toIntExact(RiverFixedMathV2.roundDivide(
                source.zMillionths(), RiverFixedMathV2.FIXED_SCALE));
        boolean reachedSource = sourceX >= 0 && sourceX < width() && sourceZ >= 0 && sourceZ < length()
                && sampleAt(sourceX, sourceZ, index -> false).channelMask() == 1;
        return new RiverMetrics(
                reachedSource && reachedMouth,
                monotonic,
                plan.selectedDischargeIndex(),
                outsideCorridorChannel == 0L,
                channelCells,
                bankCells,
                floodplainCells,
                corridorCells,
                plan.meanderAmplitudeBlocks(),
                plan.meanderWavelengthBlocks(),
                plan.totalArcLengthMillionths(),
                Math.subtractExact(plan.sourceBedYMillionths(), plan.mouthBedYMillionths()));
    }

    public static long estimateWindowRetainedBytes(int windowWidth, int windowLength) {
        long cells = Math.multiplyExact((long) windowWidth, windowLength);
        return Math.addExact(Math.multiplyExact(cells, RiverField.values().length * 4L), 64L * 1024L);
    }

    private Nearest nearestOnCenterline(long cellX, long cellZ) {
        long bestDistance = Long.MAX_VALUE;
        MeanderingRiverPlanV2.CenterlineSample bestSample = samples.getFirst();
        for (int index = 0; index < samples.size() - 1; index++) {
            MeanderingRiverPlanV2.CenterlineSample a = samples.get(index);
            MeanderingRiverPlanV2.CenterlineSample b = samples.get(index + 1);
            long dx = b.xMillionths() - a.xMillionths();
            long dz = b.zMillionths() - a.zMillionths();
            long lengthSquared = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
            long projection;
            if (lengthSquared == 0L) {
                projection = 0L;
            } else {
                long relX = cellX - a.xMillionths();
                long relZ = cellZ - a.zMillionths();
                long dot = Math.addExact(Math.multiplyExact(relX, dx), Math.multiplyExact(relZ, dz));
                projection = RiverFixedMathV2.clampLong(
                        RiverFixedMathV2.mulDivExact(dot, RiverFixedMathV2.FIXED_SCALE, lengthSquared),
                        0L,
                        RiverFixedMathV2.FIXED_SCALE);
            }
            long px = Math.addExact(a.xMillionths(),
                    RiverFixedMathV2.mulDivExact(dx, projection, RiverFixedMathV2.FIXED_SCALE));
            long pz = Math.addExact(a.zMillionths(),
                    RiverFixedMathV2.mulDivExact(dz, projection, RiverFixedMathV2.FIXED_SCALE));
            long distance = RiverFixedMathV2.hypotMillionths(cellX - px, cellZ - pz);
            MeanderingRiverPlanV2.CenterlineSample chosen = projection < RiverFixedMathV2.FIXED_SCALE / 2
                    ? a : b;
            if (distance < bestDistance
                    || (distance == bestDistance && chosen.sequence() < bestSample.sequence())) {
                bestDistance = distance;
                bestSample = chosen;
            }
        }
        return new Nearest(bestSample, bestDistance);
    }

    private int index(int x, int z) {
        return Math.addExact(Math.multiplyExact(z, width()), x);
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

    public enum RiverField {
        CHANNEL_MASK,
        BANK_MASK,
        FLOODPLAIN_MASK,
        MEANDER_CORRIDOR,
        LOCAL_WIDTH,
        DISCHARGE_INDEX,
        BED_ELEVATION,
        WATER_SURFACE,
        WATER_DEPTH,
        WATER_BODY_ID
    }

    public record RiverSample(
            int channelMask,
            int bankMask,
            int floodplainMask,
            int meanderCorridor,
            int localWidthMillionths,
            int dischargeIndex,
            int bedElevationMillionths,
            int waterSurfaceMillionths,
            int waterDepthMillionths,
            int waterBodyId
    ) {
        public int rawValue(RiverField field) {
            return switch (field) {
                case CHANNEL_MASK -> channelMask;
                case BANK_MASK -> bankMask;
                case FLOODPLAIN_MASK -> floodplainMask;
                case MEANDER_CORRIDOR -> meanderCorridor;
                case LOCAL_WIDTH -> localWidthMillionths;
                case DISCHARGE_INDEX -> dischargeIndex;
                case BED_ELEVATION -> bedElevationMillionths;
                case WATER_SURFACE -> waterSurfaceMillionths;
                case WATER_DEPTH -> waterDepthMillionths;
                case WATER_BODY_ID -> waterBodyId;
            };
        }
    }

    public record RiverMetrics(
            boolean sourceToMouthReachable,
            boolean monotonicBed,
            int confluenceDischargeIndex,
            boolean meanderWithinCorridor,
            long channelCells,
            long bankCells,
            long floodplainCells,
            long corridorCells,
            int meanderAmplitudeBlocks,
            int meanderWavelengthBlocks,
            long arcLengthMillionths,
            long bedDropMillionths
    ) {
    }

    @FunctionalInterface
    public interface CellSource {
        RiverSample sampleAt(int globalX, int globalZ);
    }

    private record Nearest(MeanderingRiverPlanV2.CenterlineSample sample, long distanceMillionths) {
    }
}
