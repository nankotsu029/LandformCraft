package com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;

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

/** Streaming, integer-only global-X/Z rasterizer for the V2-3-11 volcanic skeleton. */
public final class VolcanicGeneratorV2 {
    public static final String VERSION = "landform-volcanic-fixed-v1";
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 12L * 1024L * 1024L;
    private static final long SADDLE_HALF_WIDTH = 4L * TerrainIntentV2.FIXED_SCALE;

    private final VolcanicPlanV2 plan;

    public VolcanicGeneratorV2(VolcanicPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public VolcanicPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public VolcanicSample sampleAt(int x, int z, IntPredicate hardSeaConflict) {
        Objects.requireNonNull(hardSeaConflict, "hardSeaConflict");
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new VolcanicGenerationException("v2.volcanic-bounds", "sample outside bounds");
        }
        long px = x * TerrainIntentV2.FIXED_SCALE + TerrainIntentV2.FIXED_SCALE / 2L;
        long pz = z * TerrainIntentV2.FIXED_SCALE + TerrainIntentV2.FIXED_SCALE / 2L;
        NearestIsland nearest = nearestIsland(px, pz);
        VolcanicPlanV2.IslandMass island = nearest.island();
        long radius = (long) island.radiusBlocks() * TerrainIntentV2.FIXED_SCALE;
        boolean onIsland = nearest.distance() <= radius;
        boolean saddle = onSaddle(px, pz);
        if (onIsland && hardSeaConflict.test(z * width() + x)) {
            throw new VolcanicGenerationException(
                    "v2.volcanic-hard-sea-conflict", "HARD SEA conflicts with volcanic island raster");
        }
        long relief = 0L;
        int drainage = 0;
        if (onIsland) {
            long remaining = radius - nearest.distance();
            long height = (long) island.summitHeightBlocksAboveSea() * TerrainIntentV2.FIXED_SCALE;
            relief = height * remaining / radius;
            relief = relief * remaining / radius;
            drainage = 1;
        }
        long surface = (long) plan.waterLevel() * TerrainIntentV2.FIXED_SCALE + relief;
        if (!onIsland && saddle) {
            surface = ((long) plan.waterLevel() - plan.selectedSubmarineSaddleDepthBlocks())
                    * TerrainIntentV2.FIXED_SCALE;
        }
        if (onIsland && plan.calderaPlanHook() != null
                && island.pointId().equals(plan.calderaPlanHook().hostPointId())) {
            VolcanicPlanV2.CalderaPlanHook hook = plan.calderaPlanHook();
            long calderaDistance = VolcanicPlanCompilerV2.hypot(
                    px - island.xMillionths(), pz - island.zMillionths());
            long rimRadius = (long) hook.rimRadiusBlocks() * TerrainIntentV2.FIXED_SCALE;
            if (calderaDistance <= rimRadius) {
                long half = Math.max(TerrainIntentV2.FIXED_SCALE, rimRadius / 2L);
                if (calderaDistance <= half) {
                    surface = Math.max(
                            (long) plan.minY() * TerrainIntentV2.FIXED_SCALE,
                            surface - (long) hook.craterFloorDepthBlocks() * TerrainIntentV2.FIXED_SCALE);
                    drainage = 0;
                } else {
                    long rimWeight = TerrainIntentV2.FIXED_SCALE
                            - Math.abs(calderaDistance - (rimRadius * 3L / 4L))
                            * TerrainIntentV2.FIXED_SCALE / Math.max(1L, rimRadius / 4L);
                    if (rimWeight > 0) {
                        surface += (long) hook.rimReliefBlocks() * rimWeight;
                    }
                }
            }
        }
        return new VolcanicSample(
                onIsland ? 1 : 0,
                onIsland ? island.islandIndex() : 0,
                Math.toIntExact(relief),
                saddle ? 1 : 0,
                drainage,
                Math.toIntExact(surface));
    }

