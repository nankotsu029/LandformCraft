package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformMarshModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.marsh.MarshFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MarshPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one MARSH polygon feature. */
public final class MarshPlanCompilerV2 {
    public MarshPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.MARSH) {
            throw failure("v2.marsh-kind", "feature kind is not MARSH");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.marsh-geometry", "marsh requires POLYGON geometry");
        }
        TerrainIntentV2.MarshParameters parameters =
                (TerrainIntentV2.MarshParameters) feature.parameters();
        try {
            List<MarshPlanV2.Ring> rings = toRings(polygon, bounds);
            long interiorCells = estimateInteriorCells(rings, bounds);
            if (interiorCells < 1L) {
                throw failure("v2.marsh-degenerate", "marsh interior is empty or degenerate");
            }
            int hydroperiod = midpoint(parameters.hydroperiodBlocks());
            long wetness = midpointMillionths(parameters.wetness01());
            long openWater = midpointMillionths(parameters.openWaterShare01());
            int microRelief = midpoint(parameters.microReliefBlocks());
            int groundwater = midpoint(parameters.groundwaterMinDepthBlocks());
            if (wetness < 200_000L) {
                throw failure("v2.marsh-dry", "dry marsh rejected: wetness below 0.2");
            }
            if (groundwater > hydroperiod + 16) {
                throw failure("v2.marsh-groundwater-hydroperiod",
                        "marsh groundwater/hydroperiod conflict");
            }
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 7L);
            if (work > MarshPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.marsh-budget", "marsh profile/raster budget exceeded");
            }
            int support = Math.max(microRelief, 1);
            if (support > LandformMarshModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.marsh-budget", "marsh support radius exceeds trusted halo");
            }
            return new MarshPlanV2(
                    MarshPlanV2.VERSION,
                    feature.id(),
                    rings,
                    hydroperiod,
                    wetness,
                    openWater,
                    microRelief,
                    groundwater,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    MarshPlanV2.MARSH_MASK_FIELD_ID,
                    MarshPlanV2.OPEN_WATER_FIELD_ID,
                    MarshPlanV2.MICRO_RELIEF_FIELD_ID,
                    MarshPlanV2.WETNESS_FIELD_ID,
                    MarshPlanV2.HYDROPERIOD_FIELD_ID,
                    MarshPlanV2.FLUID_OWNERSHIP_FIELD_ID,
                    MarshPlanV2.SOLID_OWNERSHIP_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException("v2.marsh-budget", "marsh arithmetic overflow", exception);
        }
    }

    private static List<MarshPlanV2.Ring> toRings(
            TerrainIntentV2.PolygonGeometry polygon,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<MarshPlanV2.Ring> rings = new ArrayList<>(polygon.rings().size());
        for (List<TerrainIntentV2.Point2> ring : polygon.rings()) {
            List<MarshPlanV2.Vertex> vertices = new ArrayList<>(ring.size());
            for (TerrainIntentV2.Point2 point : ring) {
                long x = scaleCoordinate(point.xMillionths(), bounds.width());
                long z = scaleCoordinate(point.zMillionths(), bounds.length());
                vertices.add(new MarshPlanV2.Vertex(x, z));
            }
            rings.add(new MarshPlanV2.Ring(List.copyOf(vertices)));
        }
        return List.copyOf(rings);
    }

    private static long estimateInteriorCells(
            List<MarshPlanV2.Ring> rings,
            WorldBlueprintV2.Bounds bounds
    ) {
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (MarshPlanV2.Ring ring : rings) {
            for (MarshPlanV2.Vertex vertex : ring.vertices()) {
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
                if (MarshFixedMathV2.contains(rings, cx, cz)) {
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

    private static long midpointMillionths(TerrainIntentV2.FixedRange range) {
        return range.minimumMillionths()
                + (range.maximumMillionths() - range.minimumMillionths()) / 2L;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
