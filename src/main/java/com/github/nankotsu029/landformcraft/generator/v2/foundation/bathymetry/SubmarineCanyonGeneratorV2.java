package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SubmarineCanyonPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-9-09 submarine-canyon foundation carve. */
public final class SubmarineCanyonGeneratorV2 {
    public static final String VERSION = "foundation-submarine-canyon-fixed-v1";

    private final SubmarineCanyonPlanV2 plan;
    private final List<SubmarineCanyonPlanV2.CenterlineSample> samples;
    private final long floorHalfMillionths;
    private final long rimHalfMillionths;
    private final long wallSpanMillionths;
    private final long carveMillionths;

    public SubmarineCanyonGeneratorV2(SubmarineCanyonPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.samples = plan.centerline();
        this.floorHalfMillionths = Math.multiplyExact(
                (long) (plan.selectedFloorWidthBlocks() + 1) / 2, SubmarineCanyonFixedMathV2.FIXED_SCALE);
        this.rimHalfMillionths = Math.multiplyExact(
                (long) (plan.selectedRimWidthBlocks() + 1) / 2, SubmarineCanyonFixedMathV2.FIXED_SCALE);
        this.wallSpanMillionths = Math.subtractExact(rimHalfMillionths, floorHalfMillionths);
        this.carveMillionths = Math.multiplyExact(
                (long) plan.selectedAdditionalCarveDepthBlocks(), SubmarineCanyonFixedMathV2.FIXED_SCALE);
        if (wallSpanMillionths < 2L * SubmarineCanyonFixedMathV2.FIXED_SCALE) {
            throw new FoundationSliceException("v2.submarine-canyon-thin-wall",
                    "submarine canyon wall span collapsed");
        }
    }

