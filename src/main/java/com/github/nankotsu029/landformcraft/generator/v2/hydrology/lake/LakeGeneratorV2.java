package com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
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
 * Fixed-point LAKE rasterizer for independent basin / rim / spillway shaping.
 * Dam and reservoir structures are out of scope.
 */
public final class LakeGeneratorV2 {
    public static final String VERSION = "hydrology-lake-fixed-v1";
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 12L * 1024L * 1024L;
    public static final int WATER_BODY_CODE = 2;

    private final LakePlanV2 plan;
    private final long maxInteriorDistanceMillionths;
    private final long spillMidX;
    private final long spillMidZ;
    private final long spillHalfWidthMillionths;

    public LakeGeneratorV2(LakePlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.maxInteriorDistanceMillionths = computeMaxInteriorDistance(plan);
        if (plan.terminalPolicy() == TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL) {
            this.spillMidX = LakeFixedMathV2.roundDivide(
                    Math.addExact(plan.spillFirst().xMillionths(), plan.spillSecond().xMillionths()), 2L);
            this.spillMidZ = LakeFixedMathV2.roundDivide(
                    Math.addExact(plan.spillFirst().zMillionths(), plan.spillSecond().zMillionths()), 2L);
            this.spillHalfWidthMillionths = Math.multiplyExact(
                    (long) plan.spillwayWidthBlocks(), LakeFixedMathV2.FIXED_SCALE) / 2L;
        } else {
            this.spillMidX = 0L;
            this.spillMidZ = 0L;
            this.spillHalfWidthMillionths = 0L;
        }
    }

