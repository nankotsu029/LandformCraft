package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformPlateauModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.plateau.PlateauFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlateauPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one PLATEAU polygon feature. */
public final class PlateauPlanCompilerV2 {
    public PlateauPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.PLATEAU) {
            throw failure("v2.plateau-kind", "feature kind is not PLATEAU");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.plateau-geometry", "plateau requires POLYGON geometry");
        }
        TerrainIntentV2.PlateauParameters parameters =
                (TerrainIntentV2.PlateauParameters) feature.parameters();
        try {
            List<PlateauPlanV2.Ring> rings = toRings(polygon, bounds);
            long interiorCells = estimateInteriorCells(rings, bounds);
            if (interiorCells < 1L) {
                throw failure("v2.plateau-degenerate", "plateau interior is empty or degenerate");
            }
            int capElevation = midpoint(parameters.capElevationBlocks());
            int capRelief = midpoint(parameters.capReliefBlocks());
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 4L);
            if (work > PlateauPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.plateau-budget", "plateau profile/raster budget exceeded");
            }
            int support = Math.max(1, capRelief + 2);
            if (support > LandformPlateauModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.plateau-budget", "plateau support radius exceeds trusted halo");
            }
            return new PlateauPlanV2(
                    PlateauPlanV2.VERSION,
                    feature.id(),
                    rings,
                    capElevation,
                    capRelief,
                    parameters.profile(),
                    parameters.escarpmentTransitionBandBlocks(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.width(),
                    bounds.length(),
                    PlateauPlanV2.CAP_MASK_FIELD_ID,
                    PlateauPlanV2.OWNERSHIP_FIELD_ID,
                    PlateauPlanV2.ELEVATION_FIELD_ID,
                    PlateauPlanV2.MATERIAL_HANDOFF_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException("v2.plateau-budget", "plateau arithmetic overflow", exception);
        }
    }

    private static List<PlateauPlanV2.Ring> toRings(
            TerrainIntentV2.PolygonGeometry polygon,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<PlateauPlanV2.Ring> rings = new ArrayList<>(polygon.rings().size());
        for (List<TerrainIntentV2.Point2> ring : polygon.rings()) {
            List<PlateauPlanV2.Vertex> vertices = new ArrayList<>(ring.size());
            for (TerrainIntentV2.Point2 point : ring) {
                long x = scaleCoordinate(point.xMillionths(), bounds.width());
                long z = scaleCoordinate(point.zMillionths(), bounds.length());
                vertices.add(new PlateauPlanV2.Vertex(x, z));
            }
            rings.add(new PlateauPlanV2.Ring(List.copyOf(vertices)));
        }
        return List.copyOf(rings);
    }

    private static long estimateInteriorCells(
            List<PlateauPlanV2.Ring> rings,
            WorldBlueprintV2.Bounds bounds
    ) {
        long count = 0L;
        for (int z = 0; z < bounds.length(); z++) {
            for (int x = 0; x < bounds.width(); x++) {
                long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                        TerrainIntentV2.FIXED_SCALE / 2L);
                long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                        TerrainIntentV2.FIXED_SCALE / 2L);
                if (PlateauFixedMathV2.contains(rings, px, pz)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static long scaleCoordinate(int normalized, int dimension) {
        return Math.multiplyExact((long) normalized, dimension - 1L);
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
