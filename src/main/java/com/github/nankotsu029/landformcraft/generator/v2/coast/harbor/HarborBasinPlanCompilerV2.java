package com.github.nankotsu029.landformcraft.generator.v2.coast.harbor;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.HarborBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles polygon/opening topology into a frozen local 2.5D harbor plan. */
public final class HarborBasinPlanCompilerV2 {
    private static final long SCALE = TerrainIntentV2.FIXED_SCALE;

    public HarborBasinPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            CoastalFeaturePlanV2 coastalPlan,
            WorldBlueprintV2.Bounds bounds
    ) {
        if (feature.kind() != TerrainIntentV2.FeatureKind.HARBOR_BASIN
                || coastalPlan.kind() != TerrainIntentV2.FeatureKind.HARBOR_BASIN
                || !feature.id().equals(coastalPlan.featureId())) {
            throw failure("v2.harbor-basin-plan-binding", "feature and coastal plan do not describe one harbor basin");
        }
        TerrainIntentV2.HarborBasinParameters parameters =
                (TerrainIntentV2.HarborBasinParameters) feature.parameters();
        TerrainIntentV2.Feature breakwater = requireHardEnclosure(feature, parameters, intent);
        Map<String, TerrainIntentV2.Point2> endpoints = endpoints(breakwater);
        List<Endpoint> opening = new ArrayList<>(2);
        for (String endpointId : parameters.entranceEndpointIds()) {
            TerrainIntentV2.Point2 point = endpoints.get(endpointId);
            if (point == null) {
                throw failure("v2.harbor-basin-unknown-entrance", "harbor entrance endpoint is not owned by its breakwater");
            }
            opening.add(new Endpoint(endpointId, blockPoint(point, bounds)));
        }

        List<CoastalFeaturePlanV2.BlockPoint> outer = coastalPlan.geometry().rings().getFirst().points();
        OrderedOpening ordered = requireOpeningEdge(outer, opening);
        long dx = Math.subtractExact(ordered.second().point().xMillionths(), ordered.first().point().xMillionths());
        long dz = Math.subtractExact(ordered.second().point().zMillionths(), ordered.first().point().zMillionths());
        long width = HarborBasinFixedMathV2.integerSquareRoot(Math.addExact(
                Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz)));
        if (width < 2L * SCALE) {
            throw failure("v2.harbor-basin-opening-width", "harbor entrance must be at least two blocks wide");
        }

        long normalX = HarborBasinFixedMathV2.roundDivide(Math.multiplyExact(-dz, SCALE), width);
        long normalZ = HarborBasinFixedMathV2.roundDivide(Math.multiplyExact(dx, SCALE), width);
        long midpointX = HarborBasinFixedMathV2.roundDivide(Math.addExact(
                ordered.first().point().xMillionths(), ordered.second().point().xMillionths()), 2L);
        long midpointZ = HarborBasinFixedMathV2.roundDivide(Math.addExact(
                ordered.first().point().zMillionths(), ordered.second().point().zMillionths()), 2L);
        boolean firstNormalInside = contains(coastalPlan.geometry().rings(),
                Math.addExact(midpointX, normalX), Math.addExact(midpointZ, normalZ));
        boolean secondNormalInside = contains(coastalPlan.geometry().rings(),
                Math.subtractExact(midpointX, normalX), Math.subtractExact(midpointZ, normalZ));
        if (firstNormalInside == secondNormalInside) {
            throw failure("v2.harbor-basin-closed-entrance", "entrance edge has no unique interior side");
        }
        int outwardX = Math.toIntExact(firstNormalInside ? -normalX : normalX);
        int outwardZ = Math.toIntExact(firstNormalInside ? -normalZ : normalZ);

        requireDimensions(outer, parameters.profileTransitionBlocks());
        if ((long) bounds.waterLevel() - parameters.waterDepthBlocks().maximum() < bounds.minY()) {
            throw failure("v2.harbor-basin-vertical-bounds", "maximum harbor depth exceeds vertical bounds");
        }
        int support = Math.max(parameters.profileTransitionBlocks(), parameters.entranceCorridorLengthBlocks());
        return new HarborBasinPlanV2(
                HarborBasinPlanV2.VERSION,
                feature.id(),
                HarborBasinPlanV2.BottomProfileKind.valueOf(parameters.bottomProfile().name()),
                parameters.waterDepthBlocks().minimum(), parameters.waterDepthBlocks().maximum(),
                parameters.profileTransitionBlocks(),
                parameters.entranceEndpointIds().stream().sorted().toList(),
                ordered.first().point(), ordered.second().point(), outwardX, outwardZ, width,
                parameters.entranceCorridorLengthBlocks(),
                bounds.minY(), bounds.maxY(), bounds.waterLevel(),
                CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID,
                CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID,
                CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID,
                CoastalFoundationModuleV2.HARBOR_BOTTOM_HEIGHT_FIELD_ID,
                support);
    }

    private static TerrainIntentV2.Feature requireHardEnclosure(
            TerrainIntentV2.Feature basin,
            TerrainIntentV2.HarborBasinParameters parameters,
            TerrainIntentV2 intent
    ) {
        Set<String> expectedEndpoints = new HashSet<>(parameters.entranceEndpointIds());
        return intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> (relation.kind() == TerrainIntentV2.RelationKind.ENCLOSES
                        && relation.to().equals("feature:" + basin.id()))
                        || (relation.kind() == TerrainIntentV2.RelationKind.ENCLOSED_BY
                        && relation.from().equals("feature:" + basin.id())))
                .map(relation -> relation.kind() == TerrainIntentV2.RelationKind.ENCLOSES
                        ? relation.from() : relation.to())
                .filter(endpoint -> endpoint.startsWith("feature:"))
                .map(endpoint -> endpoint.substring("feature:".length()))
                .map(id -> intent.features().stream().filter(feature -> feature.id().equals(id)).findFirst().orElse(null))
                .filter(feature -> feature != null && feature.kind() == TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR)
                .filter(feature -> new HashSet<>(((TerrainIntentV2.BreakwaterHarborParameters)
                        feature.parameters()).opening().betweenEndpointIds()).equals(expectedEndpoints))
                .sorted(Comparator.comparing(TerrainIntentV2.Feature::id))
                .findFirst()
                .orElseThrow(() -> failure(
                        "v2.harbor-basin-relation", "harbor basin requires a HARD enclosing breakwater relation with matching endpoints"));
    }

    private static Map<String, TerrainIntentV2.Point2> endpoints(TerrainIntentV2.Feature feature) {
        if (!(feature.geometry() instanceof TerrainIntentV2.MultiSplineGeometry multi)) {
            throw failure("v2.harbor-basin-relation", "enclosing breakwater does not expose named paths");
        }
        Map<String, TerrainIntentV2.Point2> result = new HashMap<>();
        for (TerrainIntentV2.NamedPath path : multi.paths()) {
            addEndpoint(result, path.startEndpointId(), path.points().getFirst());
            addEndpoint(result, path.endEndpointId(), path.points().getLast());
        }
        return Map.copyOf(result);
    }

    private static void addEndpoint(Map<String, TerrainIntentV2.Point2> endpoints, String id,
                                    TerrainIntentV2.Point2 point) {
        if (id == null) return;
        TerrainIntentV2.Point2 previous = endpoints.putIfAbsent(id, point);
        if (previous != null && !previous.equals(point)) {
            throw failure("v2.harbor-basin-ambiguous-entrance", "named endpoint resolves to multiple coordinates");
        }
    }

    private static OrderedOpening requireOpeningEdge(
            List<CoastalFeaturePlanV2.BlockPoint> ring,
            List<Endpoint> endpoints
    ) {
        int edgeCount = ring.size() - 1;
        for (int index = 0; index < edgeCount; index++) {
            CoastalFeaturePlanV2.BlockPoint first = ring.get(index);
            CoastalFeaturePlanV2.BlockPoint second = ring.get(index + 1);
            if (first.equals(endpoints.getFirst().point()) && second.equals(endpoints.getLast().point())) {
                return new OrderedOpening(endpoints.getFirst(), endpoints.getLast());
            }
            if (first.equals(endpoints.getLast().point()) && second.equals(endpoints.getFirst().point())) {
                return new OrderedOpening(endpoints.getLast(), endpoints.getFirst());
            }
        }
        throw failure("v2.harbor-basin-closed-entrance", "entrance endpoints must form one outer-ring opening edge");
    }

    private static void requireDimensions(List<CoastalFeaturePlanV2.BlockPoint> outer, int transitionBlocks) {
        long minimumX = outer.stream().mapToLong(CoastalFeaturePlanV2.BlockPoint::xMillionths).min().orElseThrow();
        long maximumX = outer.stream().mapToLong(CoastalFeaturePlanV2.BlockPoint::xMillionths).max().orElseThrow();
        long minimumZ = outer.stream().mapToLong(CoastalFeaturePlanV2.BlockPoint::zMillionths).min().orElseThrow();
        long maximumZ = outer.stream().mapToLong(CoastalFeaturePlanV2.BlockPoint::zMillionths).max().orElseThrow();
        long required = Math.multiplyExact(Math.multiplyExact((long) transitionBlocks, 2L), SCALE);
        if (maximumX - minimumX < required || maximumZ - minimumZ < required) {
            throw failure("v2.harbor-basin-dimensions", "basin dimensions cannot realize the requested bottom profile");
        }
    }

    static boolean contains(List<CoastalFeaturePlanV2.BlockRing> rings, long x, long z) {
        if (!insideOrBoundary(rings.getFirst().points(), x, z)) return false;
        for (int index = 1; index < rings.size(); index++) {
            if (insideOrBoundary(rings.get(index).points(), x, z)) return false;
        }
        return true;
    }

    private static boolean insideOrBoundary(List<CoastalFeaturePlanV2.BlockPoint> ring, long x, long z) {
        boolean inside = false;
        for (int index = 0; index < ring.size() - 1; index++) {
            CoastalFeaturePlanV2.BlockPoint a = ring.get(index);
            CoastalFeaturePlanV2.BlockPoint b = ring.get(index + 1);
            long cross = cross(a, b, x, z);
            if (cross == 0L && between(x, a.xMillionths(), b.xMillionths())
                    && between(z, a.zMillionths(), b.zMillionths())) return true;
            if ((a.zMillionths() > z) != (b.zMillionths() > z)) {
                long dy = Math.subtractExact(b.zMillionths(), a.zMillionths());
                boolean crossesRight = (dy > 0L && cross > 0L) || (dy < 0L && cross < 0L);
                if (crossesRight) inside = !inside;
            }
        }
        return inside;
    }

    private static long cross(CoastalFeaturePlanV2.BlockPoint a, CoastalFeaturePlanV2.BlockPoint b,
                              long x, long z) {
        return Math.subtractExact(
                Math.multiplyExact(Math.subtractExact(b.xMillionths(), a.xMillionths()),
                        Math.subtractExact(z, a.zMillionths())),
                Math.multiplyExact(Math.subtractExact(b.zMillionths(), a.zMillionths()),
                        Math.subtractExact(x, a.xMillionths())));
    }

    private static boolean between(long value, long first, long second) {
        return value >= Math.min(first, second) && value <= Math.max(first, second);
    }

    private static CoastalFeaturePlanV2.BlockPoint blockPoint(
            TerrainIntentV2.Point2 point,
            WorldBlueprintV2.Bounds bounds
    ) {
        return new CoastalFeaturePlanV2.BlockPoint(
                Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L),
                Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L));
    }

    private static HarborBasinGenerationException failure(String ruleId, String message) {
        return new HarborBasinGenerationException(ruleId, message);
    }

    private record Endpoint(String id, CoastalFeaturePlanV2.BlockPoint point) { }
    private record OrderedOpening(Endpoint first, Endpoint second) { }
}
