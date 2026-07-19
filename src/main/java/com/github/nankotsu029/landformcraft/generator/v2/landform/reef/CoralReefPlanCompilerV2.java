package com.github.nankotsu029.landformcraft.generator.v2.landform.reef;

import com.github.nankotsu029.landformcraft.model.v2.CoralReefPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Integer-only compiler for one CORAL_REEF polygon with lagoon and reef-pass hooks. */
public final class CoralReefPlanCompilerV2 {
    public CoralReefPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.CORAL_REEF) {
            throw failure("v2.reef-kind", "feature kind is not CORAL_REEF");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.reef-geometry", "coral reef requires POLYGON geometry");
        }
        if (polygon.rings().size() < 2) {
            throw failure("v2.reef-geometry", "coral reef requires outer ring and lagoon hole");
        }
        TerrainIntentV2.CoralReefParameters parameters =
                (TerrainIntentV2.CoralReefParameters) feature.parameters();
        try {
            List<CoralReefPlanV2.Ring> rings = toRings(polygon, bounds);
            CoralReefPlanV2.LagoonPlanHook lagoonHook = resolveLagoonHook(feature, intent);
            TerrainIntentV2.Feature lagoon = intent.features().stream()
                    .filter(candidate -> candidate.id().equals(lagoonHook.lagoonFeatureId()))
                    .findFirst()
                    .orElseThrow(() -> failure("v2.reef-lagoon-hook", "coral reef lagoon feature is missing"));
            if (lagoon.kind() != TerrainIntentV2.FeatureKind.LAGOON) {
                throw failure("v2.reef-lagoon-hook", "coral reef ENCLOSED_BY hook must target LAGOON");
            }
            TerrainIntentV2.LagoonParameters lagoonParameters =
                    (TerrainIntentV2.LagoonParameters) lagoon.parameters();
            List<CoralReefPlanV2.ReefPassPlanHook> passHooks = resolvePassHooks(feature, intent, lagoonHook, bounds, rings);
            long reefCells = estimateReefCells(rings, bounds);
            if (reefCells < 1L) {
                throw failure("v2.reef-degenerate", "coral reef ring interior is empty or degenerate");
            }
            int crestDepth = midpoint(parameters.reefCrestDepthBlocks());
            int reefWidth = midpoint(parameters.reefWidthBlocks());
            int outerSlope = midpoint(parameters.outerSlopeDegrees());
            int lagoonDepth = midpoint(lagoonParameters.depthBlocks());
            long box = Math.multiplyExact((long) bounds.width(), bounds.length());
            long work = Math.multiplyExact(box, 5L);
            if (work > CoralReefPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.reef-budget", "coral reef profile/raster budget exceeded");
            }
            int support = Math.max(reefWidth, 1);
            if (support > LandformCoralReefModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.reef-budget", "coral reef support radius exceeds trusted halo");
            }
            return new CoralReefPlanV2(
                    CoralReefPlanV2.VERSION,
                    feature.id(),
                    rings,
                    parameters.reefCrestDepthBlocks().minimum(),
                    crestDepth,
                    parameters.reefCrestDepthBlocks().maximum(),
                    parameters.reefWidthBlocks().minimum(),
                    reefWidth,
                    parameters.reefWidthBlocks().maximum(),
                    parameters.outerSlopeDegrees().minimum(),
                    outerSlope,
                    parameters.outerSlopeDegrees().maximum(),
                    lagoonHook,
                    passHooks,
                    lagoonParameters.depthBlocks().minimum(),
                    lagoonDepth,
                    lagoonParameters.depthBlocks().maximum(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    LandformCoralReefModuleV2.REEF_MASK_FIELD_ID,
                    LandformCoralReefModuleV2.CREST_DEPTH_FIELD_ID,
                    LandformCoralReefModuleV2.LAGOON_DEPTH_FIELD_ID,
                    LandformCoralReefModuleV2.PASS_CORRIDOR_FIELD_ID,
                    LandformCoralReefModuleV2.MARINE_CONNECTION_FIELD_ID,
                    support,
                    work,
                    geometryChecksum);
        } catch (ArithmeticException exception) {
            throw new CoralReefGenerationException("v2.reef-budget", "coral reef arithmetic overflow", exception);
        }
    }

    private static CoralReefPlanV2.LagoonPlanHook resolveLagoonHook(
            TerrainIntentV2.Feature reef,
            TerrainIntentV2 intent
    ) {
        List<TerrainIntentV2.Relation> enclosed = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ENCLOSED_BY
                        && relation.to().equals("feature:" + reef.id()))
                .toList();
        if (enclosed.isEmpty()) {
            throw failure("v2.reef-lagoon-hook", "coral reef requires HARD ENCLOSED_BY lagoon hook");
        }
        List<TerrainIntentV2.Relation> hard = enclosed.stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .toList();
        if (hard.size() != 1 || enclosed.size() != hard.size()) {
            throw failure("v2.reef-lagoon-hook", "coral reef allows exactly one HARD ENCLOSED_BY lagoon hook");
        }
        TerrainIntentV2.Relation relation = hard.getFirst();
        if (!relation.from().startsWith("feature:")) {
            throw failure("v2.reef-lagoon-hook", "coral reef lagoon hook must originate at a feature");
        }
        return new CoralReefPlanV2.LagoonPlanHook(relation.from().substring("feature:".length()), relation.id());
    }

    private static List<CoralReefPlanV2.ReefPassPlanHook> resolvePassHooks(
            TerrainIntentV2.Feature reef,
            TerrainIntentV2 intent,
            CoralReefPlanV2.LagoonPlanHook lagoonHook,
            WorldBlueprintV2.Bounds bounds,
            List<CoralReefPlanV2.Ring> rings
    ) {
        List<CoralReefPlanV2.ReefPassPlanHook> hooks = new ArrayList<>();
        for (TerrainIntentV2.Feature candidate : intent.features()) {
            if (candidate.kind() != TerrainIntentV2.FeatureKind.REEF_PASS) {
                continue;
            }
            List<TerrainIntentV2.Relation> carves = intent.relations().stream()
                    .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.CARVES_THROUGH
                            && relation.from().equals("feature:" + candidate.id())
                            && relation.to().equals("feature:" + reef.id()))
                    .toList();
            List<TerrainIntentV2.Relation> connects = intent.relations().stream()
                    .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.CONNECTS_TO
                            && relation.from().equals("feature:" + candidate.id())
                            && relation.to().equals("feature:" + lagoonHook.lagoonFeatureId()))
                    .toList();
            if (carves.isEmpty() && connects.isEmpty()) {
                continue;
            }
            if (carves.size() != 1 || connects.size() != 1
                    || carves.getFirst().strength() != TerrainIntentV2.Strength.HARD
                    || connects.getFirst().strength() != TerrainIntentV2.Strength.HARD) {
                throw failure("v2.reef-pass-hook",
                        "reef pass requires exactly one HARD CARVES_THROUGH and one HARD CONNECTS_TO");
            }
            if (!(candidate.geometry() instanceof TerrainIntentV2.SplineGeometry spline)) {
                throw failure("v2.reef-pass-geometry", "reef pass requires SPLINE geometry");
            }
            TerrainIntentV2.ReefPassParameters passParameters =
                    (TerrainIntentV2.ReefPassParameters) candidate.parameters();
            List<CoralReefPlanV2.CenterlinePoint> centerline = sampleCenterline(spline, bounds);
            validatePassEndpoints(centerline, rings);
            hooks.add(new CoralReefPlanV2.ReefPassPlanHook(
                    candidate.id(),
                    carves.getFirst().id(),
                    connects.getFirst().id(),
                    centerline,
                    passParameters.widthBlocks().minimum(),
                    midpoint(passParameters.widthBlocks()),
                    passParameters.widthBlocks().maximum(),
                    passParameters.waterDepthBlocks().minimum(),
                    midpoint(passParameters.waterDepthBlocks()),
                    passParameters.waterDepthBlocks().maximum()));
        }
        return List.copyOf(hooks);
    }

    private static void validatePassEndpoints(
            List<CoralReefPlanV2.CenterlinePoint> centerline,
            List<CoralReefPlanV2.Ring> rings
    ) {
        CoralReefPlanV2.CenterlinePoint first = centerline.getFirst();
        CoralReefPlanV2.CenterlinePoint last = centerline.getLast();
        if (CoralReefFixedMathV2.inLagoon(rings, first.xMillionths(), first.zMillionths())) {
            throw failure("v2.reef-pass-endpoint", "reef pass centerline must start outside or on the outer rim");
        }
        if (!CoralReefFixedMathV2.inLagoon(rings, last.xMillionths(), last.zMillionths())) {
            throw failure("v2.reef-pass-endpoint", "reef pass centerline must end inside the lagoon");
        }
    }

    private static List<CoralReefPlanV2.CenterlinePoint> sampleCenterline(
            TerrainIntentV2.SplineGeometry spline,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<long[]> controls = new ArrayList<>();
        for (TerrainIntentV2.Point2 point : spline.points()) {
            controls.add(new long[]{
                    Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L),
                    Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L)});
        }
        List<CoralReefPlanV2.CenterlinePoint> result = new ArrayList<>();
        long arc = 0L;
        result.add(new CoralReefPlanV2.CenterlinePoint(controls.getFirst()[0], controls.getFirst()[1], 0L));
        for (int i = 1; i < controls.size(); i++) {
            long[] a = controls.get(i - 1);
            long[] b = controls.get(i);
            long dx = b[0] - a[0];
            long dz = b[1] - a[1];
            long distance = CoralReefFixedMathV2.hypot(dx, dz);
            int steps = Math.max(1, Math.toIntExact(
                    (distance + TerrainIntentV2.FIXED_SCALE - 1L) / TerrainIntentV2.FIXED_SCALE));
            for (int step = 1; step <= steps; step++) {
                long x = a[0] + dx * step / steps;
                long z = a[1] + dz * step / steps;
                CoralReefPlanV2.CenterlinePoint previous = result.getLast();
                long segment = CoralReefFixedMathV2.hypot(
                        x - previous.xMillionths(), z - previous.zMillionths());
                if (segment == 0) {
                    continue;
                }
                arc = Math.addExact(arc, segment);
                result.add(new CoralReefPlanV2.CenterlinePoint(x, z, arc));
            }
        }
        return List.copyOf(result);
    }

    private static List<CoralReefPlanV2.Ring> toRings(
            TerrainIntentV2.PolygonGeometry polygon,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<CoralReefPlanV2.Ring> rings = new ArrayList<>(polygon.rings().size());
        for (List<TerrainIntentV2.Point2> ring : polygon.rings()) {
            List<CoralReefPlanV2.Vertex> vertices = new ArrayList<>(ring.size());
            for (TerrainIntentV2.Point2 point : ring) {
                vertices.add(new CoralReefPlanV2.Vertex(
                        Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L),
                        Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L)));
            }
            rings.add(new CoralReefPlanV2.Ring(List.copyOf(vertices)));
        }
        return List.copyOf(rings);
    }

    private static long estimateReefCells(List<CoralReefPlanV2.Ring> rings, WorldBlueprintV2.Bounds bounds) {
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (CoralReefPlanV2.Ring ring : rings) {
            for (CoralReefPlanV2.Vertex vertex : ring.vertices()) {
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
                if (CoralReefFixedMathV2.onReef(rings, cx, cz)) {
                    cells = Math.addExact(cells, 1L);
                }
            }
        }
        return cells;
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static CoralReefGenerationException failure(String ruleId, String message) {
        return new CoralReefGenerationException(ruleId, message);
    }
}