    public VolcanicWindowV2 renderWindow(
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
            throw new VolcanicGenerationException("v2.volcanic-halo", "halo exceeds plan support");
        }
        int startX = originX - halo;
        int startZ = originZ - halo;
        int renderedWidth = Math.addExact(windowWidth, Math.multiplyExact(halo, 2));
        int renderedLength = Math.addExact(windowLength, Math.multiplyExact(halo, 2));
        if (startX < 0 || startZ < 0
                || startX + renderedWidth > width() || startZ + renderedLength > length()) {
            throw new VolcanicGenerationException("v2.volcanic-window", "window exceeds bounds");
        }
        long retained = estimateWindowRetainedBytes(renderedWidth, renderedLength);
        if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
            throw new VolcanicGenerationException("v2.volcanic-budget", "volcanic window exceeds memory budget");
        }
        int[][] fields = new int[VolcanicField.values().length][Math.multiplyExact(renderedWidth, renderedLength)];
        for (int dz = 0; dz < renderedLength; dz++) {
            token.throwIfCancellationRequested();
            for (int dx = 0; dx < renderedWidth; dx++) {
                VolcanicSample sample = sampleAt(startX + dx, startZ + dz, hardSeaConflict);
                int index = dz * renderedWidth + dx;
                for (VolcanicField field : VolcanicField.values()) {
                    fields[field.ordinal()][index] = sample.rawValue(field);
                }
            }
        }
        return new VolcanicWindowV2(startX, startZ, renderedWidth, renderedLength, fields, retained);
    }

    public Map<VolcanicField, String> fieldChecksums(IntPredicate hardSeaConflict, CancellationToken token) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardSeaConflict), token);
    }

    public Map<VolcanicField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<VolcanicField, MessageDigest> digests = new EnumMap<>(VolcanicField.class);
        for (VolcanicField field : VolcanicField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                VolcanicSample sample = Objects.requireNonNull(source.sampleAt(x, z), "volcanic sample");
                for (VolcanicField field : VolcanicField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<VolcanicField, String> result = new EnumMap<>(VolcanicField.class);
        for (VolcanicField field : VolcanicField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    public VolcanicMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long islandCells = 0L;
        long saddleCells = 0L;
        for (int z = 0; z < length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width(); x++) {
                VolcanicSample sample = sampleAt(x, z, ignored -> false);
                islandCells += sample.islandMask();
                saddleCells += sample.submarineSaddleMask();
            }
        }
        boolean dryGap = minimumDryGapMillionths() >= 12L * TerrainIntentV2.FIXED_SCALE;
        boolean dominance = dominantAreaTwice() >= secondAreaTimesThree();
        return new VolcanicMetrics(
                plan.islands().size(), dryGap, dominance, dryGap && islandCells > 0,
                islandCells, saddleCells);
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        return Math.addExact(
                Math.multiplyExact(Math.multiplyExact((long) width, length), VolcanicField.values().length * 4L),
                64L * 1024L);
    }

    private NearestIsland nearestIsland(long px, long pz) {
        VolcanicPlanV2.IslandMass best = plan.islands().getFirst();
        long bestDistance = Long.MAX_VALUE;
        for (VolcanicPlanV2.IslandMass island : plan.islands()) {
            long distance = VolcanicPlanCompilerV2.hypot(
                    px - island.xMillionths(), pz - island.zMillionths());
            if (distance < bestDistance
                    || (distance == bestDistance && island.pointId().compareTo(best.pointId()) < 0)) {
                best = island;
                bestDistance = distance;
            }
        }
        return new NearestIsland(best, bestDistance);
    }

    private boolean onSaddle(long px, long pz) {
        for (VolcanicPlanV2.SubmarineSaddle saddle : plan.saddles()) {
            VolcanicPlanV2.IslandMass from = island(saddle.fromPointId());
            VolcanicPlanV2.IslandMass to = island(saddle.toPointId());
            if (distanceToSegment(px, pz, from.xMillionths(), from.zMillionths(),
                    to.xMillionths(), to.zMillionths()) <= SADDLE_HALF_WIDTH) {
                return true;
            }
        }
        return false;
    }

    private VolcanicPlanV2.IslandMass island(String pointId) {
        return plan.islands().stream().filter(value -> value.pointId().equals(pointId)).findFirst()
                .orElseThrow(() -> new VolcanicGenerationException(
                        "v2.volcanic-unknown-point", "saddle references unknown island"));
    }

    private long minimumDryGapMillionths() {
        long minimum = Long.MAX_VALUE;
        for (int i = 0; i < plan.islands().size(); i++) {
            for (int j = i + 1; j < plan.islands().size(); j++) {
                VolcanicPlanV2.IslandMass a = plan.islands().get(i);
                VolcanicPlanV2.IslandMass b = plan.islands().get(j);
                long radii = (long) (a.radiusBlocks() + b.radiusBlocks()) * TerrainIntentV2.FIXED_SCALE;
                minimum = Math.min(minimum, VolcanicPlanCompilerV2.hypot(
                        a.xMillionths() - b.xMillionths(), a.zMillionths() - b.zMillionths()) - radii);
            }
        }
        return minimum;
    }

    private long dominantAreaTwice() {
        VolcanicPlanV2.IslandMass dominant = plan.islands().get(plan.dominantIslandIndex());
        return 2L * dominant.radiusBlocks() * dominant.radiusBlocks();
    }

    private long secondAreaTimesThree() {
        long second = plan.islands().stream()
                .filter(island -> island.islandIndex() != plan.islands().get(plan.dominantIslandIndex()).islandIndex())
                .mapToLong(island -> (long) island.radiusBlocks() * island.radiusBlocks())
                .max().orElse(0L);
        return 3L * second;
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = bx - ax;
        long dz = bz - az;
        long denominator = dx * dx + dz * dz;
        long projection = denominator == 0 ? 0 : Math.max(0, Math.min(TerrainIntentV2.FIXED_SCALE,
                ((px - ax) * dx + (pz - az) * dz) * TerrainIntentV2.FIXED_SCALE / denominator));
        long qx = ax + dx * projection / TerrainIntentV2.FIXED_SCALE;
        long qz = az + dz * projection / TerrainIntentV2.FIXED_SCALE;
        return VolcanicPlanCompilerV2.hypot(px - qx, pz - qz);
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
        VolcanicSample sampleAt(int x, int z);
    }

    public enum VolcanicField {
        ISLAND_MASK, ISLAND_INDEX, SUMMIT_RELIEF, SUBMARINE_SADDLE_MASK, RADIAL_DRAINAGE, PROVISIONAL_SURFACE
    }

    public record VolcanicSample(
            int islandMask,
            int islandIndex,
            int summitReliefMillionths,
            int submarineSaddleMask,
            int radialDrainage,
            int provisionalSurfaceMillionths
    ) {
        public int rawValue(VolcanicField field) {
            return switch (field) {
                case ISLAND_MASK -> islandMask;
                case ISLAND_INDEX -> islandIndex;
                case SUMMIT_RELIEF -> summitReliefMillionths;
                case SUBMARINE_SADDLE_MASK -> submarineSaddleMask;
                case RADIAL_DRAINAGE -> radialDrainage;
                case PROVISIONAL_SURFACE -> provisionalSurfaceMillionths;
            };
        }
    }

    public record VolcanicMetrics(
            int componentCount,
            boolean dryGapOk,
            boolean dominanceOk,
            boolean marineSeparationOk,
            long islandCells,
            long saddleCells
    ) {
    }

    private record NearestIsland(VolcanicPlanV2.IslandMass island, long distance) {
    }
}
