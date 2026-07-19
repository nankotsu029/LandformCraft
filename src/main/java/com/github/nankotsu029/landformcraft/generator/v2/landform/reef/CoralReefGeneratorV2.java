package com.github.nankotsu029.landformcraft.generator.v2.landform.reef;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.CoralReefPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

/** Streaming, integer-only global-X/Z rasterizer for the V2-4-10 coral reef bathymetry profile. */
public final class CoralReefGeneratorV2 {
    public static final String VERSION = "landform-reef-fixed-v1";
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 12L * 1024L * 1024L;

    private final CoralReefPlanV2 plan;

    public CoralReefGeneratorV2(CoralReefPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public CoralReefPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public CoralReefSample sampleAt(int x, int z, IntPredicate hardLandConflict) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new CoralReefGenerationException("v2.reef-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        CoralReefPlanV2.ReefPassPlanHook pass = matchingPass(px, pz);
        boolean insideOuter = CoralReefFixedMathV2.insideOuter(plan.rings(), px, pz);
        if (!insideOuter && pass == null) {
            return CoralReefSample.outside();
        }
        if (insideOuter && hardLandConflict.test(index(x, z))) {
            throw new CoralReefGenerationException(
                    "v2.reef-hard-boundary-conflict", "HARD land boundary conflicts with coral reef raster");
        }
        if (pass != null) {
            boolean inLagoon = CoralReefFixedMathV2.inLagoon(plan.rings(), px, pz);
            return new CoralReefSample(
                    0,
                    inLagoon ? NO_DATA : pass.selectedDepthBlocks(),
                    inLagoon ? pass.selectedDepthBlocks() : NO_DATA,
                    1,
                    marineConnected() ? 1 : 0);
        }
        if (CoralReefFixedMathV2.inLagoon(plan.rings(), px, pz)) {
            return new CoralReefSample(
                    0,
                    NO_DATA,
                    plan.selectedLagoonDepthBlocks(),
                    0,
                    marineConnected() ? 1 : 0);
        }
        int crestDepth = reefCrestDepthBlocks(px, pz);
        return new CoralReefSample(1, crestDepth, NO_DATA, 0, 0);
    }

    public CoralReefWindowV2 renderWindow(
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
            throw new CoralReefGenerationException("v2.reef-halo", "halo exceeds plan support");
        }
        int x = originX - halo;
        int z = originZ - halo;
        int w = windowWidth + 2 * halo;
        int l = windowLength + 2 * halo;
        if (x < 0 || z < 0 || x + w > width() || z + l > length()) {
            throw new CoralReefGenerationException("v2.reef-window", "window exceeds bounds");
        }
        long retained = estimateWindowRetainedBytes(w, l);
        if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
            throw new CoralReefGenerationException("v2.reef-budget", "coral reef window exceeds memory budget");
        }
        int[][] fields = new int[CoralReefField.values().length][Math.multiplyExact(w, l)];
        for (int dz = 0; dz < l; dz++) {
            token.throwIfCancellationRequested();
            for (int dx = 0; dx < w; dx++) {
                CoralReefSample sample = sampleAt(x + dx, z + dz, hardLandConflict);
                int i = dz * w + dx;
                for (CoralReefField field : CoralReefField.values()) {
                    fields[field.ordinal()][i] = sample.rawValue(field);
                }
            }
        }
        return new CoralReefWindowV2(x, z, w, l, fields, retained);
    }

    public Map<CoralReefField, String> fieldChecksums(IntPredicate hardLandConflict, CancellationToken token) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardLandConflict), token);
    }

    public Map<CoralReefField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<CoralReefField, MessageDigest> digests = new EnumMap<>(CoralReefField.class);
        for (CoralReefField field : CoralReefField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                CoralReefSample sample = Objects.requireNonNull(source.sampleAt(x, z), "coral reef sample");
                for (CoralReefField field : CoralReefField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<CoralReefField, String> result = new EnumMap<>(CoralReefField.class);
        for (CoralReefField field : CoralReefField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    public CoralReefMetrics evaluate(IntPredicate hardLandConflict, CancellationToken token) {
        Objects.requireNonNull(hardLandConflict, "hardLandConflict");
        Objects.requireNonNull(token, "token");
        long reefCells = 0L;
        long lagoonCells = 0L;
        long passCorridorCells = 0L;
        int maxCrestDepth = 0;
        int minCrestDepth = Integer.MAX_VALUE;
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                CoralReefSample sample = sampleAt(x, z, hardLandConflict);
                if (sample.reefMask() == 1) {
                    reefCells++;
                    if (sample.crestDepthBlocks() != NO_DATA) {
                        maxCrestDepth = Math.max(maxCrestDepth, sample.crestDepthBlocks());
                        minCrestDepth = Math.min(minCrestDepth, sample.crestDepthBlocks());
                    }
                }
                if (sample.lagoonDepthBlocks() != NO_DATA) {
                    lagoonCells++;
                }
                passCorridorCells += sample.passCorridor();
            }
        }
        if (maxCrestDepth > plan.selectedLagoonDepthBlocks()) {
            throw new CoralReefGenerationException(
                    "v2.reef-deep-reef",
                    "coral reef crest depth exceeds lagoon depth envelope");
        }
        boolean marineConnected = marineConnected();
        boolean sealedLagoon = plan.passHooks().isEmpty();
        if (minCrestDepth == Integer.MAX_VALUE) {
            minCrestDepth = 0;
        }
        return new CoralReefMetrics(
                marineConnected,
                sealedLagoon,
                plan.passHooks().size(),
                minCrestDepth,
                maxCrestDepth,
                plan.selectedLagoonDepthBlocks(),
                reefCells,
                lagoonCells,
                passCorridorCells,
                plan.estimatedRasterWorkUnits());
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        return Math.addExact(
                Math.multiplyExact(Math.multiplyExact((long) width, length), CoralReefField.values().length * 4L),
                64L * 1024L);
    }

    private boolean marineConnected() {
        return !plan.passHooks().isEmpty();
    }

    private CoralReefPlanV2.ReefPassPlanHook matchingPass(long px, long pz) {
        for (CoralReefPlanV2.ReefPassPlanHook pass : plan.passHooks()) {
            if (passCorridorMask(pass, px, pz)) {
                return pass;
            }
        }
        return null;
    }

    private boolean passCorridorMask(CoralReefPlanV2.ReefPassPlanHook pass, long px, long pz) {
        long halfWidth = Math.multiplyExact((long) pass.selectedWidthBlocks(), TerrainIntentV2.FIXED_SCALE) / 2L;
        return CoralReefFixedMathV2.nearestCenterlineDistance(pass.centerline(), px, pz) <= halfWidth;
    }

    private int reefCrestDepthBlocks(long px, long pz) {
        long innerDist = CoralReefFixedMathV2.distanceToHoleBoundary(plan.rings(), px, pz);
        long widthM = Math.multiplyExact((long) plan.selectedReefWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
        long fraction = widthM == 0L ? 0L : Math.min(TerrainIntentV2.FIXED_SCALE, innerDist * TerrainIntentV2.FIXED_SCALE / widthM);
        int depthAtInner = plan.selectedCrestDepthBlocks();
        int depthAtOuter = Math.min(
                plan.selectedLagoonDepthBlocks(),
                depthAtInner + Math.max(1, plan.selectedReefWidthBlocks() / 6)
                        + Math.max(0, plan.selectedOuterSlopeDegrees() / 21));
        return depthAtInner + Math.toIntExact((long) (depthAtOuter - depthAtInner) * fraction / TerrainIntentV2.FIXED_SCALE);
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
        CoralReefSample sampleAt(int x, int z);
    }

    public enum CoralReefField {
        REEF_MASK,
        CREST_DEPTH,
        LAGOON_DEPTH,
        PASS_CORRIDOR,
        MARINE_CONNECTION
    }

    public record CoralReefSample(
            int reefMask,
            int crestDepthBlocks,
            int lagoonDepthBlocks,
            int passCorridor,
            int marineConnection
    ) {
        static CoralReefSample outside() {
            return new CoralReefSample(0, NO_DATA, NO_DATA, 0, 0);
        }

        public int rawValue(CoralReefField field) {
            return switch (field) {
                case REEF_MASK -> reefMask;
                case CREST_DEPTH -> crestDepthBlocks;
                case LAGOON_DEPTH -> lagoonDepthBlocks;
                case PASS_CORRIDOR -> passCorridor;
                case MARINE_CONNECTION -> marineConnection;
            };
        }
    }

    public record CoralReefMetrics(
            boolean marineConnected,
            boolean sealedLagoon,
            int passCount,
            int crestDepthMinBlocks,
            int crestDepthMaxBlocks,
            int lagoonDepthBlocks,
            long reefCellCount,
            long lagoonCellCount,
            long passCorridorCellCount,
            long estimatedRasterWorkUnits
    ) {
    }
}
