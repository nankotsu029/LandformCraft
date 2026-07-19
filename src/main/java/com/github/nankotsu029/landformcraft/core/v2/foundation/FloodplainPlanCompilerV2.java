package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformFloodplainModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.floodplain.FloodplainFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FloodplainPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one FLOODPLAIN polygon feature. */
public final class FloodplainPlanCompilerV2 {
    public FloodplainPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.FLOODPLAIN) {
            throw failure("v2.floodplain-kind", "feature kind is not FLOODPLAIN");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.floodplain-geometry", "floodplain requires POLYGON geometry");
        }
        TerrainIntentV2.FloodplainParameters parameters =
                (TerrainIntentV2.FloodplainParameters) feature.parameters();
        try {
            List<FloodplainPlanV2.Ring> rings = toRings(polygon, bounds);
            long interiorCells = estimateInteriorCells(rings, bounds);
            if (interiorCells < 1L) {
                throw failure("v2.floodplain-degenerate", "floodplain interior is empty or degenerate");
            }
            int adjacency = midpoint(parameters.riverAdjacencyBandBlocks());
            int groundwater = midpoint(parameters.groundwaterHandoffDepthBlocks());
            int microRelief = midpoint(parameters.microReliefBlocks());
            if (microRelief < 1) {
                throw failure("v2.floodplain-microrelief", "floodplain microrelief must be at least 1");
            }
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > FloodplainPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.floodplain-budget", "floodplain profile/raster budget exceeded");
            }
            int support = Math.max(adjacency, microRelief);
            if (support > LandformFloodplainModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.floodplain-budget", "floodplain support radius exceeds trusted halo");
            }
            return new FloodplainPlanV2(
                    FloodplainPlanV2.VERSION,
                    feature.id(),
                    rings,
                    adjacency,
                    groundwater,
                    microRelief,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    FloodplainPlanV2.FLOODPLAIN_MASK_FIELD_ID,
                    FloodplainPlanV2.ELEVATION_FIELD_ID,
                    FloodplainPlanV2.MICRO_RELIEF_FIELD_ID,
                    FloodplainPlanV2.GROUNDWATER_HANDOFF_FIELD_ID,
                    FloodplainPlanV2.SOLID_OWNERSHIP_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.floodplain-budget", "floodplain arithmetic overflow", exception);
        }
    }

    private static List<FloodplainPlanV2.Ring> toRings(
            TerrainIntentV2.PolygonGeometry polygon,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<FloodplainPlanV2.Ring> rings = new ArrayList<>(polygon.rings().size());
        for (List<TerrainIntentV2.Point2> ring : polygon.rings()) {
            List<FloodplainPlanV2.Vertex> vertices = new ArrayList<>(ring.size());
            for (TerrainIntentV2.Point2 point : ring) {
                long x = scaleCoordinate(point.xMillionths(), bounds.width());
                long z = scaleCoordinate(point.zMillionths(), bounds.length());
                vertices.add(new FloodplainPlanV2.Vertex(x, z));
            }
            rings.add(new FloodplainPlanV2.Ring(List.copyOf(vertices)));
        }
        return List.copyOf(rings);
    }

    private static long estimateInteriorCells(
            List<FloodplainPlanV2.Ring> rings,
            WorldBlueprintV2.Bounds bounds
    ) {
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (FloodplainPlanV2.Ring ring : rings) {
            for (FloodplainPlanV2.Vertex vertex : ring.vertices()) {
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
                if (FloodplainFixedMathV2.contains(rings, cx, cz)) {
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
