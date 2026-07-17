package com.github.nankotsu029.landformcraft.generator.v2.landform.fjord;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.FjordPlanV2;
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

/** Streaming, integer-only global-X/Z rasterizer for the V2-3-09 fjord profile. */
public final class FjordGeneratorV2 {
    public static final String VERSION = "landform-fjord-fixed-v1";
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 12L * 1024L * 1024L;
    private final FjordPlanV2 plan;
    private final long channelRadius;
    private final long floorRadius;
    private final long rimRadius;

    public FjordGeneratorV2(FjordPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        channelRadius = Math.multiplyExact((long) plan.selectedSurfaceWidthBlocks(), TerrainIntentV2.FIXED_SCALE) / 4L;
        floorRadius = Math.multiplyExact((long) plan.selectedSurfaceWidthBlocks(), TerrainIntentV2.FIXED_SCALE) / 3L;
        rimRadius = Math.multiplyExact((long) plan.selectedSurfaceWidthBlocks(), TerrainIntentV2.FIXED_SCALE) / 2L;
    }

    public FjordPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public FjordSample sampleAt(int x, int z, IntPredicate hardLandConflict) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FjordGenerationException("v2.fjord-bounds", "sample outside bounds");
        }
        long px = x * TerrainIntentV2.FIXED_SCALE + TerrainIntentV2.FIXED_SCALE / 2L;
        long pz = z * TerrainIntentV2.FIXED_SCALE + TerrainIntentV2.FIXED_SCALE / 2L;
        long distance = nearest(px, pz);
        if (distance > rimRadius) {
            return new FjordSample(0, 0, 0, NO_DATA, NO_DATA);
        }
        boolean channel = distance <= channelRadius;
        boolean floor = distance <= floorRadius;
        boolean wall = !floor;
        if ((channel || floor || wall) && hardLandConflict.test(z * width() + x)) {
            throw new FjordGenerationException(
                    "v2.fjord-hard-boundary-conflict", "HARD land boundary conflicts with fjord raster");
        }
        int depth = channel
                ? Math.toIntExact((long) plan.selectedChannelDepthBlocks() * TerrainIntentV2.FIXED_SCALE)
                : NO_DATA;
        long relief = 0L;
        if (wall) {
            long span = rimRadius - floorRadius;
            relief = Math.min(
                    (long) plan.selectedSidewallReliefBlocks() * TerrainIntentV2.FIXED_SCALE,
                    (distance - floorRadius)
                            * (long) plan.selectedSidewallReliefBlocks()
                            * TerrainIntentV2.FIXED_SCALE
                            / Math.max(1L, span));
        }
        return new FjordSample(channel ? 1 : 0, floor ? 1 : 0, wall ? 1 : 0, depth, Math.toIntExact(relief));
    }

    public FjordWindowV2 renderWindow(
            int originX,
            int originZ,
            int windowWidth,
            int windowLength,
            int halo,
            IntPredicate hardLandConflict,
            CancellationToken token
    ) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        Objects.requireNonNull(token, "token");
        if (halo < 0 || halo > plan.supportRadiusXZ()) {
            throw new FjordGenerationException("v2.fjord-halo", "halo exceeds plan support");
        }
        int x = originX - halo;
        int z = originZ - halo;
        int w = windowWidth + 2 * halo;
        int l = windowLength + 2 * halo;
        if (x < 0 || z < 0 || x + w > width() || z + l > length()) {
            throw new FjordGenerationException("v2.fjord-window", "window exceeds bounds");
        }
        long retained = estimateWindowRetainedBytes(w, l);
        if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
            throw new FjordGenerationException("v2.fjord-budget", "fjord window exceeds memory budget");
        }
        int[][] fields = new int[FjordField.values().length][Math.multiplyExact(w, l)];
        for (int dz = 0; dz < l; dz++) {
            token.throwIfCancellationRequested();
            for (int dx = 0; dx < w; dx++) {
                FjordSample sample = sampleAt(x + dx, z + dz, hardLandConflict);
                int i = dz * w + dx;
                for (FjordField field : FjordField.values()) {
                    fields[field.ordinal()][i] = sample.rawValue(field);
                }
            }
        }
        return new FjordWindowV2(x, z, w, l, fields, retained);
    }

    public Map<FjordField, String> fieldChecksums(IntPredicate hardLandConflict, CancellationToken token) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardLandConflict), token);
    }

    public Map<FjordField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<FjordField, MessageDigest> digests = new EnumMap<>(FjordField.class);
        for (FjordField field : FjordField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                FjordSample sample = Objects.requireNonNull(source.sampleAt(x, z), "fjord sample");
                for (FjordField field : FjordField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<FjordField, String> result = new EnumMap<>(FjordField.class);
        for (FjordField field : FjordField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    public FjordMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long channels = 0;
        long floors = 0;
        long walls = 0;
        long reliefTotal = 0;
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                FjordSample sample = sampleAt(x, z, ignored -> false);
                channels += sample.channelMask();
                floors += sample.floorMask();
                walls += sample.sidewallMask();
                reliefTotal += Math.max(0, sample.sidewallReliefMillionths());
            }
        }
        FjordPlanV2.CenterlinePoint mouth = plan.centerline().getFirst();
        int mouthX = Math.toIntExact(mouth.xMillionths() / TerrainIntentV2.FIXED_SCALE);
        int mouthZ = Math.toIntExact(mouth.zMillionths() / TerrainIntentV2.FIXED_SCALE);
        boolean seaConnected = sampleAt(mouthX, mouthZ, ignored -> false).channelMask() == 1;
        long total = plan.centerline().getLast().arcLengthMillionths();
        long slenderness = total / Math.multiplyExact((long) plan.selectedSurfaceWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
        return new FjordMetrics(
                seaConnected,
                slenderness,
                slenderness >= 5 && slenderness <= 14,
                floors > channels && walls > 0,
                walls == 0 ? 0 : reliefTotal / walls,
                channels,
                floors,
                walls);
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        return Math.addExact(
                Math.multiplyExact(Math.multiplyExact((long) width, length), FjordField.values().length * 4L),
                64L * 1024L);
    }

    private long nearest(long px, long pz) {
        List<FjordPlanV2.CenterlinePoint> points = plan.centerline();
        long result = Long.MAX_VALUE;
        for (int i = 1; i < points.size(); i++) {
            FjordPlanV2.CenterlinePoint a = points.get(i - 1);
            FjordPlanV2.CenterlinePoint b = points.get(i);
            long dx = b.xMillionths() - a.xMillionths();
            long dz = b.zMillionths() - a.zMillionths();
            long d2 = dx * dx + dz * dz;
            long projection = 0;
            if (d2 != 0) {
                projection = Math.max(0, Math.min(TerrainIntentV2.FIXED_SCALE,
                        ((px - a.xMillionths()) * dx + (pz - a.zMillionths()) * dz) * TerrainIntentV2.FIXED_SCALE / d2));
            }
            long qx = a.xMillionths() + dx * projection / TerrainIntentV2.FIXED_SCALE;
            long qz = a.zMillionths() + dz * projection / TerrainIntentV2.FIXED_SCALE;
            result = Math.min(result, FjordPlanCompilerV2.hypot(px - qx, pz - qz));
        }
        return result;
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
        FjordSample sampleAt(int x, int z);
    }

    public enum FjordField {
        CHANNEL_MASK,
        FLOOR_MASK,
        SIDEWALL_MASK,
        THALWEG_DEPTH,
        SIDEWALL_RELIEF
    }

    public record FjordSample(
            int channelMask,
            int floorMask,
            int sidewallMask,
            int thalwegDepthMillionths,
            int sidewallReliefMillionths
    ) {
        public int rawValue(FjordField field) {
            return switch (field) {
                case CHANNEL_MASK -> channelMask;
                case FLOOR_MASK -> floorMask;
                case SIDEWALL_MASK -> sidewallMask;
                case THALWEG_DEPTH -> thalwegDepthMillionths;
                case SIDEWALL_RELIEF -> sidewallReliefMillionths;
            };
        }
    }

    public record FjordMetrics(
            boolean seaConnected,
            long slendernessBlocks,
            boolean slendernessOk,
            boolean uProfileOk,
            long sidewallReliefMedian,
            long channelCells,
            long floorCells,
            long sidewallCells
    ) {
    }
}
