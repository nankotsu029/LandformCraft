package com.github.nankotsu029.landformcraft.generator.v2.landform.canyon;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
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
import java.util.function.IntPredicate;

/**
 * Fixed-point CANYON rasterizer. River owns bed elevation; canyon owns wall/floor masks and carve height.
 */
public final class CanyonGeneratorV2 {
    public static final String VERSION = "landform-canyon-fixed-v1";
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 12L * 1024L * 1024L;

    private final CanyonPlanV2 plan;
    private final List<MeanderingRiverPlanV2.CenterlineSample> samples;
    private final long floorHalfMillionths;
    private final long rimHalfMillionths;
    private final long depthMillionths;
    private final long wallSpanMillionths;

    public CanyonGeneratorV2(CanyonPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.samples = plan.centerline();
        this.floorHalfMillionths = Math.multiplyExact(
                (long) (plan.selectedFloorWidthBlocks() + 1) / 2, CanyonFixedMathV2.FIXED_SCALE);
        this.rimHalfMillionths = Math.multiplyExact(
                (long) (plan.selectedRimWidthBlocks() + 1) / 2, CanyonFixedMathV2.FIXED_SCALE);
        this.depthMillionths = Math.multiplyExact(
                (long) plan.selectedDepthBlocks(), CanyonFixedMathV2.FIXED_SCALE);
        this.wallSpanMillionths = Math.subtractExact(rimHalfMillionths, floorHalfMillionths);
        if (wallSpanMillionths < 2L * CanyonFixedMathV2.FIXED_SCALE) {
            throw new CanyonGenerationException("v2.canyon-thin-wall", "canyon wall span collapsed");
        }
    }

