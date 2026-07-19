package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.List;
import java.util.Objects;

/**
 * Analytic SDF primitive kinds for V2-5-01. Distance is negative inside the solid.
 */
public sealed interface VolumeSdfPrimitiveV2
        permits VolumeSdfPrimitiveV2.Sphere,
        VolumeSdfPrimitiveV2.Ellipsoid,
        VolumeSdfPrimitiveV2.Capsule,
        VolumeSdfPrimitiveV2.Plane,
        VolumeSdfPrimitiveV2.RoundedBox,
        VolumeSdfPrimitiveV2.SweptSpline {

    enum Kind { SPHERE, ELLIPSOID, CAPSULE, PLANE, ROUNDED_BOX, SWEPT_SPLINE }

    String primitiveId();

    Kind kind();

    /** Conservative AABB that contains the zero isosurface after fixed-point rounding. */
    VolumeSdfAabbV2 conservativeBounds();

    record Sphere(String primitiveId, VolumeSdfVec3V2 center, long radiusMillionths)
            implements VolumeSdfPrimitiveV2 {
        public Sphere {
            primitiveId = VolumeSdfPrimitivePlanV2.qualified(primitiveId, "primitiveId");
            Objects.requireNonNull(center, "center");
            requirePositiveRadius(radiusMillionths);
        }

        @Override
        public Kind kind() {
            return Kind.SPHERE;
        }

        @Override
        public VolumeSdfAabbV2 conservativeBounds() {
            return VolumeSdfAabbV2.ofPoint(center).expand(Math.addExact(radiusMillionths, ROUNDING_SLACK));
        }
    }

    record Ellipsoid(String primitiveId, VolumeSdfVec3V2 center, VolumeSdfVec3V2 radiiMillionths)
            implements VolumeSdfPrimitiveV2 {
        public Ellipsoid {
            primitiveId = VolumeSdfPrimitivePlanV2.qualified(primitiveId, "primitiveId");
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(radiiMillionths, "radiiMillionths");
            requirePositiveRadius(radiiMillionths.xMillionths());
            requirePositiveRadius(radiiMillionths.yMillionths());
            requirePositiveRadius(radiiMillionths.zMillionths());
        }

        @Override
        public Kind kind() {
            return Kind.ELLIPSOID;
        }

        @Override
        public VolumeSdfAabbV2 conservativeBounds() {
            return new VolumeSdfAabbV2(
                    Math.subtractExact(center.xMillionths(),
                            Math.addExact(radiiMillionths.xMillionths(), ROUNDING_SLACK)),
                    Math.subtractExact(center.yMillionths(),
                            Math.addExact(radiiMillionths.yMillionths(), ROUNDING_SLACK)),
                    Math.subtractExact(center.zMillionths(),
                            Math.addExact(radiiMillionths.zMillionths(), ROUNDING_SLACK)),
                    Math.addExact(center.xMillionths(),
                            Math.addExact(radiiMillionths.xMillionths(), ROUNDING_SLACK)),
                    Math.addExact(center.yMillionths(),
                            Math.addExact(radiiMillionths.yMillionths(), ROUNDING_SLACK)),
                    Math.addExact(center.zMillionths(),
                            Math.addExact(radiiMillionths.zMillionths(), ROUNDING_SLACK)));
        }
    }

    record Capsule(
            String primitiveId,
            VolumeSdfVec3V2 pointA,
            VolumeSdfVec3V2 pointB,
            long radiusMillionths
    ) implements VolumeSdfPrimitiveV2 {
        public Capsule {
            primitiveId = VolumeSdfPrimitivePlanV2.qualified(primitiveId, "primitiveId");
            Objects.requireNonNull(pointA, "pointA");
            Objects.requireNonNull(pointB, "pointB");
            requirePositiveRadius(radiusMillionths);
            if (pointA.equals(pointB)) {
                throw new IllegalArgumentException("capsule endpoints must be distinct");
            }
        }

        @Override
        public Kind kind() {
            return Kind.CAPSULE;
        }

        @Override
        public VolumeSdfAabbV2 conservativeBounds() {
            return VolumeSdfAabbV2.spanning(pointA, pointB)
                    .expand(Math.addExact(radiusMillionths, ROUNDING_SLACK));
        }
    }

    /**
     * Half-space plane. {@code normalMillionths} must be a unit vector in millionths space
     * (length approximately {@link VolumeSdfPrimitivePlanV2.Quantization#FIXED_SCALE}).
     * Infinite planes require an explicit clip AABB for conservative bounds.
     */
    record Plane(
            String primitiveId,
            VolumeSdfVec3V2 point,
            VolumeSdfVec3V2 normalMillionths,
            VolumeSdfAabbV2 clipAabb
    ) implements VolumeSdfPrimitiveV2 {
        public Plane {
            primitiveId = VolumeSdfPrimitivePlanV2.qualified(primitiveId, "primitiveId");
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(normalMillionths, "normalMillionths");
            Objects.requireNonNull(clipAabb, "clipAabb");
            long length = VolumeSdfPrimitiveMath.hypot3(
                    normalMillionths.xMillionths(),
                    normalMillionths.yMillionths(),
                    normalMillionths.zMillionths());
            long scale = VolumeSdfPrimitivePlanV2.Quantization.FIXED_SCALE;
            if (length < scale - UNIT_NORMAL_TOLERANCE || length > scale + UNIT_NORMAL_TOLERANCE) {
                throw new IllegalArgumentException("plane normal must be unit-length in millionths");
            }
        }

        @Override
        public Kind kind() {
            return Kind.PLANE;
        }

        @Override
        public VolumeSdfAabbV2 conservativeBounds() {
            return new VolumeSdfAabbV2(
                    Math.subtractExact(clipAabb.minXMillionths(), ROUNDING_SLACK),
                    Math.subtractExact(clipAabb.minYMillionths(), ROUNDING_SLACK),
                    Math.subtractExact(clipAabb.minZMillionths(), ROUNDING_SLACK),
                    Math.addExact(clipAabb.maxXMillionths(), ROUNDING_SLACK),
                    Math.addExact(clipAabb.maxYMillionths(), ROUNDING_SLACK),
                    Math.addExact(clipAabb.maxZMillionths(), ROUNDING_SLACK));
        }
    }

    record RoundedBox(
            String primitiveId,
            VolumeSdfVec3V2 center,
            VolumeSdfVec3V2 halfExtentsMillionths,
            long cornerRadiusMillionths
    ) implements VolumeSdfPrimitiveV2 {
        public RoundedBox {
            primitiveId = VolumeSdfPrimitivePlanV2.qualified(primitiveId, "primitiveId");
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(halfExtentsMillionths, "halfExtentsMillionths");
            requirePositiveRadius(halfExtentsMillionths.xMillionths());
            requirePositiveRadius(halfExtentsMillionths.yMillionths());
            requirePositiveRadius(halfExtentsMillionths.zMillionths());
            if (cornerRadiusMillionths < 0L) {
                throw new IllegalArgumentException("corner radius must be non-negative");
            }
            if (cornerRadiusMillionths >= halfExtentsMillionths.xMillionths()
                    || cornerRadiusMillionths >= halfExtentsMillionths.yMillionths()
                    || cornerRadiusMillionths >= halfExtentsMillionths.zMillionths()) {
                throw new IllegalArgumentException("corner radius must be smaller than each half-extent");
            }
        }

        @Override
        public Kind kind() {
            return Kind.ROUNDED_BOX;
        }

        @Override
        public VolumeSdfAabbV2 conservativeBounds() {
            long expandX = Math.addExact(halfExtentsMillionths.xMillionths(), ROUNDING_SLACK);
            long expandY = Math.addExact(halfExtentsMillionths.yMillionths(), ROUNDING_SLACK);
            long expandZ = Math.addExact(halfExtentsMillionths.zMillionths(), ROUNDING_SLACK);
            return new VolumeSdfAabbV2(
                    Math.subtractExact(center.xMillionths(), expandX),
                    Math.subtractExact(center.yMillionths(), expandY),
                    Math.subtractExact(center.zMillionths(), expandZ),
                    Math.addExact(center.xMillionths(), expandX),
                    Math.addExact(center.yMillionths(), expandY),
                    Math.addExact(center.zMillionths(), expandZ));
        }
    }

    record SweptSpline(
            String primitiveId,
            List<VolumeSdfVec3V2> controlPoints,
            long radiusMillionths
    ) implements VolumeSdfPrimitiveV2 {
        public SweptSpline {
            primitiveId = VolumeSdfPrimitivePlanV2.qualified(primitiveId, "primitiveId");
            controlPoints = List.copyOf(Objects.requireNonNull(controlPoints, "controlPoints"));
            if (controlPoints.size() < 2
                    || controlPoints.size() > VolumeSdfPrimitivePlanV2.MAXIMUM_SWEPT_CONTROL_POINTS) {
                throw new IllegalArgumentException("swept spline control-point count out of range");
            }
            requirePositiveRadius(radiusMillionths);
            for (int index = 1; index < controlPoints.size(); index++) {
                if (controlPoints.get(index - 1).equals(controlPoints.get(index))) {
                    throw new IllegalArgumentException("swept spline has a zero-length segment");
                }
            }
        }

        @Override
        public Kind kind() {
            return Kind.SWEPT_SPLINE;
        }

        @Override
        public VolumeSdfAabbV2 conservativeBounds() {
            return VolumeSdfAabbV2.spanning(controlPoints)
                    .expand(Math.addExact(radiusMillionths, ROUNDING_SLACK));
        }
    }

    long ROUNDING_SLACK = 1L;
    long UNIT_NORMAL_TOLERANCE = 1_000L;

    private static void requirePositiveRadius(long radiusMillionths) {
        if (radiusMillionths <= 0L) {
            throw new IllegalArgumentException("radius must be positive");
        }
    }
}
