package com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.BreakwaterHarborPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.HarborBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles two named arms, their clear opening, and one HARD enclosed basin into a frozen plan. */
public final class BreakwaterHarborPlanCompilerV2 {
    private static final long SCALE = TerrainIntentV2.FIXED_SCALE;

    public BreakwaterHarborPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            CoastalFeaturePlanV2 coastalPlan,
            HarborBasinPlanV2 basinPlan,
            WorldBlueprintV2.Bounds bounds
    ) {
        if (feature.kind() != TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR
                || coastalPlan.kind() != TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR
                || !feature.id().equals(coastalPlan.featureId())) {
            throw failure("v2.breakwater-plan-binding", "feature and coastal plan do not describe one breakwater");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.MultiSplineGeometry geometry)
                || geometry.paths().size() != 2) {
            throw failure("v2.breakwater-geometry", "breakwater requires exactly two named spline arms");
        }
        TerrainIntentV2.BreakwaterHarborParameters parameters =
                (TerrainIntentV2.BreakwaterHarborParameters) feature.parameters();
        TerrainIntentV2.Relation relation = requireHardEnclosure(feature.id(), basinPlan.featureId(), intent);

        Set<String> requestedOpeningIds = new HashSet<>(parameters.opening().betweenEndpointIds());
        Map<String, TerrainIntentV2.NamedPath> sourcePaths = new HashMap<>();
        Map<String, CoastalFeaturePlanV2.BlockPoint> endpoints = new HashMap<>();
        for (TerrainIntentV2.NamedPath path : geometry.paths()) {
            if (path.startEndpointId() == null || path.startEndpointId().isBlank()
                    || path.endEndpointId() == null || path.endEndpointId().isBlank()) {
                throw failure("v2.breakwater-subgeometry", "each arm requires named start and end endpoints");
            }
            sourcePaths.put(path.id(), path);
            addEndpoint(endpoints, path.startEndpointId(), blockPoint(path.points().getFirst(), bounds));
            addEndpoint(endpoints, path.endEndpointId(), blockPoint(path.points().getLast(), bounds));
        }
        for (String endpointId : requestedOpeningIds) {
            if (!endpoints.containsKey(endpointId)) {
                throw failure("v2.breakwater-subgeometry", "opening references an endpoint not owned by this breakwater");
            }
        }
        if (!requestedOpeningIds.equals(new HashSet<>(basinPlan.entranceEndpointIds()))) {
            throw failure("v2.breakwater-basin-opening", "breakwater and basin opening endpoint ids differ");
        }

        List<BreakwaterPathKernelV2.ArmGeometry> flattened = BreakwaterPathKernelV2.flatten(coastalPlan);
        if (BreakwaterPathKernelV2.intersects(flattened.getFirst(), flattened.getLast())) {
            throw failure("v2.breakwater-crossing-arms", "breakwater arms intersect");
        }
        if (flattened.stream().anyMatch(BreakwaterPathKernelV2::selfIntersects)) {
            throw failure("v2.breakwater-self-intersection", "breakwater arm self-intersects");
        }

        List<BreakwaterHarborPlanV2.ArmPlan> arms = new ArrayList<>(2);
        Set<String> usedOpeningIds = new HashSet<>();
        for (int index = 0; index < flattened.size(); index++) {
            BreakwaterPathKernelV2.ArmGeometry arm = flattened.get(index);
            TerrainIntentV2.NamedPath path = sourcePaths.get(arm.armId());
            if (path == null) throw failure("v2.breakwater-subgeometry", "compiled arm has no source subgeometry");
            boolean openingAtStart = requestedOpeningIds.contains(path.startEndpointId());
            boolean openingAtEnd = requestedOpeningIds.contains(path.endEndpointId());
            if (openingAtStart == openingAtEnd) {
                throw failure("v2.breakwater-opening-topology", "each arm must terminate at exactly one opening endpoint");
            }
            String openingId = openingAtStart ? path.startEndpointId() : path.endEndpointId();
            if (!usedOpeningIds.add(openingId)) {
                throw failure("v2.breakwater-opening-topology", "opening endpoints must belong to different arms");
            }
            if (arm.lengthMillionths() < Math.max(4L, parameters.crestWidthBlocks() * 2L) * SCALE) {
                throw failure("v2.breakwater-arm-length", "breakwater arm is too short for its crest profile");
            }
            arms.add(new BreakwaterHarborPlanV2.ArmPlan(
                    index + 1, arm.armId(), path.startEndpointId(), path.endEndpointId(), openingId,
                    openingAtStart, arm.lengthMillionths()));
        }

        List<String> openingIds = parameters.opening().betweenEndpointIds().stream().sorted().toList();
        CoastalFeaturePlanV2.BlockPoint openingFirst = endpoints.get(openingIds.getFirst());
        CoastalFeaturePlanV2.BlockPoint openingSecond = endpoints.get(openingIds.getLast());
        requireMatchesBasinOpening(openingFirst, openingSecond, basinPlan);
        long centerDistance = vectorLength(
                openingSecond.xMillionths() - openingFirst.xMillionths(),
                openingSecond.zMillionths() - openingFirst.zMillionths());
        long actualClearWidth = Math.subtractExact(
                centerDistance, Math.multiplyExact((long) parameters.crestWidthBlocks(), SCALE));
        long requestedClearWidth = Math.multiplyExact((long) parameters.opening().widthBlocks(), SCALE);
        if (actualClearWidth < 2L * SCALE || Math.abs(actualClearWidth - requestedClearWidth) > SCALE / 2L) {
            throw failure("v2.breakwater-opening-width",
                    "compiled clear opening differs from the explicit width by more than half a block");
        }

        int maximumDepth = Math.max(parameters.outerDepthBlocks(), basinPlan.maximumDepthBlocks());
        long halfCrest = Math.multiplyExact((long) parameters.crestWidthBlocks(), SCALE) / 2L;
        long toeRun = Math.multiplyExact(
                (long) maximumDepth, parameters.foundationSideSlopeRunPerRiseMillionths());
        int support = Math.toIntExact(BreakwaterFixedMathV2.ceilDivide(
                Math.addExact(halfCrest, toeRun), SCALE));
        if (support > 64 || support > coastalPlan.signedDistance().maximumDistanceBlocks()) {
            throw failure("v2.breakwater-support", "breakwater foundation exceeds the 64-block support bound");
        }
        if ((long) bounds.waterLevel() - maximumDepth < bounds.minY()
                || (long) bounds.waterLevel() + parameters.crestAboveWaterBlocks() > bounds.maxY()) {
            throw failure("v2.breakwater-depth", "breakwater crest or foundation exceeds vertical bounds");
        }

        return new BreakwaterHarborPlanV2(
                BreakwaterHarborPlanV2.VERSION, feature.id(),
                BreakwaterHarborPlanV2.CrestProfileKind.valueOf(parameters.crestProfile().name()),
                BreakwaterHarborPlanV2.FoundationProfileKind.valueOf(parameters.foundationProfile().name()),
                parameters.crestWidthBlocks(), parameters.crestAboveWaterBlocks(),
                parameters.outerDepthBlocks(), basinPlan.maximumDepthBlocks(),
                parameters.foundationSideSlopeRunPerRiseMillionths(), arms, openingIds,
                openingFirst, openingSecond, parameters.opening().widthBlocks(), actualClearWidth,
                parameters.opening().measurement(), parameters.innerSide(), basinPlan.featureId(), relation.id(),
                bounds.minY(), bounds.maxY(), bounds.waterLevel(),
                CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID,
                CoastalFoundationModuleV2.BREAKWATER_ARM_INDEX_FIELD_ID,
                CoastalFoundationModuleV2.BREAKWATER_TOP_HEIGHT_FIELD_ID,
                CoastalFoundationModuleV2.BREAKWATER_BOTTOM_HEIGHT_FIELD_ID,
                support);
    }

    private static TerrainIntentV2.Relation requireHardEnclosure(
            String breakwaterId,
            String basinId,
            TerrainIntentV2 intent
    ) {
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> (relation.kind() == TerrainIntentV2.RelationKind.ENCLOSES
                        && relation.from().equals("feature:" + breakwaterId)
                        && relation.to().equals("feature:" + basinId))
                        || (relation.kind() == TerrainIntentV2.RelationKind.ENCLOSED_BY
                        && relation.from().equals("feature:" + basinId)
                        && relation.to().equals("feature:" + breakwaterId)))
                .toList();
        if (relations.size() != 1) {
            throw failure("v2.breakwater-basin-relation",
                    "breakwater requires exactly one HARD enclosure relation to its harbor basin");
        }
        return relations.getFirst();
    }

    private static void requireMatchesBasinOpening(
            CoastalFeaturePlanV2.BlockPoint first,
            CoastalFeaturePlanV2.BlockPoint second,
            HarborBasinPlanV2 basinPlan
    ) {
        if (first.equals(second)) {
            throw failure("v2.breakwater-basin-opening", "breakwater opening endpoints collapse");
        }
        Set<CoastalFeaturePlanV2.BlockPoint> breakwater = Set.of(first, second);
        Set<CoastalFeaturePlanV2.BlockPoint> basin = Set.of(basinPlan.entranceFirst(), basinPlan.entranceSecond());
        if (!breakwater.equals(basin)) {
            throw failure("v2.breakwater-basin-opening", "breakwater opening coordinates do not match basin entrance");
        }
    }

    private static void addEndpoint(
            Map<String, CoastalFeaturePlanV2.BlockPoint> endpoints,
            String id,
            CoastalFeaturePlanV2.BlockPoint point
    ) {
        CoastalFeaturePlanV2.BlockPoint previous = endpoints.putIfAbsent(id, point);
        if (previous != null && !previous.equals(point)) {
            throw failure("v2.breakwater-subgeometry", "endpoint id resolves to multiple coordinates");
        }
    }

    private static CoastalFeaturePlanV2.BlockPoint blockPoint(
            TerrainIntentV2.Point2 point,
            WorldBlueprintV2.Bounds bounds
    ) {
        return new CoastalFeaturePlanV2.BlockPoint(
                Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L),
                Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L));
    }

    private static long vectorLength(long x, long z) {
        return BreakwaterFixedMathV2.integerSquareRoot(
                Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(z, z)));
    }

    private static BreakwaterGenerationException failure(String ruleId, String message) {
        return new BreakwaterGenerationException(ruleId, message);
    }
}
