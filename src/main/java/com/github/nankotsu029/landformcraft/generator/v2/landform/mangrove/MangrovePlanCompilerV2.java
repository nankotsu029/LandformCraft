package com.github.nankotsu029.landformcraft.generator.v2.landform.mangrove;

import com.github.nankotsu029.landformcraft.model.v2.MangroveWetlandPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one MANGROVE_WETLAND polygon and optional tidal parent hook. */
public final class MangrovePlanCompilerV2 {
    public MangroveWetlandPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.MANGROVE_WETLAND) {
            throw failure("v2.mangrove-kind", "feature kind is not MANGROVE_WETLAND");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.mangrove-geometry", "mangrove requires POLYGON geometry");
        }
        TerrainIntentV2.MangroveWetlandParameters parameters =
                (TerrainIntentV2.MangroveWetlandParameters) feature.parameters();
        try {
            List<MangroveWetlandPlanV2.Ring> rings = toRings(polygon, bounds);
            long interiorCells = estimateInteriorCells(rings, bounds);
            if (interiorCells < 1L) {
                throw failure("v2.mangrove-degenerate", "mangrove wetland interior is empty or degenerate");
            }
            MangroveWetlandPlanV2.TidalNetworkPlanHook tidalHook = resolveTidalNetworkHook(feature, intent);
            int microRelief = midpoint(parameters.microReliefBlocks());
            long waterloggedShare = midpoint(parameters.waterloggedShare01());
            long box = Math.multiplyExact((long) bounds.width(), bounds.length());
            long work = Math.multiplyExact(box, 5L);
            if (work > MangroveWetlandPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.mangrove-budget", "mangrove profile/raster budget exceeded");
            }
            int support = Math.max(1, microRelief);
            if (support > LandformMangroveModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.mangrove-budget", "mangrove support radius exceeds trusted halo");
            }
            return new MangroveWetlandPlanV2(
                    MangroveWetlandPlanV2.VERSION,
                    feature.id(),
                    rings,
                    tidalHook,
                    parameters.microReliefBlocks().minimum(),
                    microRelief,
                    parameters.microReliefBlocks().maximum(),
                    parameters.waterloggedShare01().minimumMillionths(),
                    waterloggedShare,
                    parameters.waterloggedShare01().maximumMillionths(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    LandformMangroveModuleV2.WETLAND_MASK_FIELD_ID,
                    LandformMangroveModuleV2.SURFACE_HEIGHT_FIELD_ID,
                    LandformMangroveModuleV2.OPEN_WATER_GAP_FIELD_ID,
                    LandformMangroveModuleV2.SUBSTRATE_CLASS_FIELD_ID,
                    LandformMangroveModuleV2.MICRO_RELIEF_FIELD_ID,
                    support,
                    work,
                    geometryChecksum);
        } catch (ArithmeticException exception) {
            throw new MangroveGenerationException("v2.mangrove-budget", "mangrove arithmetic overflow", exception);
        }
    }

    private static MangroveWetlandPlanV2.TidalNetworkPlanHook resolveTidalNetworkHook(
            TerrainIntentV2.Feature mangrove,
            TerrainIntentV2 intent
    ) {
        List<TerrainIntentV2.Relation> within = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.WITHIN
                        && relation.to().equals("feature:" + mangrove.id()))
                .toList();
        if (within.isEmpty()) {
            return null;
        }
        List<TerrainIntentV2.Relation> hard = within.stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .toList();
        if (hard.size() != 1 || within.size() != hard.size()) {
            throw failure("v2.mangrove-tidal-hook",
                    "mangrove allows exactly one HARD WITHIN tidal parent hook");
        }
        TerrainIntentV2.Relation relation = hard.getFirst();
        if (!relation.from().startsWith("feature:")) {
            throw failure("v2.mangrove-tidal-hook", "mangrove tidal hook must originate at a feature");
        }
        String tidalId = relation.from().substring("feature:".length());
        TerrainIntentV2.Feature tidal = intent.features().stream()
                .filter(candidate -> candidate.id().equals(tidalId))
                .findFirst()
                .orElseThrow(() -> failure("v2.mangrove-tidal-hook", "mangrove tidal parent feature is missing"));
        if (tidal.kind() != TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK) {
            throw failure("v2.mangrove-tidal-hook",
                    "mangrove WITHIN parent hook must originate at TIDAL_CHANNEL_NETWORK");
        }
        return new MangroveWetlandPlanV2.TidalNetworkPlanHook(tidalId, relation.id());
    }

    private static List<MangroveWetlandPlanV2.Ring> toRings(
            TerrainIntentV2.PolygonGeometry polygon,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<MangroveWetlandPlanV2.Ring> rings = new ArrayList<>(polygon.rings().size());
        for (List<TerrainIntentV2.Point2> ring : polygon.rings()) {
            List<MangroveWetlandPlanV2.Vertex> vertices = new ArrayList<>(ring.size());
            for (TerrainIntentV2.Point2 point : ring) {
                vertices.add(new MangroveWetlandPlanV2.Vertex(
                        Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L),
                        Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L)));
            }
            rings.add(new MangroveWetlandPlanV2.Ring(List.copyOf(vertices)));
        }
        return List.copyOf(rings);
    }

    private static long estimateInteriorCells(
            List<MangroveWetlandPlanV2.Ring> rings,
            WorldBlueprintV2.Bounds bounds
    ) {
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (MangroveWetlandPlanV2.Ring ring : rings) {
            for (MangroveWetlandPlanV2.Vertex vertex : ring.vertices()) {
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
                if (MangroveFixedMathV2.contains(rings, cx, cz)) {
                    cells = Math.addExact(cells, 1L);
                }
            }
        }
        return cells;
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static long midpoint(TerrainIntentV2.FixedRange range) {
        return range.minimumMillionths()
                + (range.maximumMillionths() - range.minimumMillionths()) / 2L;
    }

    private static MangroveGenerationException failure(String ruleId, String message) {
        return new MangroveGenerationException(ruleId, message);
    }
}
