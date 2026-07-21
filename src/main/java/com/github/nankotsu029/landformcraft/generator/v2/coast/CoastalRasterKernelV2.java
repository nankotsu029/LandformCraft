package com.github.nankotsu029.landformcraft.generator.v2.coast;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.field.FieldValueSource;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Integer-only V2-2-02 coast raster kernel. It samples release-local global X/Z and never owns a
 * whole-world dense array. Signed distance, normals, and nearshore depth use millionths.
 */
public final class CoastalRasterKernelV2 {
    public static final String VERSION = "coast-raster-fixed-v1";
    public static final int FIXED_SCALE = 1_000_000;
    public static final int GEOMETRY_SCALE = 4_096;
    public static final int CURVE_SUBDIVISIONS = 16;
    public static final int MAXIMUM_CONTROL_POINTS = 128;
    public static final int MAXIMUM_HALO_XZ = 64;
    public static final int MAXIMUM_CORE_EXTENT = 256;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 8L * 1024L * 1024L;
    public static final int NEARSHORE_NO_DATA = Integer.MIN_VALUE;

    private final CoastalFeaturePlanV2 plan;
    private final int width;
    private final int length;
    private final List<Segment> segments;
    private final int maximumDistanceMillionths;
    private final int nearshoreDistanceMillionths;
    private final int nearshoreTargetMillionths;

