package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformPlainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.plain.PlainFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one PLAIN polygon feature. */
public final class PlainPlanCompilerV2 {
    public PlainPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.PLAIN) {
            throw failure("v2.plain-kind", "feature kind is not PLAIN");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.plain-geometry", "plain requires POLYGON geometry");
        }
        TerrainIntentV2.PlainParameters parameters =
                (TerrainIntentV2.PlainParameters) feature.parameters();
        try {
            List<PlainPlanV2.Ring> rings = toRings(polygon, bounds);
            long interiorCells = estimateInteriorCells(rings, bounds);
            if (interiorCells < 1L) {
                throw failure("v2.plain-degenerate", "plain interior is empty or degenerate");
            }
            int microRelief = midpoint(parameters.microReliefBlocks());
            if (microRelief < 1) {
                throw failure("v2.plain-microrelief", "plain microrelief must be at least 1");
            }
            int baseElevation = midpoint(parameters.baseElevationAboveDatumBlocks());
            int groundwater = midpoint(parameters.groundwaterHandoffDepthBlocks());
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 4L);
            if (work > PlainPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.plain-budget", "plain profile/raster budget exceeded");
            }
            int support = Math.max(1, microRelief);
            if (support > LandformPlainModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.plain-budget", "plain support radius exceeds trusted halo");
            }
            return new PlainPlanV2(
                    PlainPlanV2.VERSION,
                    feature.id(),
                    rings,
                    baseElevation,
                    parameters.microReliefBlocks().minimum(),
                    microRelief,
                    parameters.microReliefBlocks().maximum(),
                    groundwater,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    PlainPlanV2.PLAIN_MASK_FIELD_ID,
                    PlainPlanV2.BASE_ELEVATION_FIELD_ID,
                    PlainPlanV2.MICRO_RELIEF_FIELD_ID,
                    PlainPlanV2.GROUNDWATER_HANDOFF_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException("v2.plain-budget", "plain arithmetic overflow", exception);
        }
    }

    private static List<PlainPlanV2.Ring> toRings(
            TerrainIntentV2.PolygonGeometry polygon,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<PlainPlanV2.Ring> rings = new ArrayList<>(polygon.rings().size());
        for (List<TerrainIntentV2.Point2> ring : polygon.rings()) {
            List<PlainPlanV2.Vertex> vertices = new ArrayList<>(ring.size());
            for (TerrainIntentV2.Point2 point : ring) {
                long x = scaleCoordinate(point.xMillionths(), bounds.width());
                long z = scaleCoordinate(point.zMillionths(), bounds.length());
                vertices.add(new PlainPlanV2.Vertex(x, z));
            }
            rings.add(new PlainPlanV2.Ring(List.copyOf(vertices)));
        }
        return List.copyOf(rings);
    }

    private static long estimateInteriorCells(
            List<PlainPlanV2.Ring> rings,
            WorldBlueprintV2.Bounds bounds
    ) {
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (PlainPlanV2.Ring ring : rings) {
            for (PlainPlanV2.Vertex vertex : ring.vertices()) {
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
                if (PlainFixedMathV2.contains(rings, cx, cz)) {
                    cells = Math.addExact(cells, 1L);
                }
            }
        }
        return cells;
    }

    private static long scaleCoordinate(long millionths, int blocks) {
        long scaled = Math.multiplyExact(millionths, (long) blocks - 1L);
        if (millionths >= TerrainIntentV2.FIXED_SCALE) {
            return Math.addExact(scaled, TerrainIntentV2.FIXED_SCALE - 1L);
        }
        return scaled;
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
