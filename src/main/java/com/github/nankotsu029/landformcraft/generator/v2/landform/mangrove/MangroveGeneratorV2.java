package com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.MangroveWetlandPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

/** Streaming, integer-only global-X/Z rasterizer for the V2-4-09 mangrove wetland profile. */
public final class MangroveGeneratorV2 {
    public static final String VERSION = "landform-mangrove-fixed-v1";
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 12L * 1024L * 1024L;

    private final MangroveWetlandPlanV2 plan;
    private final String seedNamespace;

    public MangroveGeneratorV2(MangroveWetlandPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.seedNamespace = "mangrove:" + plan.featureId();
    }

    public MangroveWetlandPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public MangroveSample sampleAt(int x, int z, IntPredicate hardLandConflict, BooleanSupplier tidalChannelAt) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        Objects.requireNonNull(tidalChannelAt, "tidalChannelAt");
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new MangroveGenerationException("v2.mangrove-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        if (!MangroveFixedMathV2.contains(plan.rings(), px, pz)) {
            return MangroveSample.outside();
        }
        if (hardLandConflict.test(index(x, z))) {
            throw new MangroveGenerationException(
                    "v2.mangrove-hard-boundary-conflict", "HARD land boundary conflicts with mangrove raster");
        }
        if (tidalChannelAt.getAsBoolean()) {
            return new MangroveSample(
                    1,
                    plan.waterLevel(),
                    1,
                    0,
                    0);
        }
        int microRelief = selectedMicroReliefBlocks(x, z);
        boolean waterlogged = MangroveFixedMathV2.cellHash(seedNamespace, x, z)
                < plan.selectedWaterloggedShareMillionths();
        if (waterlogged) {
            return new MangroveSample(
                    1,
                    plan.waterLevel(),
                    1,
                    MangroveWetlandPlanV2.SUBSTRATE_SEDIMENT_WET,
                    0);
        }
        int surfaceHeight = microRelief == 0 ? plan.waterLevel() : plan.waterLevel() + microRelief;
        return new MangroveSample(
                1,
                surfaceHeight,
                0,
                MangroveWetlandPlanV2.SUBSTRATE_SEDIMENT_WET,
                microRelief);
    }

    public MangroveWindowV2 renderWindow(
            int originX,
            int originZ,
            int windowWidth,
            int windowLength,
            int halo,
            IntPredicate hardLandConflict,
            BiPredicate<Integer, Integer> tidalChannelAt,
            CancellationToken token
    ) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        Objects.requireNonNull(tidalChannelAt, "tidalChannelAt");
        Objects.requireNonNull(token, "token");
        if (halo < 0 || halo > plan.supportRadiusXZ()) {
            throw new MangroveGenerationException("v2.mangrove-halo", "halo exceeds plan support");
        }
        int x = originX - halo;
        int z = originZ - halo;
        int w = windowWidth + 2 * halo;
        int l = windowLength + 2 * halo;
        if (x < 0 || z < 0 || x + w > width() || z + l > length()) {
            throw new MangroveGenerationException("v2.mangrove-window", "window exceeds bounds");
        }
        long retained = estimateWindowRetainedBytes(w, l);
        if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
            throw new MangroveGenerationException("v2.mangrove-budget", "mangrove window exceeds memory budget");
        }
        int[][] fields = new int[MangroveField.values().length][Math.multiplyExact(w, l)];
        for (int dz = 0; dz < l; dz++) {
            token.throwIfCancellationRequested();
            for (int dx = 0; dx < w; dx++) {
                int sampleX = x + dx;
                int sampleZ = z + dz;
                MangroveSample sample = sampleAt(
                        sampleX, sampleZ, hardLandConflict, () -> tidalChannelAt.test(sampleX, sampleZ));
                int i = dz * w + dx;
                for (MangroveField field : MangroveField.values()) {
                    fields[field.ordinal()][i] = sample.rawValue(field);
                }
            }
        }
        return new MangroveWindowV2(x, z, w, l, fields, retained);
    }

    public Map<MangroveField, String> fieldChecksums(
            IntPredicate hardLandConflict,
            BiPredicate<Integer, Integer> tidalChannelAt,
            CancellationToken token
    ) {
        return fieldChecksumsFrom(
                (x, z) -> sampleAt(x, z, hardLandConflict, () -> tidalChannelAt.test(x, z)),
                token);
    }

    public Map<MangroveField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<MangroveField, MessageDigest> digests = new EnumMap<>(MangroveField.class);
        for (MangroveField field : MangroveField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                MangroveSample sample = Objects.requireNonNull(source.sampleAt(x, z), "mangrove sample");
                for (MangroveField field : MangroveField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<MangroveField, String> result = new EnumMap<>(MangroveField.class);
        for (MangroveField field : MangroveField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    public MangroveMetrics evaluate(
            IntPredicate hardLandConflict,
            BiPredicate<Integer, Integer> tidalChannelAt,
            CancellationToken token
    ) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        Objects.requireNonNull(tidalChannelAt, "tidalChannelAt");
        Objects.requireNonNull(token, "token");
        long wetlandCells = 0L;
        long openWaterGapCount = 0L;
        int maxMicroReliefUsed = 0;
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                final int sampleX = x;
                final int sampleZ = z;
                MangroveSample sample = sampleAt(
                        sampleX, sampleZ, hardLandConflict, () -> tidalChannelAt.test(sampleX, sampleZ));
                if (sample.wetlandMask() != 1) continue;
                wetlandCells++;
                openWaterGapCount += sample.openWaterGap();
                maxMicroReliefUsed = Math.max(maxMicroReliefUsed, sample.microReliefBlocks());
            }
        }
        boolean marineConnected = plan.tidalNetworkHook() != null;
        if (wetlandCells > 0L && !marineConnected && openWaterGapCount == 0L) {
            throw new MangroveGenerationException(
                    "v2.mangrove-dry-wetland",
                    "mangrove wetland lacks tidal connection and open-water gaps");
        }
        return new MangroveMetrics(
                marineConnected,
                maxMicroReliefUsed,
                openWaterGapCount,
                wetlandCells,
                plan.estimatedRasterWorkUnits());
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        return Math.addExact(
                Math.multiplyExact(Math.multiplyExact((long) width, length), MangroveField.values().length * 4L),
                64L * 1024L);
    }

    private int selectedMicroReliefBlocks(int x, int z) {
        if (plan.selectedMicroReliefBlocks() <= 0) {
            return 0;
        }
        long hash = MangroveFixedMathV2.cellHash(seedNamespace, x, z);
        return Math.toIntExact(hash % plan.selectedMicroReliefBlocks());
    }

    private int index(int x, int z) {
        return Math.addExact(Math.multiplyExact(z, width()), x);
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
        MangroveSample sampleAt(int x, int z);
    }

    public enum MangroveField {
        WETLAND_MASK,
        SURFACE_HEIGHT,
        OPEN_WATER_GAP,
        SUBSTRATE_CLASS,
        MICRO_RELIEF
    }

    public record MangroveSample(
            int wetlandMask,
            int surfaceHeightBlocks,
            int openWaterGap,
            int substrateClass,
            int microReliefBlocks
    ) {
        static MangroveSample outside() {
            return new MangroveSample(0, NO_DATA, 0, 0, NO_DATA);
        }

        public int rawValue(MangroveField field) {
            return switch (field) {
                case WETLAND_MASK -> wetlandMask;
                case SURFACE_HEIGHT -> surfaceHeightBlocks;
                case OPEN_WATER_GAP -> openWaterGap;
                case SUBSTRATE_CLASS -> substrateClass;
                case MICRO_RELIEF -> microReliefBlocks;
            };
        }
    }

    public record MangroveMetrics(
            boolean marineConnected,
            int reliefP50Blocks,
            long openWaterGapCount,
            long wetlandCellCount,
            long estimatedRasterWorkUnits
    ) {
    }
}
