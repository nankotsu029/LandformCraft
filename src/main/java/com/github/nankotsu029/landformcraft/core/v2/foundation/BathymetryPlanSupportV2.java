package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.BathymetryFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BathymetryRingsV2;

import java.util.ArrayList;
import java.util.List;

/** Shared ring conversion and interior estimation for V2-9-08 bathymetry plan compilers. */
final class BathymetryPlanSupportV2 {
    private BathymetryPlanSupportV2() {
    }

    static List<BathymetryRingsV2.Ring> toRings(
            TerrainIntentV2.PolygonGeometry polygon,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<BathymetryRingsV2.Ring> rings = new ArrayList<>(polygon.rings().size());
        for (List<TerrainIntentV2.Point2> ring : polygon.rings()) {
            List<BathymetryRingsV2.Vertex> vertices = new ArrayList<>(ring.size());
            for (TerrainIntentV2.Point2 point : ring) {
                long x = scaleCoordinate(point.xMillionths(), bounds.width());
                long z = scaleCoordinate(point.zMillionths(), bounds.length());
                vertices.add(new BathymetryRingsV2.Vertex(x, z));
            }
            rings.add(new BathymetryRingsV2.Ring(List.copyOf(vertices)));
        }
        return List.copyOf(rings);
    }

    static long estimateInteriorCells(
            List<BathymetryRingsV2.Ring> rings,
            WorldBlueprintV2.Bounds bounds
    ) {
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (BathymetryRingsV2.Ring ring : rings) {
            for (BathymetryRingsV2.Vertex vertex : ring.vertices()) {
                minX = Math.min(minX, vertex.xMillionths());
                maxX = Math.max(maxX, vertex.xMillionths());
                minZ = Math.min(minZ, vertex.zMillionths());
                maxZ = Math.max(maxZ, vertex.zMillionths());
            }
        }
        int originX = Math.toIntExact(Math.max(0L, Math.floorDiv(minX, TerrainIntentV2.FIXED_SCALE) - 1));
        int originZ = Math.toIntExact(Math.max(0L, Math.floorDiv(minZ, TerrainIntentV2.FIXED_SCALE) - 1));
        int endX = Math.toIntExact(Math.min(bounds.width() - 1L,
                Math.floorDiv(maxX, TerrainIntentV2.FIXED_SCALE) + 1));
        int endZ = Math.toIntExact(Math.min(bounds.length() - 1L,
                Math.floorDiv(maxZ, TerrainIntentV2.FIXED_SCALE) + 1));
        long cells = 0L;
        for (int z = originZ; z <= endZ; z++) {
            for (int x = originX; x <= endX; x++) {
                long cx = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                        TerrainIntentV2.FIXED_SCALE / 2L);
                long cz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                        TerrainIntentV2.FIXED_SCALE / 2L);
                if (BathymetryFixedMathV2.contains(rings, cx, cz)) {
                    cells = Math.addExact(cells, 1L);
                }
            }
        }
        return cells;
    }

    static long scaleCoordinate(long millionths, int blocks) {
        long scaled = Math.multiplyExact(millionths, (long) blocks - 1L);
        if (millionths >= TerrainIntentV2.FIXED_SCALE) {
            return Math.addExact(scaled, TerrainIntentV2.FIXED_SCALE - 1L);
        }
        return scaled;
    }

    static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    static long midpoint(TerrainIntentV2.FixedRange range) {
        return range.minimumMillionths()
                + (range.maximumMillionths() - range.minimumMillionths()) / 2L;
    }
}
