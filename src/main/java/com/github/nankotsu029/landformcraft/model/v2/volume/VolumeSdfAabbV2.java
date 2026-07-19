package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.List;
import java.util.Objects;

/**
 * Conservative axis-aligned bounds for one SDF primitive, expressed in release-local
 * block millionths. Extents are inclusive of the zero isosurface and rounding slack.
 */
public record VolumeSdfAabbV2(
        long minXMillionths,
        long minYMillionths,
        long minZMillionths,
        long maxXMillionths,
        long maxYMillionths,
        long maxZMillionths
) {
    public VolumeSdfAabbV2 {
        if (minXMillionths > maxXMillionths
                || minYMillionths > maxYMillionths
                || minZMillionths > maxZMillionths) {
            throw new IllegalArgumentException("AABB min must be <= max");
        }
    }

    public long extentXBlocks() {
        return ceilDiv(Math.subtractExact(maxXMillionths, minXMillionths),
                VolumeSdfPrimitivePlanV2.Quantization.FIXED_SCALE);
    }

    public long extentYBlocks() {
        return ceilDiv(Math.subtractExact(maxYMillionths, minYMillionths),
                VolumeSdfPrimitivePlanV2.Quantization.FIXED_SCALE);
    }

    public long extentZBlocks() {
        return ceilDiv(Math.subtractExact(maxZMillionths, minZMillionths),
                VolumeSdfPrimitivePlanV2.Quantization.FIXED_SCALE);
    }

    public VolumeSdfAabbV2 expand(long radiusMillionths) {
        if (radiusMillionths < 0L) {
            throw new IllegalArgumentException("expand radius must be non-negative");
        }
        return expandAxes(radiusMillionths, radiusMillionths, radiusMillionths);
    }

    public VolumeSdfAabbV2 expandAxes(long xMillionths, long yMillionths, long zMillionths) {
        if (xMillionths < 0L || yMillionths < 0L || zMillionths < 0L) {
            throw new IllegalArgumentException("expand radii must be non-negative");
        }
        return new VolumeSdfAabbV2(
                Math.subtractExact(minXMillionths, xMillionths),
                Math.subtractExact(minYMillionths, yMillionths),
                Math.subtractExact(minZMillionths, zMillionths),
                Math.addExact(maxXMillionths, xMillionths),
                Math.addExact(maxYMillionths, yMillionths),
                Math.addExact(maxZMillionths, zMillionths));
    }

    /** Inclusive AABB intersection. Empty (non-overlapping) returns empty Optional. */
    public java.util.Optional<VolumeSdfAabbV2> intersection(VolumeSdfAabbV2 other) {
        Objects.requireNonNull(other, "other");
        long minX = Math.max(minXMillionths, other.minXMillionths);
        long minY = Math.max(minYMillionths, other.minYMillionths);
        long minZ = Math.max(minZMillionths, other.minZMillionths);
        long maxX = Math.min(maxXMillionths, other.maxXMillionths);
        long maxY = Math.min(maxYMillionths, other.maxYMillionths);
        long maxZ = Math.min(maxZMillionths, other.maxZMillionths);
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new VolumeSdfAabbV2(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public boolean intersects(VolumeSdfAabbV2 other) {
        return intersection(other).isPresent();
    }

    public static VolumeSdfAabbV2 ofPoint(VolumeSdfVec3V2 point) {
        Objects.requireNonNull(point, "point");
        return new VolumeSdfAabbV2(
                point.xMillionths(), point.yMillionths(), point.zMillionths(),
                point.xMillionths(), point.yMillionths(), point.zMillionths());
    }

    public static VolumeSdfAabbV2 spanning(VolumeSdfVec3V2 a, VolumeSdfVec3V2 b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return new VolumeSdfAabbV2(
                Math.min(a.xMillionths(), b.xMillionths()),
                Math.min(a.yMillionths(), b.yMillionths()),
                Math.min(a.zMillionths(), b.zMillionths()),
                Math.max(a.xMillionths(), b.xMillionths()),
                Math.max(a.yMillionths(), b.yMillionths()),
                Math.max(a.zMillionths(), b.zMillionths()));
    }

    public static VolumeSdfAabbV2 spanning(List<VolumeSdfVec3V2> points) {
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("AABB requires at least one point");
        }
        VolumeSdfAabbV2 bounds = ofPoint(points.getFirst());
        for (int index = 1; index < points.size(); index++) {
            VolumeSdfVec3V2 point = points.get(index);
            bounds = new VolumeSdfAabbV2(
                    Math.min(bounds.minXMillionths(), point.xMillionths()),
                    Math.min(bounds.minYMillionths(), point.yMillionths()),
                    Math.min(bounds.minZMillionths(), point.zMillionths()),
                    Math.max(bounds.maxXMillionths(), point.xMillionths()),
                    Math.max(bounds.maxYMillionths(), point.yMillionths()),
                    Math.max(bounds.maxZMillionths(), point.zMillionths()));
        }
        return bounds;
    }

    private static long ceilDiv(long numerator, long denominator) {
        if (denominator <= 0L) {
            throw new IllegalArgumentException("denominator must be positive");
        }
        if (numerator <= 0L) {
            return 0L;
        }
        return Math.addExact(numerator, denominator - 1L) / denominator;
    }
}