    public SubmarineCanyonPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public SubmarineCanyonSampleV2 sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.submarine-canyon-bounds", "sample outside bounds");
        }
        long cellX = Math.addExact(Math.multiplyExact((long) x, SubmarineCanyonFixedMathV2.FIXED_SCALE),
                SubmarineCanyonFixedMathV2.FIXED_SCALE / 2L);
        long cellZ = Math.addExact(Math.multiplyExact((long) z, SubmarineCanyonFixedMathV2.FIXED_SCALE),
                SubmarineCanyonFixedMathV2.FIXED_SCALE / 2L);
        Nearest nearest = nearestOnCenterline(cellX, cellZ);
        if (nearest.distanceMillionths() > rimHalfMillionths) {
            return SubmarineCanyonSampleV2.outside(plan.waterLevel());
        }
        long profile = carveProfileMillionths(nearest.distanceMillionths());
        long additional = SubmarineCanyonFixedMathV2.mulDivExact(
                carveMillionths, profile, SubmarineCanyonFixedMathV2.FIXED_SCALE);
        int hostBase = Math.max(1, nearest.sample().floorDepthBlocksBelowSea()
                - plan.selectedAdditionalCarveDepthBlocks());
        int floorDepth = Math.toIntExact(Math.addExact(hostBase,
                SubmarineCanyonFixedMathV2.roundDivide(additional, SubmarineCanyonFixedMathV2.FIXED_SCALE)));
        floorDepth = Math.max(floorDepth, hostBase + (profile > 0L ? 1 : 0));
        // Prefer centerline floor depth on the floor corridor for continuity with plan samples.
        if (nearest.distanceMillionths() <= floorHalfMillionths) {
            floorDepth = nearest.sample().floorDepthBlocksBelowSea();
        }
        int floorY = plan.waterLevel() - floorDepth;
        int fluidTop = floorY < plan.waterLevel() ? plan.waterLevel() : floorY;
        return new SubmarineCanyonSampleV2(
                1,
                floorDepth,
                1,
                nearest.sample().hostRole().code(),
                fluidTop,
                floorY);
    }

    public Map<SubmarineCanyonSampleV2.SubmarineCanyonField, String> fieldChecksums() {
        return fieldChecksumsFrom(this::sampleAt);
    }

    public Map<SubmarineCanyonSampleV2.SubmarineCanyonField, String> fieldChecksumsFrom(CellSource source) {
        Objects.requireNonNull(source, "source");
        EnumMap<SubmarineCanyonSampleV2.SubmarineCanyonField, MessageDigest> digests =
                new EnumMap<>(SubmarineCanyonSampleV2.SubmarineCanyonField.class);
        for (SubmarineCanyonSampleV2.SubmarineCanyonField field
                : SubmarineCanyonSampleV2.SubmarineCanyonField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width());
            updateInt(digest, length());
            digests.put(field, digest);
        }
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                SubmarineCanyonSampleV2 sample = Objects.requireNonNull(source.sampleAt(x, z), "sample");
                for (SubmarineCanyonSampleV2.SubmarineCanyonField field
                        : SubmarineCanyonSampleV2.SubmarineCanyonField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<SubmarineCanyonSampleV2.SubmarineCanyonField, String> result =
                new EnumMap<>(SubmarineCanyonSampleV2.SubmarineCanyonField.class);
        for (SubmarineCanyonSampleV2.SubmarineCanyonField field
                : SubmarineCanyonSampleV2.SubmarineCanyonField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<SubmarineCanyonSampleV2.SubmarineCanyonField, String> tiledFieldChecksums(int tileSize) {
        if (tileSize < 1) {
            throw new IllegalArgumentException("tileSize must be positive");
        }
        int cells = Math.multiplyExact(width(), length());
        int[][] values = new int[SubmarineCanyonSampleV2.SubmarineCanyonField.values().length][cells];
        boolean[] covered = new boolean[cells];
        for (int originZ = 0; originZ < length(); originZ += tileSize) {
            for (int originX = 0; originX < width(); originX += tileSize) {
                int endX = Math.min(width(), originX + tileSize);
                int endZ = Math.min(length(), originZ + tileSize);
                for (int z = originZ; z < endZ; z++) {
                    for (int x = originX; x < endX; x++) {
                        int index = z * width() + x;
                        covered[index] = true;
                        SubmarineCanyonSampleV2 sample = sampleAt(x, z);
                        for (SubmarineCanyonSampleV2.SubmarineCanyonField field
                                : SubmarineCanyonSampleV2.SubmarineCanyonField.values()) {
                            values[field.ordinal()][index] = sample.rawValue(field);
                        }
                    }
                }
            }
        }
        for (boolean cell : covered) {
            if (!cell) {
                throw new IllegalStateException("tiled submarine canyon coverage incomplete");
            }
        }
        return fieldChecksumsFrom((x, z) -> {
            int index = z * width() + x;
            return new SubmarineCanyonSampleV2(
                    values[SubmarineCanyonSampleV2.SubmarineCanyonField.MASK.ordinal()][index],
                    values[SubmarineCanyonSampleV2.SubmarineCanyonField.FLOOR_DEPTH.ordinal()][index],
                    values[SubmarineCanyonSampleV2.SubmarineCanyonField.OWNERSHIP.ordinal()][index],
                    values[SubmarineCanyonSampleV2.SubmarineCanyonField.HOST_HANDOFF.ordinal()][index],
                    values[SubmarineCanyonSampleV2.SubmarineCanyonField.FLUID_COLUMN_HINT.ordinal()][index],
                    plan.waterLevel()
                            - values[SubmarineCanyonSampleV2.SubmarineCanyonField.FLOOR_DEPTH.ordinal()][index]);
        });
    }

    public String underwaterColumnExportChecksum() {
        MessageDigest digest = sha256();
        digest.update((VERSION + "\0underwater-column\0").getBytes(StandardCharsets.UTF_8));
        updateInt(digest, width());
        updateInt(digest, length());
        updateInt(digest, plan.waterLevel());
        updateInt(digest, plan.minY());
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                SubmarineCanyonSampleV2 sample = sampleAt(x, z);
                if (!sample.owned()) {
                    digest.update(BathymetrySampleV2.TAG_EMPTY);
                    continue;
                }
                int floorY = sample.floorY();
                updateInt(digest, floorY);
                int top = Math.max(floorY, plan.waterLevel());
                for (int y = plan.minY(); y <= top; y++) {
                    byte tag;
                    if (y <= floorY) {
                        tag = BathymetrySampleV2.TAG_SOLID;
                    } else if (y <= plan.waterLevel()) {
                        tag = BathymetrySampleV2.TAG_FLUID;
                    } else {
                        tag = BathymetrySampleV2.TAG_EMPTY;
                    }
                    digest.update(tag);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public SubmarineCanyonMetrics evaluate() {
        boolean headContained = samples.getFirst().hostRole() == SubmarineCanyonPlanV2.HostRole.HEAD_SHELF;
        boolean outletContained = samples.getLast().hostRole() == SubmarineCanyonPlanV2.HostRole.OUTLET_BASIN;
        boolean slopeCrossingOk = samples.stream()
                .anyMatch(sample -> sample.hostRole() == SubmarineCanyonPlanV2.HostRole.SLOPE_CROSSING);
        boolean downGradientOk = true;
        for (int index = 1; index < samples.size(); index++) {
            if (samples.get(index).floorDepthBlocksBelowSea()
                    < samples.get(index - 1).floorDepthBlocksBelowSea()) {
                downGradientOk = false;
                break;
            }
        }
        long owned = 0L;
        boolean floorDepthOk = true;
        boolean fluidSolidOk = true;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                SubmarineCanyonSampleV2 sample = sampleAt(x, z);
                if (!sample.owned()) {
                    continue;
                }
                owned++;
                if (sample.floorDepthBlocksBelowSea() < 1
                        || sample.floorDepthBlocksBelowSea() > 512
                        || sample.floorY() != plan.waterLevel() - sample.floorDepthBlocksBelowSea()) {
                    floorDepthOk = false;
                }
                if (sample.fluidColumnHintTopY() < sample.floorY()) {
                    fluidSolidOk = false;
                }
                if (sample.floorY() < plan.waterLevel()
                        && sample.fluidColumnHintTopY() != plan.waterLevel()) {
                    fluidSolidOk = false;
                }
            }
        }
        Map<SubmarineCanyonSampleV2.SubmarineCanyonField, String> whole = fieldChecksums();
        Map<SubmarineCanyonSampleV2.SubmarineCanyonField, String> tiled = tiledFieldChecksums(16);
        boolean wholeTileOk = whole.equals(tiled);
        boolean budgetOk = owned > 0 && plan.estimatedRasterWorkUnits()
                <= SubmarineCanyonPlanV2.MAXIMUM_RASTER_WORK_UNITS;
        return new SubmarineCanyonMetrics(
                headContained,
                outletContained,
                slopeCrossingOk,
                downGradientOk,
                floorDepthOk && owned > 0,
                fluidSolidOk,
                wholeTileOk,
                budgetOk);
    }

    private long carveProfileMillionths(long distanceMillionths) {
        if (distanceMillionths <= floorHalfMillionths) {
            return SubmarineCanyonFixedMathV2.FIXED_SCALE;
        }
        long span = Math.subtractExact(distanceMillionths, floorHalfMillionths);
        long normalized = SubmarineCanyonFixedMathV2.mulDivExact(
                span, SubmarineCanyonFixedMathV2.FIXED_SCALE, wallSpanMillionths);
        normalized = SubmarineCanyonFixedMathV2.clampLong(
                normalized, 0L, SubmarineCanyonFixedMathV2.FIXED_SCALE);
        long shaped = switch (plan.crossSection()) {
            case V, TERRACED_V -> normalized;
            case U, TERRACED_U -> SubmarineCanyonFixedMathV2.mulDivExact(
                    normalized, normalized, SubmarineCanyonFixedMathV2.FIXED_SCALE);
        };
        return Math.subtractExact(SubmarineCanyonFixedMathV2.FIXED_SCALE, shaped);
    }

    private Nearest nearestOnCenterline(long cellX, long cellZ) {
        long bestDistance = Long.MAX_VALUE;
        SubmarineCanyonPlanV2.CenterlineSample best = samples.getFirst();
        for (int index = 0; index < samples.size() - 1; index++) {
            SubmarineCanyonPlanV2.CenterlineSample a = samples.get(index);
            SubmarineCanyonPlanV2.CenterlineSample b = samples.get(index + 1);
            SegmentNearest segment = nearestOnSegment(
                    cellX, cellZ,
                    a.xMillionths(), a.zMillionths(),
                    b.xMillionths(), b.zMillionths());
            if (segment.distanceMillionths() < bestDistance) {
                bestDistance = segment.distanceMillionths();
                long t = segment.tMillionths();
                int depth = Math.toIntExact(a.floorDepthBlocksBelowSea()
                        + SubmarineCanyonFixedMathV2.roundDivide(
                        Math.multiplyExact(
                                (long) b.floorDepthBlocksBelowSea() - a.floorDepthBlocksBelowSea(), t),
                        SubmarineCanyonFixedMathV2.FIXED_SCALE));
                SubmarineCanyonPlanV2.HostRole role = t < SubmarineCanyonFixedMathV2.FIXED_SCALE / 2L
                        ? a.hostRole() : b.hostRole();
                best = new SubmarineCanyonPlanV2.CenterlineSample(
                        a.sequence(),
                        a.xMillionths() + SubmarineCanyonFixedMathV2.roundDivide(
                                Math.multiplyExact(b.xMillionths() - a.xMillionths(), t),
                                SubmarineCanyonFixedMathV2.FIXED_SCALE),
                        a.zMillionths() + SubmarineCanyonFixedMathV2.roundDivide(
                                Math.multiplyExact(b.zMillionths() - a.zMillionths(), t),
                                SubmarineCanyonFixedMathV2.FIXED_SCALE),
                        a.arcLengthMillionths() + SubmarineCanyonFixedMathV2.roundDivide(
                                Math.multiplyExact(b.arcLengthMillionths() - a.arcLengthMillionths(), t),
                                SubmarineCanyonFixedMathV2.FIXED_SCALE),
                        role,
                        depth);
            }
        }
        return new Nearest(best, bestDistance);
    }

    private static SegmentNearest nearestOnSegment(
            long px, long pz, long ax, long az, long bx, long bz
    ) {
        long dx = Math.subtractExact(bx, ax);
        long dz = Math.subtractExact(bz, az);
        long lengthSquared = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long tMillionths;
        if (lengthSquared == 0L) {
            tMillionths = 0L;
        } else {
            long projection = Math.addExact(
                    Math.multiplyExact(Math.subtractExact(px, ax), dx),
                    Math.multiplyExact(Math.subtractExact(pz, az), dz));
            tMillionths = SubmarineCanyonFixedMathV2.clampLong(
                    SubmarineCanyonFixedMathV2.mulDivExact(
                            projection, SubmarineCanyonFixedMathV2.FIXED_SCALE, lengthSquared),
                    0L, SubmarineCanyonFixedMathV2.FIXED_SCALE);
        }
        long qx = Math.addExact(ax, SubmarineCanyonFixedMathV2.roundDivide(
                Math.multiplyExact(dx, tMillionths), SubmarineCanyonFixedMathV2.FIXED_SCALE));
        long qz = Math.addExact(az, SubmarineCanyonFixedMathV2.roundDivide(
                Math.multiplyExact(dz, tMillionths), SubmarineCanyonFixedMathV2.FIXED_SCALE));
        long distance = SubmarineCanyonFixedMathV2.hypotMillionths(
                Math.subtractExact(px, qx), Math.subtractExact(pz, qz));
        return new SegmentNearest(distance, tMillionths);
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

    @FunctionalInterface
    public interface CellSource {
        SubmarineCanyonSampleV2 sampleAt(int x, int z);
    }

    public record SubmarineCanyonMetrics(
            boolean headContained,
            boolean outletContained,
            boolean slopeCrossingOk,
            boolean downGradientOk,
            boolean floorDepthOk,
            boolean fluidSolidConflictFree,
            boolean wholeTileOk,
            boolean budgetOk
    ) {
    }

    private record Nearest(SubmarineCanyonPlanV2.CenterlineSample sample, long distanceMillionths) {
    }

    private record SegmentNearest(long distanceMillionths, long tMillionths) {
    }
}
