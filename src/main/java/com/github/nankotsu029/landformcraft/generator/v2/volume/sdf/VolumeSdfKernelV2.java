package com.github.nankotsu029.landformcraft.generator.v2.volume.sdf;

import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * Allocation-free fixed-point SDF sampler for V2-5-01. Distance is negative inside the solid.
 * Float/double are never used as the source of truth.
 */
public final class VolumeSdfKernelV2 {
    public static final String KERNEL_VERSION = VolumeSdfPrimitivePlanV2.Kernel.VERSION;

    private final VolumeSdfPrimitivePlanV2 plan;

    public VolumeSdfKernelV2(VolumeSdfPrimitivePlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (!KERNEL_VERSION.equals(plan.kernel().kernelVersion())
                || !VolumeSdfPrimitivePlanV2.Quantization.VERSION.equals(
                plan.quantization().quantizationVersion())) {
            throw new VolumeSdfExceptionV2(
                    VolumeSdfFailureCodeV2.UNKNOWN_KERNEL, "unsupported volume-sdf kernel/quantization");
        }
    }

    public VolumeSdfPrimitivePlanV2 plan() {
        return plan;
    }

    public VolumeSdfAabbV2 conservativeBounds(String primitiveId) {
        return requirePrimitive(primitiveId).conservativeBounds();
    }

    /**
     * Samples signed distance at release-local millionths coordinates. Does not allocate
     * per sample beyond the returned long.
     */
    public long sampleDistanceMillionths(String primitiveId, long xMillionths, long yMillionths, long zMillionths) {
        VolumeSdfPrimitiveV2 primitive = requirePrimitive(primitiveId);
        try {
            return switch (primitive) {
                case VolumeSdfPrimitiveV2.Sphere sphere -> sampleSphere(sphere, xMillionths, yMillionths, zMillionths);
                case VolumeSdfPrimitiveV2.Ellipsoid ellipsoid ->
                        sampleEllipsoid(ellipsoid, xMillionths, yMillionths, zMillionths);
                case VolumeSdfPrimitiveV2.Capsule capsule ->
                        sampleCapsule(capsule, xMillionths, yMillionths, zMillionths);
                case VolumeSdfPrimitiveV2.Plane plane -> samplePlane(plane, xMillionths, yMillionths, zMillionths);
                case VolumeSdfPrimitiveV2.RoundedBox box ->
                        sampleRoundedBox(box, xMillionths, yMillionths, zMillionths);
                case VolumeSdfPrimitiveV2.SweptSpline spline ->
                        sampleSweptSpline(spline, xMillionths, yMillionths, zMillionths);
            };
        } catch (ArithmeticException exception) {
            throw new VolumeSdfExceptionV2(
                    VolumeSdfFailureCodeV2.ARITHMETIC_OVERFLOW,
                    "volume-sdf arithmetic overflow",
                    exception);
        }
    }

    public int sampleSign(String primitiveId, long xMillionths, long yMillionths, long zMillionths) {
        long distance = sampleDistanceMillionths(primitiveId, xMillionths, yMillionths, zMillionths);
        return Long.compare(distance, 0L);
    }

