package com.github.nankotsu029.landformcraft.generator.v2.landform.canyon;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compiles one CANYON Intent feature against a HARD WITHIN MEANDERING_RIVER binding.
 * Uses the river-owned frozen centerline; canyon does not invent a second bed graph.
 */
public final class CanyonPlanCompilerV2 {
    public CanyonPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            List<MeanderingRiverPlanV2> riverPlans,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(riverPlans, "riverPlans");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.CANYON) {
            throw failure("v2.canyon-kind", "feature kind is not CANYON");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)
                || spline.points().size() < 2) {
            throw failure("v2.canyon-geometry", "canyon requires SPLINE geometry with at least two points");
        }
        TerrainIntentV2.CanyonParameters parameters = (TerrainIntentV2.CanyonParameters) feature.parameters();
        try {
            Binding binding = resolveWithinRiver(feature, intent, riverPlans);
            MeanderingRiverPlanV2 river = binding.river();
            int selectedFloor = mid(parameters.floorWidthBlocks());
            int selectedRim = mid(parameters.rimWidthBlocks());
            int selectedDepth = mid(parameters.depthBlocks());
            if (selectedRim < selectedFloor + 2) {
                throw failure("v2.canyon-thin-wall", "canyon wall thickness is below two blocks");
            }
            if (river.selectedBankfullWidthBlocks() > selectedFloor) {
                throw failure("v2.canyon-river-containment",
                        "river bankfull width exceeds canyon floor width");
            }
            long maxDepth = Math.multiplyExact((long) selectedDepth, CanyonFixedMathV2.FIXED_SCALE);
            long lowestBed = Math.min(river.sourceBedYMillionths(), river.mouthBedYMillionths());
            if (Math.subtractExact(lowestBed, maxDepth)
                    < Math.multiplyExact((long) bounds.minY(), CanyonFixedMathV2.FIXED_SCALE)) {
                throw failure("v2.canyon-vertical-bounds", "canyon depth exceeds vertical bounds above minY");
            }
            requireCenterlineAligned(spline, river, bounds, selectedRim);
            requireMonotonicBed(river);
            int support = Math.min(LandformCanyonModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                    Math.max((selectedRim + 1) / 2, river.supportRadiusXZ()));
            return new CanyonPlanV2(
                    CanyonPlanV2.VERSION,
                    feature.id(),
                    river.featureId(),
                    binding.relationId(),
                    parameters.crossSection(),
                    river.centerline(),
                    river.totalArcLengthMillionths(),
                    river.sourceBedYMillionths(),
                    river.mouthBedYMillionths(),
                    parameters.floorWidthBlocks().minimum(),
                    parameters.floorWidthBlocks().maximum(),
                    selectedFloor,
                    parameters.rimWidthBlocks().minimum(),
                    parameters.rimWidthBlocks().maximum(),
                    selectedRim,
                    parameters.depthBlocks().minimum(),
                    parameters.depthBlocks().maximum(),
                    selectedDepth,
                    parameters.terraceCount(),
                    parameters.terraceWidthBlocks(),
                    river.selectedBankfullWidthBlocks(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    LandformCanyonModuleV2.CANYON_MASK_FIELD_ID,
                    LandformCanyonModuleV2.FLOOR_MASK_FIELD_ID,
                    LandformCanyonModuleV2.RIM_MASK_FIELD_ID,
                    LandformCanyonModuleV2.TERRACE_MASK_FIELD_ID,
                    LandformCanyonModuleV2.SURFACE_HEIGHT_FIELD_ID,
                    LandformCanyonModuleV2.WALL_HEIGHT_FIELD_ID,
                    HydrologyIrModuleV2.BED_ELEVATION_FIELD,
                    support,
                    geometryChecksum,
                    river.geometryChecksum());
        } catch (ArithmeticException exception) {
            throw new CanyonGenerationException("v2.canyon-overflow", "canyon plan arithmetic overflow", exception);
        }
    }

    private static Binding resolveWithinRiver(
            TerrainIntentV2.Feature canyon,
            TerrainIntentV2 intent,
            List<MeanderingRiverPlanV2> riverPlans
    ) {
        String canyonEndpoint = "feature:" + canyon.id();
        List<TerrainIntentV2.Relation> matches = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD
                        && relation.kind() == TerrainIntentV2.RelationKind.WITHIN
                        && relation.to().equals(canyonEndpoint)
                        && relation.from().startsWith("feature:"))
                .toList();
        if (matches.isEmpty()) {
            throw failure("v2.canyon-within-missing", "canyon requires one HARD WITHIN MEANDERING_RIVER");
        }
        if (matches.size() > 1) {
            throw failure("v2.canyon-crossing", "canyon has multiple HARD WITHIN river bindings");
        }
        TerrainIntentV2.Relation relation = matches.getFirst();
        String riverId = relation.from().substring("feature:".length());
        TerrainIntentV2.Feature riverFeature = intent.features().stream()
                .filter(candidate -> candidate.id().equals(riverId))
                .findFirst()
                .orElseThrow(() -> failure("v2.canyon-within-missing", "WITHIN river feature is missing"));
        if (riverFeature.kind() != TerrainIntentV2.FeatureKind.MEANDERING_RIVER) {
            throw failure("v2.canyon-within-kind", "canyon WITHIN must bind a MEANDERING_RIVER feature");
        }
        MeanderingRiverPlanV2 river = riverPlans.stream()
                .filter(plan -> plan.featureId().equals(riverId))
                .findFirst()
                .orElseThrow(() -> failure("v2.canyon-within-missing", "WITHIN river plan is not compiled"));
        long sharedRivers = riverPlans.stream()
                .filter(plan -> plan.featureId().equals(riverId))
                .count();
        if (sharedRivers != 1L) {
            throw failure("v2.canyon-crossing", "canyon river binding is ambiguous");
        }
        return new Binding(relation.id(), river);
    }

    private static void requireCenterlineAligned(
            TerrainIntentV2.SplineGeometry canyonSpline,
            MeanderingRiverPlanV2 river,
            WorldBlueprintV2.Bounds bounds,
            int selectedRim
    ) {
        long rimLimit = Math.multiplyExact((long) (selectedRim + 1) / 2, CanyonFixedMathV2.FIXED_SCALE);
        for (TerrainIntentV2.Point2 point : canyonSpline.points()) {
            long x = Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L);
            long z = Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L);
            long distance = nearestDistance(river.centerline(), x, z);
            if (distance > rimLimit) {
                throw failure("v2.canyon-disconnected-centerline",
                        "canyon spline leaves the shared river corridor");
            }
        }
        for (MeanderingRiverPlanV2.CenterlineSample sample : river.centerline()) {
            // River samples define the axis; they are inside by construction. Keep a cheap
            // monotonicity/presence guard so empty or corrupted plans are rejected.
            if (sample.localHalfWidthBlocks() < 1) {
                throw failure("v2.canyon-disconnected-centerline", "shared river centerline is incomplete");
            }
        }
    }

    private static void requireMonotonicBed(MeanderingRiverPlanV2 river) {
        List<MeanderingRiverPlanV2.CenterlineSample> samples = river.centerline();
        for (int index = 1; index < samples.size(); index++) {
            if (samples.get(index).bedYMillionths() > samples.get(index - 1).bedYMillionths()) {
                throw failure("v2.canyon-bed-monotonicity", "shared river bed rises downstream");
            }
        }
    }

    private static long nearestDistance(
            List<MeanderingRiverPlanV2.CenterlineSample> samples,
            long cellX,
            long cellZ
    ) {
        long best = Long.MAX_VALUE;
        for (int index = 0; index < samples.size() - 1; index++) {
            MeanderingRiverPlanV2.CenterlineSample a = samples.get(index);
            MeanderingRiverPlanV2.CenterlineSample b = samples.get(index + 1);
            long distance = distanceToSegment(
                    cellX, cellZ,
                    a.xMillionths(), a.zMillionths(),
                    b.xMillionths(), b.zMillionths());
            if (distance < best) best = distance;
        }
        return best;
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = Math.subtractExact(bx, ax);
        long dz = Math.subtractExact(bz, az);
        long lengthSquared = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long projection;
        if (lengthSquared == 0L) {
            projection = 0L;
        } else {
            long relX = Math.subtractExact(px, ax);
            long relZ = Math.subtractExact(pz, az);
            long dot = Math.addExact(Math.multiplyExact(relX, dx), Math.multiplyExact(relZ, dz));
            projection = CanyonFixedMathV2.clampLong(
                    CanyonFixedMathV2.mulDivExact(dot, CanyonFixedMathV2.FIXED_SCALE, lengthSquared),
                    0L,
                    CanyonFixedMathV2.FIXED_SCALE);
        }
        long qx = Math.addExact(ax, CanyonFixedMathV2.mulDivExact(dx, projection, CanyonFixedMathV2.FIXED_SCALE));
        long qz = Math.addExact(az, CanyonFixedMathV2.mulDivExact(dz, projection, CanyonFixedMathV2.FIXED_SCALE));
        return CanyonFixedMathV2.hypotMillionths(Math.subtractExact(px, qx), Math.subtractExact(pz, qz));
    }

    private static int mid(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static CanyonGenerationException failure(String ruleId, String message) {
        return new CanyonGenerationException(ruleId, message);
    }

    private record Binding(String relationId, MeanderingRiverPlanV2 river) {
    }
}
