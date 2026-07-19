package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

import com.github.nankotsu029.landformcraft.model.v2.foundation.BathymetryRingsV2;

import java.util.List;

/** Integer-only polygon tests and deterministic hashing for V2-9-08 bathymetry profiles. */
public final class BathymetryFixedMathV2 {
    private BathymetryFixedMathV2() {
    }

    public static boolean contains(List<BathymetryRingsV2.Ring> rings, long x, long z) {
        if (!insideOrBoundary(rings.getFirst().vertices(), x, z)) {
            return false;
        }
        for (int index = 1; index < rings.size(); index++) {
            if (insideOrBoundary(rings.get(index).vertices(), x, z)) {
                return false;
            }
        }
        return true;
    }

    static boolean insideOrBoundary(List<BathymetryRingsV2.Vertex> ring, long x, long z) {
        boolean inside = false;
        for (int index = 0; index < ring.size() - 1; index++) {
            BathymetryRingsV2.Vertex a = ring.get(index);
            BathymetryRingsV2.Vertex b = ring.get(index + 1);
            long cross = cross(a, b, x, z);
            if (cross == 0L && between(x, a.xMillionths(), b.xMillionths())
                    && between(z, a.zMillionths(), b.zMillionths())) {
                return true;
            }
            if ((a.zMillionths() > z) != (b.zMillionths() > z)) {
                long dy = Math.subtractExact(b.zMillionths(), a.zMillionths());
                boolean crossesRight = (dy > 0L && cross > 0L) || (dy < 0L && cross < 0L);
                if (crossesRight) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    static long cross(BathymetryRingsV2.Vertex a, BathymetryRingsV2.Vertex b, long x, long z) {
        return Math.subtractExact(
                Math.multiplyExact(Math.subtractExact(b.xMillionths(), a.xMillionths()),
                        Math.subtractExact(z, a.zMillionths())),
                Math.multiplyExact(Math.subtractExact(b.zMillionths(), a.zMillionths()),
                        Math.subtractExact(x, a.xMillionths())));
    }

    static boolean between(long value, long first, long second) {
        return value >= Math.min(first, second) && value <= Math.max(first, second);
    }

    public static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public static long cellHash(String seedNamespace, int x, int z) {
        long mixed = mix64(mix64(seedNamespace.hashCode() ^ x) ^ z);
        return Long.remainderUnsigned(mixed, 1_000_000L);
    }

    /** Approximate distance in blocks from cell center to outer-ring boundary (block-space; no millionths² overflow). */
    public static int coastDistanceBlocks(List<BathymetryRingsV2.Ring> rings, long px, long pz) {
        List<BathymetryRingsV2.Vertex> outer = rings.getFirst().vertices();
        long best = Long.MAX_VALUE;
        long cx = Math.floorDiv(px, 1_000_000L);
        long cz = Math.floorDiv(pz, 1_000_000L);
        for (int index = 0; index < outer.size() - 1; index++) {
            BathymetryRingsV2.Vertex a = outer.get(index);
            BathymetryRingsV2.Vertex b = outer.get(index + 1);
            long ax = Math.floorDiv(a.xMillionths(), 1_000_000L);
            long az = Math.floorDiv(a.zMillionths(), 1_000_000L);
            long bx = Math.floorDiv(b.xMillionths(), 1_000_000L);
            long bz = Math.floorDiv(b.zMillionths(), 1_000_000L);
            long dist = pointSegmentDistanceBlocks(cx, cz, ax, az, bx, bz);
            best = Math.min(best, dist);
        }
        return Math.toIntExact(best);
    }

    static long pointSegmentDistanceBlocks(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = Math.subtractExact(bx, ax);
        long dz = Math.subtractExact(bz, az);
        long len2 = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        if (len2 == 0L) {
            return hypot(Math.subtractExact(px, ax), Math.subtractExact(pz, az));
        }
        long tNum = Math.addExact(
                Math.multiplyExact(Math.subtractExact(px, ax), dx),
                Math.multiplyExact(Math.subtractExact(pz, az), dz));
        if (tNum <= 0L) {
            return hypot(Math.subtractExact(px, ax), Math.subtractExact(pz, az));
        }
        if (tNum >= len2) {
            return hypot(Math.subtractExact(px, bx), Math.subtractExact(pz, bz));
        }
        long qx = Math.addExact(ax, Math.floorDiv(Math.multiplyExact(tNum, dx), len2));
        long qz = Math.addExact(az, Math.floorDiv(Math.multiplyExact(tNum, dz), len2));
        return hypot(Math.subtractExact(px, qx), Math.subtractExact(pz, qz));
    }

    public static long hypot(long dx, long dz) {
        long ax = Math.abs(dx);
        long az = Math.abs(dz);
        long lo = Math.min(ax, az);
        long hi = Math.max(ax, az);
        if (hi == 0L) {
            return 0L;
        }
        return hi + Math.floorDiv(lo * 4142L, 10_000L);
    }
}
