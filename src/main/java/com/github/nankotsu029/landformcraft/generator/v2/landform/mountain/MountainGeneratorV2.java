package com.github.nankotsu029.landformcraft.generator.v2.landform.mountain;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.MountainPlanV2;
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

/** Streaming, integer-only global-X/Z rasterizer for the V2-3-10 mountain ridge skeleton. */
public final class MountainGeneratorV2 {
    public static final String VERSION = "landform-mountain-fixed-v1";
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 12L * 1024L * 1024L;

    private final MountainPlanV2 plan;
    private final long ridgeRadius;
    private final long peakRadius;
    private final long saddleRadius;
    private final long spurRadius;

    public MountainGeneratorV2(MountainPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        ridgeRadius = Math.multiplyExact((long) plan.selectedRidgeHalfWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
        peakRadius = Math.max(TerrainIntentV2.FIXED_SCALE, ridgeRadius / 3L);
        saddleRadius = Math.max(TerrainIntentV2.FIXED_SCALE, ridgeRadius / 4L);
        spurRadius = Math.max(TerrainIntentV2.FIXED_SCALE, ridgeRadius / 2L);
    }

    public MountainPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public MountainSample sampleAt(int x, int z, IntPredicate hardSeaConflict) {
        Objects.requireNonNull(hardSeaConflict, "hardSeaConflict");
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new MountainGenerationException("v2.mountain-bounds", "sample outside bounds");
        }
        long px = x * TerrainIntentV2.FIXED_SCALE + TerrainIntentV2.FIXED_SCALE / 2L;
        long pz = z * TerrainIntentV2.FIXED_SCALE + TerrainIntentV2.FIXED_SCALE / 2L;
        NearestRidge nearest = nearestRidge(px, pz);
        NearestSpur spur = nearestSpur(px, pz);
        long distance = Math.min(nearest.distance(), spur.distance());
        boolean ridge = nearest.distance() <= ridgeRadius;
        boolean spurMask = spur.distance() <= spurRadius;
        boolean peak = nearestPeak(px, pz) <= peakRadius;
        boolean saddle = nearestSaddle(px, pz) <= saddleRadius;
        if ((ridge || spurMask || peak || saddle) && hardSeaConflict.test(z * width() + x)) {
            throw new MountainGenerationException(
                    "v2.mountain-hard-coast-conflict", "HARD SEA conflicts with mountain raster");
        }
        int surface = NO_DATA;
        if (distance <= ridgeRadius || spur.distance() <= spurRadius) {
            long active = distance <= ridgeRadius ? nearest.distance() : spur.distance();
            long radius = distance <= ridgeRadius ? ridgeRadius : spurRadius;
            long falloff = radius == 0 ? 0 : active * TerrainIntentV2.FIXED_SCALE / radius;
            long sharpness = plan.ridgeSharpnessMillionths();
            long shaped = falloff * falloff / TerrainIntentV2.FIXED_SCALE;
            shaped = shaped * sharpness / TerrainIntentV2.FIXED_SCALE
                    + falloff * (TerrainIntentV2.FIXED_SCALE - sharpness) / TerrainIntentV2.FIXED_SCALE;
            long relief = Math.multiplyExact((long) plan.selectedMaxReliefBlocks(), TerrainIntentV2.FIXED_SCALE);
            long delta = relief - relief * shaped / TerrainIntentV2.FIXED_SCALE;
            surface = Math.toIntExact(Math.multiplyExact((long) plan.waterLevel(), TerrainIntentV2.FIXED_SCALE) + delta);
        }
        return new MountainSample(
                ridge ? 1 : 0,
                peak ? 1 : 0,
                saddle ? 1 : 0,
                spurMask ? 1 : 0,
                surface,
                ridge ? nearest.segmentId() : (spurMask ? spur.segmentId() : 0));
    }