    public CanyonPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public CanyonSample sampleAt(int globalX, int globalZ, IntPredicate hardLandConflict) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        if (globalX < 0 || globalX >= width() || globalZ < 0 || globalZ >= length()) {
            throw new CanyonGenerationException("v2.canyon-bounds", "sample is outside release-local bounds");
        }
        try {
            long cellX = Math.multiplyExact((long) globalX, CanyonFixedMathV2.FIXED_SCALE)
                    + CanyonFixedMathV2.FIXED_SCALE / 2L;
            long cellZ = Math.multiplyExact((long) globalZ, CanyonFixedMathV2.FIXED_SCALE)
                    + CanyonFixedMathV2.FIXED_SCALE / 2L;
            Nearest nearest = nearestOnCenterline(cellX, cellZ);
            long distance = nearest.distanceMillionths();
            if (distance > rimHalfMillionths) {
                return emptySample();
            }
            boolean floor = distance <= floorHalfMillionths;
            boolean rim = !floor && distance > Math.subtractExact(rimHalfMillionths, CanyonFixedMathV2.FIXED_SCALE);
            boolean terrace = isTerrace(distance);
            long wallHeight = wallHeightAt(distance);
            long surface = Math.addExact(nearest.sample().bedYMillionths(), wallHeight);
            if ((floor || terrace || rim) && hardLandConflict.test(index(globalX, globalZ))) {
                throw new CanyonGenerationException(
                        "v2.canyon-owner-conflict",
                        "HARD land constraint conflicts with canyon carve raster");
            }
            return new CanyonSample(
                    1,
                    floor ? 1 : 0,
                    rim ? 1 : 0,
                    terrace ? 1 : 0,
                    Math.toIntExact(surface),
                    Math.toIntExact(wallHeight),
                    Math.toIntExact(nearest.sample().bedYMillionths()));
        } catch (ArithmeticException exception) {
            throw new CanyonGenerationException("v2.canyon-overflow", "canyon sample arithmetic overflow", exception);
        }
    }

    public CanyonWindowV2 renderWindow(
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
            throw new CanyonGenerationException("v2.canyon-halo", "requested halo exceeds plan support radius");
        }
        try {
            int originX = Math.subtractExact(coreOriginX, haloXZ);
            int originZ = Math.subtractExact(coreOriginZ, haloXZ);
            int windowWidth = Math.addExact(coreWidth, Math.multiplyExact(haloXZ, 2));
            int windowLength = Math.addExact(coreLength, Math.multiplyExact(haloXZ, 2));
            if (originX < 0 || originZ < 0
                    || originX + windowWidth > width()
                    || originZ + windowLength > length()) {
                throw new CanyonGenerationException("v2.canyon-window", "canyon window exceeds world bounds");
            }
            long retained = estimateWindowRetainedBytes(windowWidth, windowLength);
            if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
                throw new CanyonGenerationException("v2.canyon-budget", "canyon window exceeds retained-memory budget");
            }
            int cells = Math.multiplyExact(windowWidth, windowLength);
            int[][] fields = new int[CanyonField.values().length][cells];
            for (int localZ = 0; localZ < windowLength; localZ++) {
                token.throwIfCancellationRequested();
                for (int localX = 0; localX < windowWidth; localX++) {
                    int globalX = originX + localX;
                    int globalZ = originZ + localZ;
                    CanyonSample sample = sampleAt(globalX, globalZ, hardLandConflict);
                    int index = localZ * windowWidth + localX;
                    for (CanyonField field : CanyonField.values()) {
                        fields[field.ordinal()][index] = sample.rawValue(field);
                    }
                }
            }
            return new CanyonWindowV2(originX, originZ, windowWidth, windowLength, fields, retained);
        } catch (ArithmeticException exception) {
            throw new CanyonGenerationException("v2.canyon-overflow", "canyon window arithmetic overflow", exception);
        }
    }

    public Map<CanyonField, String> fieldChecksums(IntPredicate hardLandConflict, CancellationToken token) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardLandConflict), token);
    }

    public Map<CanyonField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<CanyonField, MessageDigest> digests = new EnumMap<>(CanyonField.class);
        for (CanyonField field : CanyonField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                CanyonSample sample = Objects.requireNonNull(source.sampleAt(x, z), "canyon sample");
                for (CanyonField field : CanyonField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<CanyonField, String> result = new EnumMap<>(CanyonField.class);
        for (CanyonField field : CanyonField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** EXPERIMENTAL validator hooks for V2-3-05 Acceptance metrics. */
    public CanyonMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long canyonCells = 0L;
        long floorCells = 0L;
        long rimCells = 0L;
        long terraceCells = 0L;
        boolean monotonic = true;
        for (int index = 1; index < samples.size(); index++) {
            if (samples.get(index).bedYMillionths() > samples.get(index - 1).bedYMillionths()) {
                monotonic = false;
                break;
            }
        }
        long riverHalf = Math.multiplyExact(
                (long) (plan.riverBankfullWidthBlocks() + 1) / 2, CanyonFixedMathV2.FIXED_SCALE);
        long outsideFloor = 0L;
        for (MeanderingRiverPlanV2.CenterlineSample sample : samples) {
            token.throwIfCancellationRequested();
            int x = Math.toIntExact(CanyonFixedMathV2.roundDivide(
                    sample.xMillionths(), CanyonFixedMathV2.FIXED_SCALE));
            int z = Math.toIntExact(CanyonFixedMathV2.roundDivide(
                    sample.zMillionths(), CanyonFixedMathV2.FIXED_SCALE));
            if (x < 0 || x >= width() || z < 0 || z >= length()) {
                outsideFloor++;
                continue;
            }
            if (sampleAt(x, z, index -> false).floorMask() != 1) {
                outsideFloor++;
            }
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                CanyonSample sample = sampleAt(x, z, index -> false);
                if (sample.canyonMask() == 1) canyonCells++;
                if (sample.floorMask() == 1) floorCells++;
                if (sample.rimMask() == 1) rimCells++;
                if (sample.terraceMask() == 1) terraceCells++;
            }
        }
        boolean terraced = plan.crossSection() == TerrainIntentV2.CanyonCrossSection.TERRACED_V
                || plan.crossSection() == TerrainIntentV2.CanyonCrossSection.TERRACED_U;
        return new CanyonMetrics(
                outsideFloor == 0L,
                monotonic,
                canyonCells > 0L && floorCells > 0L && rimCells > 0L,
                !terraced || terraceCells > 0L,
                riverHalf <= floorHalfMillionths,
                canyonCells,
                floorCells,
                rimCells,
                terraceCells,
                plan.selectedFloorWidthBlocks(),
                plan.selectedRimWidthBlocks(),
                plan.selectedDepthBlocks());
    }

    public static long estimateWindowRetainedBytes(int windowWidth, int windowLength) {
        long cells = Math.multiplyExact((long) windowWidth, windowLength);
        return Math.addExact(Math.multiplyExact(cells, CanyonField.values().length * 4L), 64L * 1024L);
    }

    private long wallHeightAt(long distanceMillionths) {
        if (distanceMillionths <= floorHalfMillionths) {
            return 0L;
        }
        long span = Math.subtractExact(distanceMillionths, floorHalfMillionths);
        long normalized = CanyonFixedMathV2.mulDivExact(span, CanyonFixedMathV2.FIXED_SCALE, wallSpanMillionths);
        normalized = CanyonFixedMathV2.clampLong(normalized, 0L, CanyonFixedMathV2.FIXED_SCALE);
        long shaped = switch (plan.crossSection()) {
            case V, TERRACED_V -> normalized;
            case U, TERRACED_U -> CanyonFixedMathV2.mulDivExact(
                    normalized, normalized, CanyonFixedMathV2.FIXED_SCALE);
        };
        boolean terraced = plan.crossSection() == TerrainIntentV2.CanyonCrossSection.TERRACED_V
                || plan.crossSection() == TerrainIntentV2.CanyonCrossSection.TERRACED_U;
        if (!terraced) {
            return CanyonFixedMathV2.mulDivExact(depthMillionths, shaped, CanyonFixedMathV2.FIXED_SCALE);
        }
        int steps = plan.terraceCount() + 1;
        long step = CanyonFixedMathV2.FIXED_SCALE / steps;
        long bucket = Math.min(steps - 1L, Math.floorDiv(shaped, Math.max(1L, step)));
        return CanyonFixedMathV2.mulDivExact(depthMillionths, bucket + 1L, steps);
    }

    private boolean isTerrace(long distanceMillionths) {
        if (plan.terraceCount() == 0 || distanceMillionths <= floorHalfMillionths
                || distanceMillionths > rimHalfMillionths) {
            return false;
        }
        long span = Math.subtractExact(distanceMillionths, floorHalfMillionths);
        long terraceWidth = Math.multiplyExact((long) plan.terraceWidthBlocks(), CanyonFixedMathV2.FIXED_SCALE);
        long band = Math.max(terraceWidth, wallSpanMillionths / (plan.terraceCount() + 1L));
        long mod = Math.floorMod(span, band);
        return mod <= terraceWidth / 2L || mod >= band - terraceWidth / 2L;
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
                projection = CanyonFixedMathV2.clampLong(
                        CanyonFixedMathV2.mulDivExact(dot, CanyonFixedMathV2.FIXED_SCALE, lengthSquared),
                        0L,
                        CanyonFixedMathV2.FIXED_SCALE);
            }
            long px = Math.addExact(a.xMillionths(),
                    CanyonFixedMathV2.mulDivExact(dx, projection, CanyonFixedMathV2.FIXED_SCALE));
            long pz = Math.addExact(a.zMillionths(),
                    CanyonFixedMathV2.mulDivExact(dz, projection, CanyonFixedMathV2.FIXED_SCALE));
            long distance = CanyonFixedMathV2.hypotMillionths(cellX - px, cellZ - pz);
            MeanderingRiverPlanV2.CenterlineSample chosen = projection < CanyonFixedMathV2.FIXED_SCALE / 2 ? a : b;
            if (distance < bestDistance
                    || (distance == bestDistance && chosen.sequence() < bestSample.sequence())) {
                bestDistance = distance;
                bestSample = chosen;
            }
        }
        return new Nearest(bestSample, bestDistance);
    }

    private static CanyonSample emptySample() {
        return new CanyonSample(0, 0, 0, 0, NO_DATA, NO_DATA, NO_DATA);
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

    public enum CanyonField {
        CANYON_MASK,
        FLOOR_MASK,
        RIM_MASK,
        TERRACE_MASK,
        SURFACE_HEIGHT,
        WALL_HEIGHT,
        BED_ELEVATION
    }

    public record CanyonSample(
            int canyonMask,
            int floorMask,
            int rimMask,
            int terraceMask,
            int surfaceHeightMillionths,
            int wallHeightMillionths,
            int bedElevationMillionths
    ) {
        public int rawValue(CanyonField field) {
            return switch (field) {
                case CANYON_MASK -> canyonMask;
                case FLOOR_MASK -> floorMask;
                case RIM_MASK -> rimMask;
                case TERRACE_MASK -> terraceMask;
                case SURFACE_HEIGHT -> surfaceHeightMillionths;
                case WALL_HEIGHT -> wallHeightMillionths;
                case BED_ELEVATION -> bedElevationMillionths;
            };
        }
    }

    public record CanyonMetrics(
            boolean riverContainedInFloor,
            boolean monotonicBed,
            boolean floorAndRimPresent,
            boolean terraceContractSatisfied,
            boolean riverFitsFloor,
            long canyonCells,
            long floorCells,
            long rimCells,
            long terraceCells,
            int selectedFloorWidthBlocks,
            int selectedRimWidthBlocks,
            int selectedDepthBlocks
    ) {
    }

    @FunctionalInterface
    public interface CellSource {
        CanyonSample sampleAt(int globalX, int globalZ);
    }

    private record Nearest(MeanderingRiverPlanV2.CenterlineSample sample, long distanceMillionths) {
    }
}