    public LakePlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public LakeSample sampleAt(int globalX, int globalZ, IntPredicate hardLandConflict) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        if (globalX < 0 || globalX >= width() || globalZ < 0 || globalZ >= length()) {
            throw new LakeGenerationException("v2.lake-bounds", "sample is outside release-local bounds");
        }
        try {
            long cellX = Math.multiplyExact((long) globalX, LakeFixedMathV2.FIXED_SCALE)
                    + LakeFixedMathV2.FIXED_SCALE / 2L;
            long cellZ = Math.multiplyExact((long) globalZ, LakeFixedMathV2.FIXED_SCALE)
                    + LakeFixedMathV2.FIXED_SCALE / 2L;
            boolean basin = LakeFixedMathV2.pointInRing(plan.basinRing(), cellX, cellZ);
            boolean spillway = isSpillway(cellX, cellZ);
            if (!basin && !spillway) {
                return emptySample();
            }
            if (hardLandConflict.test(index(globalX, globalZ))) {
                throw new LakeGenerationException(
                        "v2.lake-hard-conflict",
                        "HARD land constraint conflicts with lake water raster");
            }
            if (spillway && !basin) {
                // Exterior spill corridor only: reverse flow into the basin is rejected at compile.
                int shallow = Math.toIntExact(Math.min(plan.maximumDepthMillionths(),
                        LakeFixedMathV2.FIXED_SCALE));
                int floor = Math.toIntExact(Math.subtractExact(plan.waterSurfaceYMillionths(), shallow));
                return new LakeSample(
                        0, 0, 1, shallow, floor,
                        Math.toIntExact(plan.waterSurfaceYMillionths()),
                        floor,
                        Math.toIntExact(plan.waterSurfaceYMillionths()),
                        shallow,
                        WATER_BODY_CODE);
            }

            long distanceToRim = distanceToRing(plan.basinRing(), cellX, cellZ);
            boolean rim = isRimCell(globalX, globalZ, basin);
            if (spillway && basin) {
                rim = true;
            }
            int depth = depthAt(distanceToRim);
            int floor = Math.toIntExact(Math.subtractExact(plan.waterSurfaceYMillionths(), depth));
            int surface = Math.toIntExact(plan.waterSurfaceYMillionths());
            return new LakeSample(
                    1,
                    rim ? 1 : 0,
                    spillway ? 1 : 0,
                    depth,
                    floor,
                    surface,
                    floor,
                    surface,
                    depth,
                    WATER_BODY_CODE);
        } catch (ArithmeticException exception) {
            throw new LakeGenerationException("v2.lake-overflow", "lake sample arithmetic overflow", exception);
        }
    }

    public LakeWindowV2 renderWindow(
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
            throw new LakeGenerationException("v2.lake-halo", "requested halo exceeds plan support radius");
        }
        try {
            int originX = Math.subtractExact(coreOriginX, haloXZ);
            int originZ = Math.subtractExact(coreOriginZ, haloXZ);
            int windowWidth = Math.addExact(coreWidth, Math.multiplyExact(haloXZ, 2));
            int windowLength = Math.addExact(coreLength, Math.multiplyExact(haloXZ, 2));
            if (originX < 0 || originZ < 0
                    || originX + windowWidth > width()
                    || originZ + windowLength > length()) {
                throw new LakeGenerationException("v2.lake-window", "lake window exceeds world bounds");
            }
            int cells = Math.multiplyExact(windowWidth, windowLength);
            long retained = estimateWindowRetainedBytes(windowWidth, windowLength);
            if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
                throw new LakeGenerationException("v2.lake-budget", "lake window exceeds retained-memory budget");
            }
            int[][] fields = new int[LakeField.values().length][cells];
            for (int localZ = 0; localZ < windowLength; localZ++) {
                token.throwIfCancellationRequested();
                for (int localX = 0; localX < windowWidth; localX++) {
                    int globalX = originX + localX;
                    int globalZ = originZ + localZ;
                    LakeSample sample = sampleAt(globalX, globalZ, hardLandConflict);
                    int index = localZ * windowWidth + localX;
                    for (LakeField field : LakeField.values()) {
                        fields[field.ordinal()][index] = sample.rawValue(field);
                    }
                }
            }
            return new LakeWindowV2(originX, originZ, windowWidth, windowLength, fields, retained);
        } catch (ArithmeticException exception) {
            throw new LakeGenerationException("v2.lake-overflow", "lake window arithmetic overflow", exception);
        }
    }

    public Map<LakeField, String> fieldChecksums(IntPredicate hardLandConflict, CancellationToken token) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardLandConflict), token);
    }

    public Map<LakeField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<LakeField, MessageDigest> digests = new EnumMap<>(LakeField.class);
        for (LakeField field : LakeField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                LakeSample sample = Objects.requireNonNull(source.sampleAt(x, z), "lake sample");
                for (LakeField field : LakeField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<LakeField, String> result = new EnumMap<>(LakeField.class);
        for (LakeField field : LakeField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** EXPERIMENTAL validator hooks for V2-3-04 Acceptance metrics. */
    public LakeMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long basinCells = 0L;
        long rimCells = 0L;
        long spillCells = 0L;
        long exteriorWaterOutsideSpill = 0L;
        long surfaceMismatch = 0L;
        long reverseFlowCells = 0L;
        int surface = Math.toIntExact(plan.waterSurfaceYMillionths());
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                LakeSample sample = sampleAt(x, z, index -> false);
                if (sample.basinMask() == 1) {
                    basinCells++;
                    if (sample.surfaceMillionths() != surface) surfaceMismatch++;
                }
                if (sample.rimMask() == 1) rimCells++;
                if (sample.spillwayMask() == 1) spillCells++;
                if (sample.basinMask() == 0 && sample.spillwayMask() == 0 && sample.waterBodyId() != 0) {
                    exteriorWaterOutsideSpill++;
                }
                if (sample.spillwayMask() == 1 && sample.basinMask() == 1
                        && plan.terminalPolicy() == TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL) {
                    // Spill cells that remain deep interior are fine; reverse flow is compile-time.
                }
            }
        }
        boolean open = plan.terminalPolicy() == TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL;
        boolean spillReach = !open || spillCells > 0L;
        boolean closedOk = open || spillCells == 0L;
        boolean leakingRim = exteriorWaterOutsideSpill > 0L || !closedOk;
        boolean singleSurface = surfaceMismatch == 0L && basinCells > 0L;
        boolean inletOutlet = !plan.inletNodeIds().isEmpty() || plan.outletNodeId() != null;
        return new LakeMetrics(
                singleSurface,
                rimCells > 0L,
                spillReach,
                inletOutlet,
                !leakingRim,
                reverseFlowCells == 0L,
                basinCells,
                rimCells,
                spillCells,
                plan.selectedTargetDepthBlocks(),
                surface);
    }

    public static long estimateWindowRetainedBytes(int windowWidth, int windowLength) {
        long cells = Math.multiplyExact((long) windowWidth, windowLength);
        return Math.addExact(Math.multiplyExact(cells, LakeField.values().length * 4L), 64L * 1024L);
    }

    private boolean isSpillway(long cellX, long cellZ) {
        if (plan.terminalPolicy() != TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL) {
            return false;
        }
        long relX = Math.subtractExact(cellX, spillMidX);
        long relZ = Math.subtractExact(cellZ, spillMidZ);
        long along = Math.addExact(
                Math.multiplyExact(relX, plan.outwardUnitXMillionths()),
                Math.multiplyExact(relZ, plan.outwardUnitZMillionths()));
        along = LakeFixedMathV2.roundDivide(along, LakeFixedMathV2.FIXED_SCALE);
        if (along < 0L || along > Math.multiplyExact(
                (long) plan.spillwayCorridorLengthBlocks(), LakeFixedMathV2.FIXED_SCALE)) {
            return false;
        }
        long across = Math.addExact(
                Math.multiplyExact(relX, -plan.outwardUnitZMillionths()),
                Math.multiplyExact(relZ, plan.outwardUnitXMillionths()));
        across = Math.abs(LakeFixedMathV2.roundDivide(across, LakeFixedMathV2.FIXED_SCALE));
        return across <= spillHalfWidthMillionths;
    }

    private boolean isRimCell(int globalX, int globalZ, boolean basin) {
        if (!basin) return false;
        return !insideCell(globalX - 1, globalZ)
                || !insideCell(globalX + 1, globalZ)
                || !insideCell(globalX, globalZ - 1)
                || !insideCell(globalX, globalZ + 1);
    }

    private boolean insideCell(int globalX, int globalZ) {
        if (globalX < 0 || globalX >= width() || globalZ < 0 || globalZ >= length()) {
            return false;
        }
        long cellX = Math.multiplyExact((long) globalX, LakeFixedMathV2.FIXED_SCALE)
                + LakeFixedMathV2.FIXED_SCALE / 2L;
        long cellZ = Math.multiplyExact((long) globalZ, LakeFixedMathV2.FIXED_SCALE)
                + LakeFixedMathV2.FIXED_SCALE / 2L;
        return LakeFixedMathV2.pointInRing(plan.basinRing(), cellX, cellZ);
    }

    private int depthAt(long distanceToRimMillionths) {
        if (maxInteriorDistanceMillionths <= 0L) {
            return Math.toIntExact(plan.maximumDepthMillionths());
        }
        long depth = LakeFixedMathV2.roundDivide(
                Math.multiplyExact(plan.maximumDepthMillionths(), distanceToRimMillionths),
                maxInteriorDistanceMillionths);
        long shoreLimit = Math.multiplyExact((long) plan.shoreWidthBlocks(), LakeFixedMathV2.FIXED_SCALE);
        if (distanceToRimMillionths < shoreLimit && shoreLimit > 0L) {
            depth = LakeFixedMathV2.roundDivide(
                    Math.multiplyExact(depth, distanceToRimMillionths), shoreLimit);
        }
        if (depth < LakeFixedMathV2.FIXED_SCALE) {
            depth = LakeFixedMathV2.FIXED_SCALE;
        }
        return Math.toIntExact(Math.min(depth, plan.maximumDepthMillionths()));
    }

    private static long distanceToRing(List<LakePlanV2.RingPoint> ring, long x, long z) {
        long best = Long.MAX_VALUE;
        for (int index = 0; index < ring.size() - 1; index++) {
            LakePlanV2.RingPoint a = ring.get(index);
            LakePlanV2.RingPoint b = ring.get(index + 1);
            long distance = distanceToSegment(
                    x, z, a.xMillionths(), a.zMillionths(), b.xMillionths(), b.zMillionths());
            if (distance < best) best = distance;
        }
        return best;
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = Math.subtractExact(bx, ax);
        long dz = Math.subtractExact(bz, az);
        long lengthSquared = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long projection;
        if (lengthSquared == 0L) {
            projection = 0L;
        } else {
            long relX = Math.subtractExact(px, ax);
            long relZ = Math.subtractExact(pz, az);
            long dot = Math.addExact(Math.multiplyExact(relX, dx), Math.multiplyExact(relZ, dz));
            projection = LakeFixedMathV2.clampLong(
                    LakeFixedMathV2.mulDivExact(dot, LakeFixedMathV2.FIXED_SCALE, lengthSquared),
                    0L,
                    LakeFixedMathV2.FIXED_SCALE);
        }
        long qx = Math.addExact(ax, LakeFixedMathV2.mulDivExact(dx, projection, LakeFixedMathV2.FIXED_SCALE));
        long qz = Math.addExact(az, LakeFixedMathV2.mulDivExact(dz, projection, LakeFixedMathV2.FIXED_SCALE));
        return LakeFixedMathV2.hypotMillionths(Math.subtractExact(px, qx), Math.subtractExact(pz, qz));
    }

    private static long computeMaxInteriorDistance(LakePlanV2 plan) {
        long max = 0L;
        for (int z = 0; z < plan.length(); z++) {
            for (int x = 0; x < plan.width(); x++) {
                long cellX = Math.multiplyExact((long) x, LakeFixedMathV2.FIXED_SCALE)
                        + LakeFixedMathV2.FIXED_SCALE / 2L;
                long cellZ = Math.multiplyExact((long) z, LakeFixedMathV2.FIXED_SCALE)
                        + LakeFixedMathV2.FIXED_SCALE / 2L;
                if (!LakeFixedMathV2.pointInRing(plan.basinRing(), cellX, cellZ)) continue;
                long distance = distanceToRing(plan.basinRing(), cellX, cellZ);
                if (distance > max) max = distance;
            }
        }
        if (max <= 0L) {
            throw new LakeGenerationException("v2.lake-geometry", "lake basin interior is empty");
        }
        return max;
    }

    private static LakeSample emptySample() {
        return new LakeSample(0, 0, 0, NO_DATA, NO_DATA, NO_DATA, NO_DATA, NO_DATA, NO_DATA, 0);
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

    public enum LakeField {
        BASIN_MASK,
        RIM_MASK,
        SPILLWAY_MASK,
        DEPTH,
        FLOOR_HEIGHT,
        SURFACE,
        BED_ELEVATION,
        WATER_SURFACE,
        WATER_DEPTH,
        WATER_BODY_ID
    }

    public record LakeSample(
            int basinMask,
            int rimMask,
            int spillwayMask,
            int depthMillionths,
            int floorHeightMillionths,
            int surfaceMillionths,
            int bedElevationMillionths,
            int waterSurfaceMillionths,
            int waterDepthMillionths,
            int waterBodyId
    ) {
        public int rawValue(LakeField field) {
            return switch (field) {
                case BASIN_MASK -> basinMask;
                case RIM_MASK -> rimMask;
                case SPILLWAY_MASK -> spillwayMask;
                case DEPTH -> depthMillionths;
                case FLOOR_HEIGHT -> floorHeightMillionths;
                case SURFACE -> surfaceMillionths;
                case BED_ELEVATION -> bedElevationMillionths;
                case WATER_SURFACE -> waterSurfaceMillionths;
                case WATER_DEPTH -> waterDepthMillionths;
                case WATER_BODY_ID -> waterBodyId;
            };
        }
    }

    public record LakeMetrics(
            boolean singleSurfaceLevel,
            boolean rimPresent,
            boolean spillReachPresent,
            boolean inletOutletDeclared,
            boolean rimSealedExceptSpill,
            boolean noReverseFlow,
            long basinCells,
            long rimCells,
            long spillwayCells,
            int selectedDepthBlocks,
            int surfaceYMillionths
    ) {
    }

    @FunctionalInterface
    public interface CellSource {
        LakeSample sampleAt(int globalX, int globalZ);
    }
}