    public MountainWindowV2 renderWindow(
            int originX,
            int originZ,
            int windowWidth,
            int windowLength,
            int halo,
            IntPredicate hardSeaConflict,
            CancellationToken token
    ) {
        Objects.requireNonNull(hardSeaConflict, "hardSeaConflict");
        Objects.requireNonNull(token, "token");
        if (halo < 0 || halo > plan.supportRadiusXZ()) {
            throw new MountainGenerationException("v2.mountain-halo", "halo exceeds plan support");
        }
        int x = originX - halo;
        int z = originZ - halo;
        int w = windowWidth + 2 * halo;
        int l = windowLength + 2 * halo;
        if (x < 0 || z < 0 || x + w > width() || z + l > length()) {
            throw new MountainGenerationException("v2.mountain-window", "window exceeds bounds");
        }
        long retained = estimateWindowRetainedBytes(w, l);
        if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
            throw new MountainGenerationException("v2.mountain-budget", "mountain window exceeds memory budget");
        }
        int[][] fields = new int[MountainField.values().length][Math.multiplyExact(w, l)];
        for (int dz = 0; dz < l; dz++) {
            token.throwIfCancellationRequested();
            for (int dx = 0; dx < w; dx++) {
                MountainSample sample = sampleAt(x + dx, z + dz, hardSeaConflict);
                int i = dz * w + dx;
                for (MountainField field : MountainField.values()) {
                    fields[field.ordinal()][i] = sample.rawValue(field);
                }
            }
        }
        return new MountainWindowV2(x, z, w, l, fields, retained);
    }

    public Map<MountainField, String> fieldChecksums(IntPredicate hardSeaConflict, CancellationToken token) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardSeaConflict), token);
    }

    public Map<MountainField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<MountainField, MessageDigest> digests = new EnumMap<>(MountainField.class);
        for (MountainField field : MountainField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                MountainSample sample = Objects.requireNonNull(source.sampleAt(x, z), "mountain sample");
                for (MountainField field : MountainField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<MountainField, String> result = new EnumMap<>(MountainField.class);
        for (MountainField field : MountainField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    public MountainMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long ridgeCells = 0;
        long peakCells = 0;
        long saddleCells = 0;
        long spurCells = 0;
        long drainageOk = 0;
        long drainageChecked = 0;
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                MountainSample sample = sampleAt(x, z, ignored -> false);
                ridgeCells += sample.ridgeMask();
                peakCells += sample.peakMask();
                saddleCells += sample.saddleMask();
                spurCells += sample.spurMask();
                if (sample.ridgeMask() == 1 && sample.provisionalSurfaceMillionths() != NO_DATA) {
                    drainageChecked++;
                    int nx = Math.min(width() - 1, x + 1);
                    MountainSample neighbor = sampleAt(nx, z, ignored -> false);
                    if (neighbor.provisionalSurfaceMillionths() == NO_DATA
                            || neighbor.provisionalSurfaceMillionths() <= sample.provisionalSurfaceMillionths()) {
                        drainageOk++;
                    }
                }
            }
        }
        boolean peakOrder = true;
        long previous = -1L;
        for (MountainPlanV2.NamedStation peak : plan.peaks()) {
            if (peak.arcLengthMillionths() <= previous) {
                peakOrder = false;
                break;
            }
            previous = peak.arcLengthMillionths();
        }
        return new MountainMetrics(
                ridgeCells > 0,
                peakOrder && peakCells > 0,
                plan.selectedMaxReliefBlocks() > 0 && ridgeCells > 0,
                drainageChecked == 0 || drainageOk * 2 >= drainageChecked,
                ridgeCells,
                peakCells,
                saddleCells,
                spurCells);
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        return Math.addExact(
                Math.multiplyExact(Math.multiplyExact((long) width, length), MountainField.values().length * 4L),
                64L * 1024L);
    }

    private NearestRidge nearestRidge(long px, long pz) {
        List<MountainPlanV2.RidgePoint> points = plan.ridge();
        long best = Long.MAX_VALUE;
        int segmentId = 1;
        for (int i = 1; i < points.size(); i++) {
            MountainPlanV2.RidgePoint a = points.get(i - 1);
            MountainPlanV2.RidgePoint b = points.get(i);
            long distance = distanceToSegment(px, pz, a.xMillionths(), a.zMillionths(), b.xMillionths(), b.zMillionths());
            if (distance < best) {
                best = distance;
                segmentId = b.segmentId();
            }
        }
        return new NearestRidge(best, segmentId);
    }

    private NearestSpur nearestSpur(long px, long pz) {
        long best = Long.MAX_VALUE;
        int segmentId = 0;
        int index = 1;
        for (MountainPlanV2.SpurSegment spur : plan.spurs()) {
            long distance = distanceToSegment(
                    px, pz,
                    spur.originXMillionths(), spur.originZMillionths(),
                    spur.tipXMillionths(), spur.tipZMillionths());
            if (distance < best) {
                best = distance;
                segmentId = 1000 + index;
            }
            index++;
        }
        return new NearestSpur(best, segmentId);
    }

    private long nearestPeak(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (MountainPlanV2.NamedStation peak : plan.peaks()) {
            best = Math.min(best, MountainPlanCompilerV2.hypot(px - peak.xMillionths(), pz - peak.zMillionths()));
        }
        return best;
    }

    private long nearestSaddle(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (MountainPlanV2.NamedStation saddle : plan.saddles()) {
            best = Math.min(best, MountainPlanCompilerV2.hypot(px - saddle.xMillionths(), pz - saddle.zMillionths()));
        }
        return best;
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = bx - ax;
        long dz = bz - az;
        long d2 = dx * dx + dz * dz;
        long projection = 0;
        if (d2 != 0) {
            projection = Math.max(0, Math.min(TerrainIntentV2.FIXED_SCALE,
                    ((px - ax) * dx + (pz - az) * dz) * TerrainIntentV2.FIXED_SCALE / d2));
        }
        long qx = ax + dx * projection / TerrainIntentV2.FIXED_SCALE;
        long qz = az + dz * projection / TerrainIntentV2.FIXED_SCALE;
        return MountainPlanCompilerV2.hypot(px - qx, pz - qz);
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

    @FunctionalInterface
    public interface CellSource {
        MountainSample sampleAt(int x, int z);
    }

    public enum MountainField {
        RIDGE_MASK,
        PEAK_MASK,
        SADDLE_MASK,
        SPUR_MASK,
        PROVISIONAL_SURFACE,
        RIDGE_SEGMENT_ID
    }

    public record MountainSample(
            int ridgeMask,
            int peakMask,
            int saddleMask,
            int spurMask,
            int provisionalSurfaceMillionths,
            int ridgeSegmentId
    ) {
        public int rawValue(MountainField field) {
            return switch (field) {
                case RIDGE_MASK -> ridgeMask;
                case PEAK_MASK -> peakMask;
                case SADDLE_MASK -> saddleMask;
                case SPUR_MASK -> spurMask;
                case PROVISIONAL_SURFACE -> provisionalSurfaceMillionths;
                case RIDGE_SEGMENT_ID -> ridgeSegmentId;
            };
        }
    }

    public record MountainMetrics(
            boolean ridgeContinuous,
            boolean peakOrderOk,
            boolean reliefOk,
            boolean drainageHandoffOk,
            long ridgeCells,
            long peakCells,
            long saddleCells,
            long spurCells
    ) {
    }

    private record NearestRidge(long distance, int segmentId) {
    }

    private record NearestSpur(long distance, int segmentId) {
    }
}
