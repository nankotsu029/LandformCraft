package com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall;

import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compiles one WATERFALL Intent feature against a HARD ON_PATH_OF MEANDERING_RIVER binding.
 * Produces lip/base reach split and plunge-pool geometry without volume fluid.
 */
public final class WaterfallPlanCompilerV2 {
    public WaterfallPlanV2 compile(
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
        if (feature.kind() != TerrainIntentV2.FeatureKind.WATERFALL) {
            throw failure("v2.waterfall-kind", "feature kind is not WATERFALL");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PointGeometry pointGeometry)) {
            throw failure("v2.waterfall-geometry", "waterfall requires POINT geometry");
        }
        TerrainIntentV2.WaterfallParameters parameters = (TerrainIntentV2.WaterfallParameters) feature.parameters();
        try {
            if (parameters.behindFallClearanceBlocks() > 0) {
                throw failure(
                        "v2.waterfall-zero-roof-clearance",
                        "behind-fall roof clearance requires V2-5 volume capability; 2.5D waterfall forbids non-zero clearance");
            }
            Binding binding = resolveOnPathRiver(feature, intent, riverPlans);
            MeanderingRiverPlanV2 river = binding.river();
            int selectedDrop = mid(parameters.dropBlocks());
            long dropMillionths = Math.multiplyExact((long) selectedDrop, WaterfallFixedMathV2.FIXED_SCALE);
            long pointX = Math.multiplyExact((long) pointGeometry.point().xMillionths(), bounds.width() - 1L);
            long pointZ = Math.multiplyExact((long) pointGeometry.point().zMillionths(), bounds.length() - 1L);
            Projection lip = projectOntoCenterline(river.centerline(), pointX, pointZ);
            long pathTolerance = Math.multiplyExact(
                    (long) (Math.max(parameters.lipWidthBlocks(), river.selectedBankfullWidthBlocks()) + 1) / 2,
                    WaterfallFixedMathV2.FIXED_SCALE);
            if (lip.distanceMillionths() > pathTolerance) {
                throw failure("v2.waterfall-off-path", "waterfall POINT is off the HARD ON_PATH_OF river corridor");
            }
            long minArc = WaterfallFixedMathV2.FIXED_SCALE;
            long maxArc = Math.subtractExact(river.totalArcLengthMillionths(), WaterfallFixedMathV2.FIXED_SCALE);
            if (lip.arcLengthMillionths() < minArc || lip.arcLengthMillionths() > maxArc) {
                throw failure("v2.waterfall-off-path", "waterfall lip must leave room for upstream and downstream reaches");
            }
            long lipBed = interpolateBed(river.centerline(), lip.arcLengthMillionths());
            long baseBed = Math.subtractExact(lipBed, dropMillionths);
            if (baseBed >= lipBed) {
                throw failure("v2.waterfall-uphill-base", "waterfall base elevation is not below the lip");
            }
            if (baseBed < Math.multiplyExact((long) bounds.minY(), WaterfallFixedMathV2.FIXED_SCALE)) {
                throw failure("v2.waterfall-vertical-bounds", "waterfall base drops below minY");
            }
            long baseOffset = Math.multiplyExact(
                    (long) Math.max(1, parameters.plungePoolRadiusBlocks() / 4),
                    WaterfallFixedMathV2.FIXED_SCALE);
            long baseArc = Math.min(
                    Math.addExact(lip.arcLengthMillionths(), baseOffset),
                    Math.subtractExact(river.totalArcLengthMillionths(), WaterfallFixedMathV2.FIXED_SCALE / 2L));
            if (baseArc <= lip.arcLengthMillionths()) {
                throw failure("v2.waterfall-off-path", "waterfall cannot place a downstream base on the river path");
            }
            SamplePoint basePoint = sampleAtArc(river.centerline(), baseArc);
            ReachSplit split = splitReaches(river.centerline(), lip, basePoint, lipBed, baseBed, dropMillionths);
            int plungeDepth = Math.max(2, Math.min(8, selectedDrop / 4));
            if (Math.subtractExact(baseBed, Math.multiplyExact((long) plungeDepth, WaterfallFixedMathV2.FIXED_SCALE))
                    < Math.multiplyExact((long) bounds.minY(), WaterfallFixedMathV2.FIXED_SCALE)) {
                throw failure("v2.waterfall-vertical-bounds", "plunge pool floor exceeds vertical bounds");
            }
            int support = Math.min(HydrologyWaterfallModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                    Math.max(parameters.lipWidthBlocks(),
                            Math.max(parameters.plungePoolRadiusBlocks(), river.supportRadiusXZ())));
            return new WaterfallPlanV2(
                    WaterfallPlanV2.VERSION,
                    feature.id(),
                    river.featureId(),
                    binding.relationId(),
                    lip.xMillionths(),
                    lip.zMillionths(),
                    basePoint.xMillionths(),
                    basePoint.zMillionths(),
                    lip.arcLengthMillionths(),
                    baseArc,
                    lipBed,
                    baseBed,
                    parameters.dropBlocks().minimum(),
                    parameters.dropBlocks().maximum(),
                    selectedDrop,
                    parameters.lipWidthBlocks(),
                    parameters.plungePoolRadiusBlocks(),
                    plungeDepth,
                    0,
                    "reach-up-" + feature.id(),
                    "reach-down-" + feature.id(),
                    "lip-" + feature.id(),
                    "base-" + feature.id(),
                    split.upstream(),
                    split.downstream(),
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    HydrologyWaterfallModuleV2.LIP_MASK_FIELD_ID,
                    HydrologyWaterfallModuleV2.BASE_MASK_FIELD_ID,
                    HydrologyWaterfallModuleV2.PLUNGE_POOL_MASK_FIELD_ID,
                    HydrologyWaterfallModuleV2.LIP_ELEVATION_FIELD_ID,
                    HydrologyWaterfallModuleV2.BASE_ELEVATION_FIELD_ID,
                    HydrologyWaterfallModuleV2.PLUNGE_POOL_FLOOR_FIELD_ID,
                    HydrologyIrModuleV2.BED_ELEVATION_FIELD,
                    support,
                    geometryChecksum,
                    river.geometryChecksum());
        } catch (ArithmeticException exception) {
            throw new WaterfallGenerationException(
                    "v2.waterfall-overflow", "waterfall plan arithmetic overflow", exception);
        }
    }

    private static Binding resolveOnPathRiver(
            TerrainIntentV2.Feature waterfall,
            TerrainIntentV2 intent,
            List<MeanderingRiverPlanV2> riverPlans
    ) {
        String waterfallEndpoint = "feature:" + waterfall.id();
        List<TerrainIntentV2.Relation> matches = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD
                        && relation.kind() == TerrainIntentV2.RelationKind.ON_PATH_OF
                        && relation.from().equals(waterfallEndpoint)
                        && relation.to().startsWith("feature:"))
                .toList();
        if (matches.isEmpty()) {
            throw failure("v2.waterfall-on-path-missing", "waterfall requires one HARD ON_PATH_OF MEANDERING_RIVER");
        }
        if (matches.size() > 1) {
            throw failure("v2.waterfall-on-path-ambiguous", "waterfall has multiple HARD ON_PATH_OF river bindings");
        }
        TerrainIntentV2.Relation relation = matches.getFirst();
        String riverId = relation.to().substring("feature:".length());
        TerrainIntentV2.Feature riverFeature = intent.features().stream()
                .filter(candidate -> candidate.id().equals(riverId))
                .findFirst()
                .orElseThrow(() -> failure("v2.waterfall-on-path-missing", "ON_PATH_OF river feature is missing"));
        if (riverFeature.kind() != TerrainIntentV2.FeatureKind.MEANDERING_RIVER) {
            throw failure("v2.waterfall-on-path-kind", "waterfall ON_PATH_OF must bind a MEANDERING_RIVER feature");
        }
        MeanderingRiverPlanV2 river = riverPlans.stream()
                .filter(plan -> plan.featureId().equals(riverId))
                .findFirst()
                .orElseThrow(() -> failure("v2.waterfall-on-path-missing", "ON_PATH_OF river plan is not compiled"));
        return new Binding(relation.id(), river);
    }

    private static Projection projectOntoCenterline(
            List<MeanderingRiverPlanV2.CenterlineSample> samples,
            long cellX,
            long cellZ
    ) {
        long bestDistance = Long.MAX_VALUE;
        long bestArc = 0L;
        long bestX = samples.getFirst().xMillionths();
        long bestZ = samples.getFirst().zMillionths();
        for (int index = 0; index < samples.size() - 1; index++) {
            MeanderingRiverPlanV2.CenterlineSample a = samples.get(index);
            MeanderingRiverPlanV2.CenterlineSample b = samples.get(index + 1);
            long dx = Math.subtractExact(b.xMillionths(), a.xMillionths());
            long dz = Math.subtractExact(b.zMillionths(), a.zMillionths());
            long lengthSquared = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
            long projection;
            if (lengthSquared == 0L) {
                projection = 0L;
            } else {
                long relX = Math.subtractExact(cellX, a.xMillionths());
                long relZ = Math.subtractExact(cellZ, a.zMillionths());
                long dot = Math.addExact(Math.multiplyExact(relX, dx), Math.multiplyExact(relZ, dz));
                projection = WaterfallFixedMathV2.clampLong(
                        WaterfallFixedMathV2.mulDivExact(dot, WaterfallFixedMathV2.FIXED_SCALE, lengthSquared),
                        0L,
                        WaterfallFixedMathV2.FIXED_SCALE);
            }
            long qx = Math.addExact(a.xMillionths(),
                    WaterfallFixedMathV2.mulDivExact(dx, projection, WaterfallFixedMathV2.FIXED_SCALE));
            long qz = Math.addExact(a.zMillionths(),
                    WaterfallFixedMathV2.mulDivExact(dz, projection, WaterfallFixedMathV2.FIXED_SCALE));
            long distance = WaterfallFixedMathV2.hypotMillionths(
                    Math.subtractExact(cellX, qx), Math.subtractExact(cellZ, qz));
            long segmentArc = Math.subtractExact(b.arcLengthMillionths(), a.arcLengthMillionths());
            long arc = Math.addExact(a.arcLengthMillionths(),
                    WaterfallFixedMathV2.mulDivExact(segmentArc, projection, WaterfallFixedMathV2.FIXED_SCALE));
            if (distance < bestDistance
                    || (distance == bestDistance && arc < bestArc)) {
                bestDistance = distance;
                bestArc = arc;
                bestX = qx;
                bestZ = qz;
            }
        }
        return new Projection(bestX, bestZ, bestArc, bestDistance);
    }

    private static long interpolateBed(List<MeanderingRiverPlanV2.CenterlineSample> samples, long arc) {
        for (int index = 0; index < samples.size() - 1; index++) {
            MeanderingRiverPlanV2.CenterlineSample a = samples.get(index);
            MeanderingRiverPlanV2.CenterlineSample b = samples.get(index + 1);
            if (arc < a.arcLengthMillionths() || arc > b.arcLengthMillionths()) continue;
            long span = Math.subtractExact(b.arcLengthMillionths(), a.arcLengthMillionths());
            if (span == 0L) return a.bedYMillionths();
            long t = WaterfallFixedMathV2.mulDivExact(
                    Math.subtractExact(arc, a.arcLengthMillionths()),
                    WaterfallFixedMathV2.FIXED_SCALE,
                    span);
            long delta = Math.subtractExact(b.bedYMillionths(), a.bedYMillionths());
            return Math.addExact(a.bedYMillionths(),
                    WaterfallFixedMathV2.mulDivExact(delta, t, WaterfallFixedMathV2.FIXED_SCALE));
        }
        return samples.getLast().bedYMillionths();
    }

    private static SamplePoint sampleAtArc(List<MeanderingRiverPlanV2.CenterlineSample> samples, long arc) {
        for (int index = 0; index < samples.size() - 1; index++) {
            MeanderingRiverPlanV2.CenterlineSample a = samples.get(index);
            MeanderingRiverPlanV2.CenterlineSample b = samples.get(index + 1);
            if (arc < a.arcLengthMillionths() || arc > b.arcLengthMillionths()) continue;
            long span = Math.subtractExact(b.arcLengthMillionths(), a.arcLengthMillionths());
            long t = span == 0L ? 0L : WaterfallFixedMathV2.mulDivExact(
                    Math.subtractExact(arc, a.arcLengthMillionths()),
                    WaterfallFixedMathV2.FIXED_SCALE,
                    span);
            long x = Math.addExact(a.xMillionths(), WaterfallFixedMathV2.mulDivExact(
                    Math.subtractExact(b.xMillionths(), a.xMillionths()), t, WaterfallFixedMathV2.FIXED_SCALE));
            long z = Math.addExact(a.zMillionths(), WaterfallFixedMathV2.mulDivExact(
                    Math.subtractExact(b.zMillionths(), a.zMillionths()), t, WaterfallFixedMathV2.FIXED_SCALE));
            int halfWidth = a.localHalfWidthBlocks();
            return new SamplePoint(arc, x, z, halfWidth);
        }
        MeanderingRiverPlanV2.CenterlineSample last = samples.getLast();
        return new SamplePoint(last.arcLengthMillionths(), last.xMillionths(), last.zMillionths(),
                last.localHalfWidthBlocks());
    }

    private static ReachSplit splitReaches(
            List<MeanderingRiverPlanV2.CenterlineSample> samples,
            Projection lip,
            SamplePoint base,
            long lipBed,
            long baseBed,
            long dropMillionths
    ) {
        List<MeanderingRiverPlanV2.CenterlineSample> upstream = new ArrayList<>();
        List<MeanderingRiverPlanV2.CenterlineSample> downstream = new ArrayList<>();
        int upstreamSequence = 0;
        for (MeanderingRiverPlanV2.CenterlineSample sample : samples) {
            if (sample.arcLengthMillionths() < lip.arcLengthMillionths()) {
                upstream.add(new MeanderingRiverPlanV2.CenterlineSample(
                        upstreamSequence++,
                        sample.arcLengthMillionths(),
                        sample.xMillionths(),
                        sample.zMillionths(),
                        sample.bedYMillionths(),
                        sample.localHalfWidthBlocks()));
            }
        }
        if (upstream.isEmpty()) {
            MeanderingRiverPlanV2.CenterlineSample first = samples.getFirst();
            upstream.add(new MeanderingRiverPlanV2.CenterlineSample(
                    0, first.arcLengthMillionths(), first.xMillionths(), first.zMillionths(),
                    first.bedYMillionths(), first.localHalfWidthBlocks()));
            upstreamSequence = 1;
        }
        upstream.add(new MeanderingRiverPlanV2.CenterlineSample(
                upstreamSequence,
                lip.arcLengthMillionths(),
                lip.xMillionths(),
                lip.zMillionths(),
                lipBed,
                Math.max(1, samples.getFirst().localHalfWidthBlocks())));

        int downstreamSequence = 0;
        downstream.add(new MeanderingRiverPlanV2.CenterlineSample(
                downstreamSequence++,
                base.arcLengthMillionths(),
                base.xMillionths(),
                base.zMillionths(),
                baseBed,
                Math.max(1, base.halfWidthBlocks())));
        for (MeanderingRiverPlanV2.CenterlineSample sample : samples) {
            if (sample.arcLengthMillionths() <= base.arcLengthMillionths()) continue;
            long adjustedBed = Math.subtractExact(sample.bedYMillionths(), dropMillionths);
            if (adjustedBed > downstream.getLast().bedYMillionths()) {
                adjustedBed = downstream.getLast().bedYMillionths();
            }
            downstream.add(new MeanderingRiverPlanV2.CenterlineSample(
                    downstreamSequence++,
                    sample.arcLengthMillionths(),
                    sample.xMillionths(),
                    sample.zMillionths(),
                    adjustedBed,
                    sample.localHalfWidthBlocks()));
        }
        if (downstream.size() < 2) {
            MeanderingRiverPlanV2.CenterlineSample last = samples.getLast();
            long adjustedBed = Math.subtractExact(last.bedYMillionths(), dropMillionths);
            if (adjustedBed > baseBed) adjustedBed = baseBed;
            downstream.add(new MeanderingRiverPlanV2.CenterlineSample(
                    downstreamSequence,
                    Math.addExact(base.arcLengthMillionths(), WaterfallFixedMathV2.FIXED_SCALE),
                    last.xMillionths(),
                    last.zMillionths(),
                    adjustedBed,
                    last.localHalfWidthBlocks()));
        }
        return new ReachSplit(List.copyOf(upstream), List.copyOf(downstream));
    }

    private static int mid(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static WaterfallGenerationException failure(String ruleId, String message) {
        return new WaterfallGenerationException(ruleId, message);
    }

    private record Binding(String relationId, MeanderingRiverPlanV2 river) {
    }

    private record Projection(long xMillionths, long zMillionths, long arcLengthMillionths, long distanceMillionths) {
    }

    private record SamplePoint(long arcLengthMillionths, long xMillionths, long zMillionths, int halfWidthBlocks) {
    }

    private record ReachSplit(
            List<MeanderingRiverPlanV2.CenterlineSample> upstream,
            List<MeanderingRiverPlanV2.CenterlineSample> downstream
    ) {
    }
}