    public CoastalRasterKernelV2(CoastalFeaturePlanV2 plan, int width, int length) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
            throw new CoastalRasterException("v2.coastal-raster-dimensions", "dimensions must be within 1.." + ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING);
        }
        if (plan.geometryRole() != CoastalFeaturePlanV2.GeometryRole.COASTLINE
                || plan.signedDistance().sign() != CoastalFeaturePlanV2.DistanceSign.POSITIVE_ON_LAND_SIDE) {
            throw new CoastalRasterException(
                    "v2.coastal-raster-role",
                    "V2-2-02 raster supports only the common COASTLINE contract");
        }
        if (plan.supportRadiusXZ() > MAXIMUM_HALO_XZ) {
            throw new CoastalRasterException(
                    "v2.coastal-raster-support", "coastal support exceeds the declared kernel halo");
        }
        this.width = width;
        this.length = length;
        try {
            this.maximumDistanceMillionths = Math.multiplyExact(
                    plan.signedDistance().maximumDistanceBlocks(), FIXED_SCALE);
            this.nearshoreDistanceMillionths = Math.multiplyExact(
                    plan.nearshoreProfile().distanceBlocks(), FIXED_SCALE);
            this.nearshoreTargetMillionths = Math.multiplyExact(
                    plan.nearshoreProfile().targetDepthBlocks(), FIXED_SCALE);
            this.segments = flatten(plan);
        } catch (ArithmeticException exception) {
            throw new CoastalRasterException(
                    "v2.coastal-raster-overflow", "coastal geometry exceeds fixed-point bounds", exception);
        }
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public CoastalFeaturePlanV2 plan() {
        return plan;
    }

    public RasterSample sampleAt(int globalX, int globalZ) {
        return sampleAt(globalX, globalZ, HardLandWaterSourceV2.NONE);
    }

    public RasterSample sampleAt(
            int globalX,
            int globalZ,
            HardLandWaterSourceV2 hardSource
    ) {
        requireCoordinate(globalX, globalZ);
        Objects.requireNonNull(hardSource, "hardSource");
        try {
            long sampleX = Math.multiplyExact((long) globalX, GEOMETRY_SCALE);
            long sampleZ = Math.multiplyExact((long) globalZ, GEOMETRY_SCALE);
            Candidate nearest = null;
            for (Segment segment : segments) {
                Candidate candidate = nearest(segment, sampleX, sampleZ);
                if (nearest == null || candidate.distanceSquared() < nearest.distanceSquared()) {
                    nearest = candidate;
                }
            }
            if (nearest == null) {
                throw new CoastalRasterException(
                        "v2.coastal-raster-degenerate", "coastal path has no sampleable segment");
            }
            Segment segment = nearest.segment();
            long cross = Math.subtractExact(
                    Math.multiplyExact(segment.dx(), Math.subtractExact(sampleZ, segment.az())),
                    Math.multiplyExact(segment.dz(), Math.subtractExact(sampleX, segment.ax())));
            int side = Long.compare(cross, 0L);
            boolean landLeft = plan.coastSide() == CoastalFeaturePlanV2.CoastSide.LAND_LEFT;
            int landSign = landLeft ? -side : side;
            long distanceQ = integerSquareRoot(nearest.distanceSquared());
            long distanceMillionths = roundDivide(
                    Math.multiplyExact(distanceQ, FIXED_SCALE), GEOMETRY_SCALE);
            int magnitude = Math.toIntExact(Math.min(distanceMillionths, maximumDistanceMillionths));
            int signedDistance = landSign < 0 ? -magnitude : magnitude;

            long normalXNumerator = landLeft ? segment.dz() : -segment.dz();
            long normalZNumerator = landLeft ? -segment.dx() : segment.dx();
            long tangentLength = integerSquareRoot(segment.lengthSquared());
            int normalX = Math.toIntExact(roundDivide(
                    Math.multiplyExact(normalXNumerator, FIXED_SCALE), tangentLength));
            int normalZ = Math.toIntExact(roundDivide(
                    Math.multiplyExact(normalZNumerator, FIXED_SCALE), tangentLength));

            int coastSide = signedDistance >= 0 ? 1 : 0;
            HardLandWaterSourceV2.Classification hard = Objects.requireNonNull(
                    hardSource.classificationAt(globalX, globalZ), "hard classification");
            int actual = hard == HardLandWaterSourceV2.Classification.UNSPECIFIED
                    ? coastSide : hard.rawValue();
            int nearshore = nearshoreDepth(signedDistance, actual);
            return new RasterSample(actual, coastSide, signedDistance, normalX, normalZ, nearshore,
                    hard != HardLandWaterSourceV2.Classification.UNSPECIFIED);
        } catch (ArithmeticException exception) {
            throw new CoastalRasterException(
                    "v2.coastal-raster-overflow", "coastal raster arithmetic overflow", exception);
        }
    }

    public CoastalRasterWindowV2.Bounds windowBounds(
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int haloXZ
    ) {
        if (coreOriginX < 0 || coreOriginZ < 0 || coreWidth < 1 || coreLength < 1
                || coreWidth > MAXIMUM_CORE_EXTENT || coreLength > MAXIMUM_CORE_EXTENT
                || (long) coreOriginX + coreWidth > width || (long) coreOriginZ + coreLength > length) {
            throw new CoastalRasterException(
                    "v2.coastal-raster-window", "core window is outside bounds or exceeds 256 cells");
        }
        if (haloXZ < 0 || haloXZ > MAXIMUM_HALO_XZ) {
            throw new CoastalRasterException(
                    "v2.coastal-raster-support", "window halo is outside 0..64");
        }
        int originX = Math.max(0, coreOriginX - haloXZ);
        int originZ = Math.max(0, coreOriginZ - haloXZ);
        int endX = Math.min(width, Math.addExact(Math.addExact(coreOriginX, coreWidth), haloXZ));
        int endZ = Math.min(length, Math.addExact(Math.addExact(coreOriginZ, coreLength), haloXZ));
        return new CoastalRasterWindowV2.Bounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength,
                originX, originZ, endX - originX, endZ - originZ, haloXZ);
    }

    public CoastalRasterWindowV2 renderWindow(
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int haloXZ,
            HardLandWaterSourceV2 hardSource,
            CancellationToken token
    ) {
        Objects.requireNonNull(hardSource, "hardSource");
        Objects.requireNonNull(token, "token");
        CoastalRasterWindowV2.Bounds bounds = windowBounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength, haloXZ);
        try {
            int cells = Math.multiplyExact(bounds.width(), bounds.length());
            long retainedBytes = estimateWindowRetainedBytes(bounds.width(), bounds.length());
            if (retainedBytes > MAXIMUM_WINDOW_RETAINED_BYTES) {
                throw new CoastalRasterException(
                        "v2.coastal-raster-budget", "coastal window exceeds retained-memory budget");
            }
            int[][] fields = new int[RasterField.values().length][cells];
            byte[] hard = new byte[cells];
            for (int localZ = 0; localZ < bounds.length(); localZ++) {
                token.throwIfCancellationRequested();
                int globalZ = bounds.originZ() + localZ;
                for (int localX = 0; localX < bounds.width(); localX++) {
                    int globalX = bounds.originX() + localX;
                    RasterSample sample = sampleAt(globalX, globalZ, hardSource);
                    int index = localZ * bounds.width() + localX;
                    for (RasterField field : RasterField.values()) {
                        fields[field.ordinal()][index] = sample.rawValue(field);
                    }
                    hard[index] = sample.hardConstrained() ? (byte) 1 : 0;
                }
            }
            return new CoastalRasterWindowV2(bounds, fields, hard, retainedBytes);
        } catch (ArithmeticException exception) {
            throw new CoastalRasterException(
                    "v2.coastal-raster-overflow", "coastal window arithmetic overflow", exception);
        }
    }

    public FieldValueSource fieldValueSource(RasterField field, HardLandWaterSourceV2 hardSource) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(hardSource, "hardSource");
        return (x, z) -> sampleAt(x, z, hardSource).rawValue(field);
    }

    public Map<RasterField, String> fieldChecksums(
            HardLandWaterSourceV2 hardSource,
            CancellationToken token
    ) {
        return fieldChecksumsFrom((x, z) -> sampleAt(x, z, hardSource), token);
    }

    /** Canonical row-major checksum over any whole/tile-backed sampler. */
    public Map<RasterField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<RasterField, MessageDigest> digests = new EnumMap<>(RasterField.class);
        for (RasterField field : RasterField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width);
            updateInt(digest, length);
            digests.put(field, digest);
        }
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                RasterSample sample = Objects.requireNonNull(source.sampleAt(x, z), "raster sample");
                for (RasterField field : RasterField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<RasterField, String> result = new EnumMap<>(RasterField.class);
        for (RasterField field : RasterField.values()) {
            result.put(field, java.util.HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        if (width < 1 || length < 1) throw new IllegalArgumentException("window dimensions must be positive");
        long cells = Math.multiplyExact((long) width, length);
        long fieldBytes = Math.multiplyExact(cells, (long) RasterField.values().length * Integer.BYTES);
        return Math.addExact(64L * 1024L, Math.addExact(fieldBytes, cells));
    }

    private int nearshoreDepth(int signedDistance, int actualLandWater) {
        if (plan.nearshoreProfile().kind() == CoastalFeaturePlanV2.NearshoreProfileKind.NONE
                || actualLandWater == 1 || signedDistance >= 0) {
            return NEARSHORE_NO_DATA;
        }
        long distance = -(long) signedDistance;
        long scaled = roundDivide(Math.multiplyExact(distance, nearshoreTargetMillionths),
                nearshoreDistanceMillionths);
        return Math.toIntExact(Math.min(scaled, nearshoreTargetMillionths));
    }

    private void requireCoordinate(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("coordinate outside coastal raster");
        }
    }

    private static List<Segment> flatten(CoastalFeaturePlanV2 plan) {
        List<CoastalFeaturePlanV2.BlockPath> paths = plan.geometry().paths();
        if (paths.size() != 1) {
            throw new CoastalRasterException(
                    "v2.coastal-raster-geometry", "COASTLINE raster requires exactly one path");
        }
        CoastalFeaturePlanV2.BlockPath path = paths.getFirst();
        if (path.points().size() > MAXIMUM_CONTROL_POINTS) {
            throw new CoastalRasterException(
                    "v2.coastal-raster-budget", "coast path exceeds control-point budget");
        }
        List<Point> points = path.points().stream().map(CoastalRasterKernelV2::quantize).toList();
        for (int index = 1; index < points.size(); index++) {
            if (points.get(index - 1).equals(points.get(index))) {
                throw new CoastalRasterException(
                        "v2.coastal-raster-degenerate", "coast points collapse at fixed precision");
            }
        }
        List<Segment> result = new ArrayList<>();
        if (path.interpolation() == TerrainIntentV2.Interpolation.POLYLINE) {
            for (int index = 1; index < points.size(); index++) {
                result.add(segment(points.get(index - 1), points.get(index)));
            }
        } else {
            for (int index = 0; index < points.size() - 1; index++) {
                Point p0 = points.get(Math.max(0, index - 1));
                Point p1 = points.get(index);
                Point p2 = points.get(index + 1);
                Point p3 = points.get(Math.min(points.size() - 1, index + 2));
                Point b1 = new Point(
                        Math.addExact(p1.x(), roundDivide(Math.subtractExact(p2.x(), p0.x()), 6)),
                        Math.addExact(p1.z(), roundDivide(Math.subtractExact(p2.z(), p0.z()), 6)));
                Point b2 = new Point(
                        Math.subtractExact(p2.x(), roundDivide(Math.subtractExact(p3.x(), p1.x()), 6)),
                        Math.subtractExact(p2.z(), roundDivide(Math.subtractExact(p3.z(), p1.z()), 6)));
                Point previous = p1;
                for (int step = 1; step <= CURVE_SUBDIVISIONS; step++) {
                    Point next = bezier(p1, b1, b2, p2, step);
                    if (previous.equals(next)) {
                        throw new CoastalRasterException(
                                "v2.coastal-raster-degenerate", "curve segment collapses at fixed precision");
                    }
                    result.add(segment(previous, next));
                    previous = next;
                }
            }
        }
        return List.copyOf(result);
    }

    private static Point quantize(CoastalFeaturePlanV2.BlockPoint point) {
        return new Point(
                roundDivide(Math.multiplyExact(point.xMillionths(), GEOMETRY_SCALE), FIXED_SCALE),
                roundDivide(Math.multiplyExact(point.zMillionths(), GEOMETRY_SCALE), FIXED_SCALE));
    }

    private static Point bezier(Point b0, Point b1, Point b2, Point b3, int step) {
        Point a = lerp(b0, b1, step);
        Point b = lerp(b1, b2, step);
        Point c = lerp(b2, b3, step);
        Point d = lerp(a, b, step);
        Point e = lerp(b, c, step);
        return lerp(d, e, step);
    }

    private static Point lerp(Point a, Point b, int step) {
        return new Point(
                Math.addExact(a.x(), roundDivide(
                        Math.multiplyExact(Math.subtractExact(b.x(), a.x()), step), CURVE_SUBDIVISIONS)),
                Math.addExact(a.z(), roundDivide(
                        Math.multiplyExact(Math.subtractExact(b.z(), a.z()), step), CURVE_SUBDIVISIONS)));
    }

    private static Segment segment(Point a, Point b) {
        long dx = Math.subtractExact(b.x(), a.x());
        long dz = Math.subtractExact(b.z(), a.z());
        long lengthSquared = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        if (lengthSquared == 0L) {
            throw new CoastalRasterException(
                    "v2.coastal-raster-degenerate", "coast path contains a zero-length fixed segment");
        }
        return new Segment(a.x(), a.z(), b.x(), b.z(), dx, dz, lengthSquared);
    }

    private static Candidate nearest(Segment segment, long sampleX, long sampleZ) {
        long relativeX = Math.subtractExact(sampleX, segment.ax());
        long relativeZ = Math.subtractExact(sampleZ, segment.az());
        long dot = Math.addExact(
                Math.multiplyExact(relativeX, segment.dx()), Math.multiplyExact(relativeZ, segment.dz()));
        long t = Math.max(0L, Math.min(GEOMETRY_SCALE,
                roundDivide(Math.multiplyExact(dot, GEOMETRY_SCALE), segment.lengthSquared())));
        long closestX = Math.addExact(segment.ax(),
                roundDivide(Math.multiplyExact(segment.dx(), t), GEOMETRY_SCALE));
        long closestZ = Math.addExact(segment.az(),
                roundDivide(Math.multiplyExact(segment.dz(), t), GEOMETRY_SCALE));
        long distanceX = Math.subtractExact(sampleX, closestX);
        long distanceZ = Math.subtractExact(sampleZ, closestZ);
        long squared = Math.addExact(
                Math.multiplyExact(distanceX, distanceX), Math.multiplyExact(distanceZ, distanceZ));
        return new Candidate(segment, squared);
    }

    /** Rounds halves away from zero; denominator must be positive. */
    static long roundDivide(long numerator, long denominator) {
        if (denominator <= 0L) throw new ArithmeticException("non-positive fixed-point denominator");
        long quotient = numerator / denominator;
        long remainder = numerator % denominator;
        long absoluteRemainder = Math.abs(remainder);
        if (Math.multiplyExact(absoluteRemainder, 2L) >= denominator) {
            quotient = Math.addExact(quotient, numerator < 0L ? -1L : 1L);
        }
        return quotient;
    }

    static long integerSquareRoot(long value) {
        if (value < 0L) throw new ArithmeticException("negative square root");
        long result = 0L;
        long bit = 1L << 62;
        while (bit > value) bit >>>= 2;
        long remaining = value;
        while (bit != 0L) {
            if (remaining >= result + bit) {
                remaining -= result + bit;
                result = (result >>> 1) + bit;
            } else {
                result >>>= 1;
            }
            bit >>>= 2;
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

    public enum RasterField {
        ACTUAL_LAND_WATER,
        COAST_SIDE,
        SIGNED_DISTANCE,
        NORMAL_X,
        NORMAL_Z,
        NEARSHORE_PROFILE
    }

    public record RasterSample(
            int actualLandWater,
            int coastSide,
            int signedDistanceMillionths,
            int normalXMillionths,
            int normalZMillionths,
            int nearshoreDepthMillionths,
            boolean hardConstrained
    ) {
        public int rawValue(RasterField field) {
            return switch (Objects.requireNonNull(field, "field")) {
                case ACTUAL_LAND_WATER -> actualLandWater;
                case COAST_SIDE -> coastSide;
                case SIGNED_DISTANCE -> signedDistanceMillionths;
                case NORMAL_X -> normalXMillionths;
                case NORMAL_Z -> normalZMillionths;
                case NEARSHORE_PROFILE -> nearshoreDepthMillionths;
            };
        }
    }

    @FunctionalInterface
    public interface CellSource {
        RasterSample sampleAt(int globalX, int globalZ);
    }

    private record Point(long x, long z) { }
    private record Segment(long ax, long az, long bx, long bz, long dx, long dz, long lengthSquared) { }
    private record Candidate(Segment segment, long distanceSquared) { }
}