    /** Canonical golden checksum over a lattice of samples for one primitive. */
    public String goldenChecksum(String primitiveId, long stepMillionths, int halfExtentSteps) {
        if (stepMillionths <= 0L || halfExtentSteps < 1 || halfExtentSteps > 32) {
            throw new VolumeSdfExceptionV2(
                    VolumeSdfFailureCodeV2.BUDGET_EXCEEDED, "golden lattice budget rejected");
        }
        VolumeSdfPrimitiveV2 primitive = requirePrimitive(primitiveId);
        VolumeSdfAabbV2 bounds = primitive.conservativeBounds();
        long centerX = Math.addExact(bounds.minXMillionths(), bounds.maxXMillionths()) / 2L;
        long centerY = Math.addExact(bounds.minYMillionths(), bounds.maxYMillionths()) / 2L;
        long centerZ = Math.addExact(bounds.minZMillionths(), bounds.maxZMillionths()) / 2L;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(primitiveId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(KERNEL_VERSION.getBytes(StandardCharsets.UTF_8));
            for (int iz = -halfExtentSteps; iz <= halfExtentSteps; iz++) {
                for (int iy = -halfExtentSteps; iy <= halfExtentSteps; iy++) {
                    for (int ix = -halfExtentSteps; ix <= halfExtentSteps; ix++) {
                        long x = Math.addExact(centerX, Math.multiplyExact(ix, stepMillionths));
                        long y = Math.addExact(centerY, Math.multiplyExact(iy, stepMillionths));
                        long z = Math.addExact(centerZ, Math.multiplyExact(iz, stepMillionths));
                        long distance = sampleDistanceMillionths(primitiveId, x, y, z);
                        digest.update((byte) Long.compare(distance, 0L));
                        putLong(digest, distance);
                    }
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private VolumeSdfPrimitiveV2 requirePrimitive(String primitiveId) {
        Objects.requireNonNull(primitiveId, "primitiveId");
        for (VolumeSdfPrimitiveV2 primitive : plan.primitives()) {
            if (primitive.primitiveId().equals(primitiveId)) {
                return primitive;
            }
        }
        throw new VolumeSdfExceptionV2(
                VolumeSdfFailureCodeV2.UNSUPPORTED_PRIMITIVE, "unknown primitive id: " + primitiveId);
    }

    private long sampleSphere(
            VolumeSdfPrimitiveV2.Sphere sphere,
            long xMillionths,
            long yMillionths,
            long zMillionths
    ) {
        requirePositive(sphere.radiusMillionths());
        long dx = VolumeSdfFixedMathV2.toGeometry(Math.subtractExact(xMillionths, sphere.center().xMillionths()));
        long dy = VolumeSdfFixedMathV2.toGeometry(Math.subtractExact(yMillionths, sphere.center().yMillionths()));
        long dz = VolumeSdfFixedMathV2.toGeometry(Math.subtractExact(zMillionths, sphere.center().zMillionths()));
        long radiusQ = VolumeSdfFixedMathV2.toGeometry(sphere.radiusMillionths());
        return VolumeSdfFixedMathV2.toMillionths(
                Math.subtractExact(VolumeSdfFixedMathV2.hypot3(dx, dy, dz), radiusQ));
    }

    private long sampleEllipsoid(
            VolumeSdfPrimitiveV2.Ellipsoid ellipsoid,
            long xMillionths,
            long yMillionths,
            long zMillionths
    ) {
        VolumeSdfVec3V2 radii = ellipsoid.radiiMillionths();
        requirePositive(radii.xMillionths());
        requirePositive(radii.yMillionths());
        requirePositive(radii.zMillionths());
        long px = VolumeSdfFixedMathV2.toGeometry(Math.subtractExact(xMillionths, ellipsoid.center().xMillionths()));
        long py = VolumeSdfFixedMathV2.toGeometry(Math.subtractExact(yMillionths, ellipsoid.center().yMillionths()));
        long pz = VolumeSdfFixedMathV2.toGeometry(Math.subtractExact(zMillionths, ellipsoid.center().zMillionths()));
        long rx = VolumeSdfFixedMathV2.toGeometry(radii.xMillionths());
        long ry = VolumeSdfFixedMathV2.toGeometry(radii.yMillionths());
        long rz = VolumeSdfFixedMathV2.toGeometry(radii.zMillionths());
        // Exact ellipsoid SDF (Inigo Quilez) in fixed-point Q space.
        long sx = VolumeSdfFixedMathV2.roundDivide(Math.multiplyExact(px, VolumeSdfFixedMathV2.GEOMETRY_SCALE), rx);
        long sy = VolumeSdfFixedMathV2.roundDivide(Math.multiplyExact(py, VolumeSdfFixedMathV2.GEOMETRY_SCALE), ry);
        long sz = VolumeSdfFixedMathV2.roundDivide(Math.multiplyExact(pz, VolumeSdfFixedMathV2.GEOMETRY_SCALE), rz);
        long k0 = VolumeSdfFixedMathV2.hypot3(sx, sy, sz);
        if (k0 == 0L) {
            return -Math.min(radii.xMillionths(), Math.min(radii.yMillionths(), radii.zMillionths()));
        }
        // k1 = length((p/r)/r) keeps the same units as the float SDF without squaring radii first.
        long tx = VolumeSdfFixedMathV2.roundDivide(Math.multiplyExact(sx, VolumeSdfFixedMathV2.GEOMETRY_SCALE), rx);
        long ty = VolumeSdfFixedMathV2.roundDivide(Math.multiplyExact(sy, VolumeSdfFixedMathV2.GEOMETRY_SCALE), ry);
        long tz = VolumeSdfFixedMathV2.roundDivide(Math.multiplyExact(sz, VolumeSdfFixedMathV2.GEOMETRY_SCALE), rz);
        long k1 = VolumeSdfFixedMathV2.hypot3(tx, ty, tz);
        long minRadiusQ = Math.min(rx, Math.min(ry, rz));
        if (k1 == 0L) {
            long approxQ = VolumeSdfFixedMathV2.roundDivide(
                    Math.multiplyExact(Math.subtractExact(k0, VolumeSdfFixedMathV2.GEOMETRY_SCALE), minRadiusQ),
                    VolumeSdfFixedMathV2.GEOMETRY_SCALE);
            return VolumeSdfFixedMathV2.toMillionths(approxQ);
        }
        long numerator = Math.multiplyExact(k0, Math.subtractExact(k0, VolumeSdfFixedMathV2.GEOMETRY_SCALE));
        long distanceQ = VolumeSdfFixedMathV2.roundDivide(numerator, k1);
        return VolumeSdfFixedMathV2.toMillionths(distanceQ);
    }

    private long sampleCapsule(
            VolumeSdfPrimitiveV2.Capsule capsule,
            long xMillionths,
            long yMillionths,
            long zMillionths
    ) {
        requirePositive(capsule.radiusMillionths());
        long distanceQ = segmentDistanceQ(
                capsule.pointA(), capsule.pointB(), xMillionths, yMillionths, zMillionths);
        long radiusQ = VolumeSdfFixedMathV2.toGeometry(capsule.radiusMillionths());
        return VolumeSdfFixedMathV2.toMillionths(Math.subtractExact(distanceQ, radiusQ));
    }

    private long samplePlane(
            VolumeSdfPrimitiveV2.Plane plane,
            long xMillionths,
            long yMillionths,
            long zMillionths
    ) {
        long dx = Math.subtractExact(xMillionths, plane.point().xMillionths());
        long dy = Math.subtractExact(yMillionths, plane.point().yMillionths());
        long dz = Math.subtractExact(zMillionths, plane.point().zMillionths());
        long dot = Math.addExact(
                Math.addExact(
                        Math.multiplyExact(dx, plane.normalMillionths().xMillionths()),
                        Math.multiplyExact(dy, plane.normalMillionths().yMillionths())),
                Math.multiplyExact(dz, plane.normalMillionths().zMillionths()));
        return VolumeSdfFixedMathV2.roundDivide(dot, VolumeSdfFixedMathV2.FIXED_SCALE);
    }

    private long sampleRoundedBox(
            VolumeSdfPrimitiveV2.RoundedBox box,
            long xMillionths,
            long yMillionths,
            long zMillionths
    ) {
        VolumeSdfVec3V2 half = box.halfExtentsMillionths();
        requirePositive(half.xMillionths());
        requirePositive(half.yMillionths());
        requirePositive(half.zMillionths());
        if (box.cornerRadiusMillionths() < 0L) {
            throw new VolumeSdfExceptionV2(
                    VolumeSdfFailureCodeV2.ZERO_RADIUS, "negative corner radius");
        }
        long px = Math.abs(VolumeSdfFixedMathV2.toGeometry(
                Math.subtractExact(xMillionths, box.center().xMillionths())));
        long py = Math.abs(VolumeSdfFixedMathV2.toGeometry(
                Math.subtractExact(yMillionths, box.center().yMillionths())));
        long pz = Math.abs(VolumeSdfFixedMathV2.toGeometry(
                Math.subtractExact(zMillionths, box.center().zMillionths())));
        long hx = VolumeSdfFixedMathV2.toGeometry(half.xMillionths());
        long hy = VolumeSdfFixedMathV2.toGeometry(half.yMillionths());
        long hz = VolumeSdfFixedMathV2.toGeometry(half.zMillionths());
        long radiusQ = VolumeSdfFixedMathV2.toGeometry(box.cornerRadiusMillionths());
        long bx = Math.subtractExact(hx, radiusQ);
        long by = Math.subtractExact(hy, radiusQ);
        long bz = Math.subtractExact(hz, radiusQ);
        long qx = Math.subtractExact(px, bx);
        long qy = Math.subtractExact(py, by);
        long qz = Math.subtractExact(pz, bz);
        long outsideX = Math.max(qx, 0L);
        long outsideY = Math.max(qy, 0L);
        long outsideZ = Math.max(qz, 0L);
        long outside = VolumeSdfFixedMathV2.hypot3(outsideX, outsideY, outsideZ);
        long inside = Math.min(VolumeSdfFixedMathV2.max3(qx, qy, qz), 0L);
        long distanceQ = Math.subtractExact(Math.addExact(outside, inside), radiusQ);
        return VolumeSdfFixedMathV2.toMillionths(distanceQ);
    }

    private long sampleSweptSpline(
            VolumeSdfPrimitiveV2.SweptSpline spline,
            long xMillionths,
            long yMillionths,
            long zMillionths
    ) {
        requirePositive(spline.radiusMillionths());
        List<VolumeSdfVec3V2> points = spline.controlPoints();
        int segments = points.size() - 1;
        if (segments > plan.kernel().maximumSampleOperationsPerPrimitive()) {
            throw new VolumeSdfExceptionV2(
                    VolumeSdfFailureCodeV2.BUDGET_EXCEEDED, "swept spline sample ops exceed budget");
        }
        long bestQ = Long.MAX_VALUE;
        for (int index = 0; index < segments; index++) {
            long distanceQ = segmentDistanceQ(
                    points.get(index), points.get(index + 1), xMillionths, yMillionths, zMillionths);
            if (distanceQ < bestQ) {
                bestQ = distanceQ;
            }
        }
        long radiusQ = VolumeSdfFixedMathV2.toGeometry(spline.radiusMillionths());
        return VolumeSdfFixedMathV2.toMillionths(Math.subtractExact(bestQ, radiusQ));
    }

    private static long segmentDistanceQ(
            VolumeSdfVec3V2 a,
            VolumeSdfVec3V2 b,
            long xMillionths,
            long yMillionths,
            long zMillionths
    ) {
        long ax = VolumeSdfFixedMathV2.toGeometry(a.xMillionths());
        long ay = VolumeSdfFixedMathV2.toGeometry(a.yMillionths());
        long az = VolumeSdfFixedMathV2.toGeometry(a.zMillionths());
        long bx = VolumeSdfFixedMathV2.toGeometry(b.xMillionths());
        long by = VolumeSdfFixedMathV2.toGeometry(b.yMillionths());
        long bz = VolumeSdfFixedMathV2.toGeometry(b.zMillionths());
        long px = VolumeSdfFixedMathV2.toGeometry(xMillionths);
        long py = VolumeSdfFixedMathV2.toGeometry(yMillionths);
        long pz = VolumeSdfFixedMathV2.toGeometry(zMillionths);
        long abx = Math.subtractExact(bx, ax);
        long aby = Math.subtractExact(by, ay);
        long abz = Math.subtractExact(bz, az);
        long apx = Math.subtractExact(px, ax);
        long apy = Math.subtractExact(py, ay);
        long apz = Math.subtractExact(pz, az);
        long abLen2 = Math.addExact(
                Math.addExact(Math.multiplyExact(abx, abx), Math.multiplyExact(aby, aby)),
                Math.multiplyExact(abz, abz));
        if (abLen2 == 0L) {
            throw new VolumeSdfExceptionV2(
                    VolumeSdfFailureCodeV2.DEGENERATE_GEOMETRY, "zero-length segment");
        }
        long projection = VolumeSdfFixedMathV2.clamp(
                VolumeSdfFixedMathV2.roundDivide(
                        Math.multiplyExact(
                                Math.addExact(
                                        Math.addExact(Math.multiplyExact(apx, abx), Math.multiplyExact(apy, aby)),
                                        Math.multiplyExact(apz, abz)),
                                VolumeSdfFixedMathV2.GEOMETRY_SCALE),
                        abLen2),
                0L,
                VolumeSdfFixedMathV2.GEOMETRY_SCALE);
        long qx = Math.addExact(ax, VolumeSdfFixedMathV2.roundDivide(
                Math.multiplyExact(abx, projection), VolumeSdfFixedMathV2.GEOMETRY_SCALE));
        long qy = Math.addExact(ay, VolumeSdfFixedMathV2.roundDivide(
                Math.multiplyExact(aby, projection), VolumeSdfFixedMathV2.GEOMETRY_SCALE));
        long qz = Math.addExact(az, VolumeSdfFixedMathV2.roundDivide(
                Math.multiplyExact(abz, projection), VolumeSdfFixedMathV2.GEOMETRY_SCALE));
        return VolumeSdfFixedMathV2.hypot3(
                Math.subtractExact(px, qx), Math.subtractExact(py, qy), Math.subtractExact(pz, qz));
    }

    private static void requirePositive(long radiusMillionths) {
        if (radiusMillionths <= 0L) {
            throw new VolumeSdfExceptionV2(VolumeSdfFailureCodeV2.ZERO_RADIUS, "radius must be positive");
        }
    }

    private static void putLong(MessageDigest digest, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
