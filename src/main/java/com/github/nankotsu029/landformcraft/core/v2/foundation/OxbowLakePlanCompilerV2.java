package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.river.RiverFixedMathV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.oxbow.OxbowLakeFixedMathV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OxbowLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compiles V2-10-11 OXBOW_LAKE reach-cutoff basin plans bound to a parent river reach. */
public final class OxbowLakePlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private static final long FIXED = TerrainIntentV2.FIXED_SCALE;
    private static final Set<TerrainIntentV2.FeatureKind> HOST_KINDS = EnumSet.of(
            TerrainIntentV2.FeatureKind.PLAIN,
            TerrainIntentV2.FeatureKind.FLOODPLAIN,
            TerrainIntentV2.FeatureKind.VALLEY,
            TerrainIntentV2.FeatureKind.MARSH);
    private static final Set<TerrainIntentV2.FeatureKind> PARENT_RIVER_KINDS = EnumSet.of(
            TerrainIntentV2.FeatureKind.RIVER,
            TerrainIntentV2.FeatureKind.MEANDERING_RIVER);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final RiverPlanCompilerV2 riverCompiler = new RiverPlanCompilerV2();
    private final MeanderingRiverPlanCompilerV2 meanderingRiverCompiler = new MeanderingRiverPlanCompilerV2();

    public OxbowLakePlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            String hostSurfaceGeometryChecksum,
            ParentRiverBinding parentBinding
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        Objects.requireNonNull(hostSurfaceGeometryChecksum, "hostSurfaceGeometryChecksum");
        Objects.requireNonNull(parentBinding, "parentBinding");

        if (feature.kind() != TerrainIntentV2.FeatureKind.OXBOW_LAKE) {
            throw failure("v2.oxbow-kind", "feature kind is not OXBOW_LAKE");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.OxbowLakeParameters parameters)) {
            throw failure("v2.oxbow-params", "oxbow lake parameters missing");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)
                || polygon.rings().size() != 1) {
            throw failure("v2.oxbow-geometry", "oxbow lake requires a single-ring closed POLYGON");
        }

        HostBinding host = resolveHostBinding(feature, intent);
        RiverRelationBinding riverRelation = resolveRiverBinding(feature, intent);

        ParentRiverBinding sealedBinding = sealParentBinding(
                riverRelation, intent, bounds, parentBinding);
        if (!sealedBinding.featureId().equals(parentBinding.featureId())
                || !sealedBinding.planChecksum().equals(parentBinding.planChecksum())
                || sealedBinding.kind() != parentBinding.kind()) {
            throw failure("v2.oxbow-orphan", "parent river plan binding does not match compiled river");
        }

        List<OxbowLakePlanV2.RingPoint> ring = toRing(polygon.rings().getFirst(), bounds);
        long surfaceY = Math.multiplyExact((long) bounds.waterLevel(), FIXED);
        int selectedDepth = midpoint(parameters.targetDepthBlocks());
        int shoreWidth = parameters.shoreWidthBlocks();
        int wetlandWidth = midpoint(parameters.wetlandHandoffWidthBlocks());
        int support = midpoint(parameters.supportRadiusBlocks());

        long centroidX = centroidX(ring);
        long centroidZ = centroidZ(ring);
        long minDistance = minCenterlineDistanceBlocks(sealedBinding, centroidX, centroidZ);
        if (minDistance > support) {
            throw failure("v2.oxbow-disconnected",
                    "oxbow basin centroid is farther than support radius from parent river centerline");
        }

        long ringCells = estimateInteriorCells(ring, bounds);
        long work = Math.multiplyExact(Math.multiplyExact(ringCells, (long) selectedDepth), (long) wetlandWidth);
        if (work > OxbowLakePlanV2.MAXIMUM_WORK_UNITS) {
            throw failure("v2.oxbow-budget", "oxbow lake work units exceed budget");
        }

        String cutoffReachId = resolveCutoffReachId(sealedBinding, parameters.cutoffReachIdHint());

        return new OxbowLakePlanV2(
                OxbowLakePlanV2.VERSION,
                feature.id(),
                sealedBinding.featureId(),
                sealedBinding.kind(),
                sealedBinding.planChecksum(),
                riverRelation.relationId(),
                cutoffReachId,
                host.hostSurfaceFeatureId(),
                hostSurfaceGeometryChecksum,
                host.hostRelationId(),
                ring,
                surfaceY,
                surfaceY,
                selectedDepth,
                shoreWidth,
                wetlandWidth,
                OxbowLakePlanV2.TERMINAL_POLICY,
                -1,
                0,
                0,
                OxbowLakePlanV2.BASIN_MASK_FIELD_ID,
                OxbowLakePlanV2.RIM_MASK_FIELD_ID,
                OxbowLakePlanV2.WETLAND_HANDOFF_FIELD_ID,
                OxbowLakePlanV2.OWNERSHIP_FIELD_ID,
                OxbowLakePlanV2.CUTOFF_FIELD_ID,
                OxbowLakePlanV2.LEVEL_FIELD_ID,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    private ParentRiverBinding sealParentBinding(
            RiverRelationBinding riverRelation,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            ParentRiverBinding expected
    ) {
        TerrainIntentV2.Feature riverFeature = requireFeature(intent, riverRelation.riverFeatureId());
        if (riverFeature.kind() == TerrainIntentV2.FeatureKind.RIVER) {
            RiverPlanV2 compiled = codec.sealRiverPlan(riverCompiler.compile(
                    riverFeature, intent, bounds, codec.geometryChecksum(riverFeature.geometry())));
            return new ParentRiverBinding(
                    compiled.featureId(),
                    OxbowLakePlanV2.ParentRiverKind.RIVER,
                    compiled.canonicalChecksum(),
                    compiled);
        }
        if (riverFeature.kind() == TerrainIntentV2.FeatureKind.MEANDERING_RIVER) {
            MeanderingRiverPlanV2 compiled = meanderingRiverCompiler.compile(
                    riverFeature, bounds, codec.geometryChecksum(riverFeature.geometry()));
            return new ParentRiverBinding(
                    compiled.featureId(),
                    OxbowLakePlanV2.ParentRiverKind.MEANDERING_RIVER,
                    compiled.geometryChecksum(),
                    compiled);
        }
        throw failure("v2.oxbow-missing-river", "ORIGINATES_AT target is not RIVER or MEANDERING_RIVER");
    }

    private static String resolveCutoffReachId(ParentRiverBinding binding, String hint) {
        if (hint != null && !hint.isBlank()) {
            if (binding.riverPlan() instanceof RiverPlanV2 river) {
                boolean matches = river.reaches().stream().anyMatch(reach -> reach.reachId().equals(hint));
                if (matches) {
                    return hint;
                }
            } else if (binding.riverPlan() instanceof MeanderingRiverPlanV2 meander
                    && meander.reachId().equals(hint)) {
                return hint;
            }
        }
        if (binding.riverPlan() instanceof RiverPlanV2 river) {
            return river.reaches().stream()
                    .filter(reach -> reach.reachClass() == RiverPlanV2.ReachClass.MAIN_STEM)
                    .map(RiverPlanV2.Reach::reachId)
                    .findFirst()
                    .or(() -> river.reaches().stream().map(RiverPlanV2.Reach::reachId).findFirst())
                    .orElseThrow(() -> failure("v2.oxbow-missing-river", "parent river lacks cutoff reach"));
        }
        MeanderingRiverPlanV2 meander = (MeanderingRiverPlanV2) binding.riverPlan();
        return meander.reachId();
    }

    private static long minCenterlineDistanceBlocks(
            ParentRiverBinding binding,
            long centroidX,
            long centroidZ
    ) {
        if (binding.riverPlan() instanceof RiverPlanV2 river) {
            long best = Long.MAX_VALUE;
            for (RiverPlanV2.CenterlineSample sample : river.centerline()) {
                long dx = centroidX - sample.xMillionths();
                long dz = centroidZ - sample.zMillionths();
                best = Math.min(best, RiverFixedMathV2.hypot(dx, dz) / FIXED);
            }
            return best;
        }
        MeanderingRiverPlanV2 meander = (MeanderingRiverPlanV2) binding.riverPlan();
        long best = Long.MAX_VALUE;
        for (MeanderingRiverPlanV2.CenterlineSample sample : meander.centerline()) {
            long dx = centroidX - sample.xMillionths();
            long dz = centroidZ - sample.zMillionths();
            best = Math.min(best, RiverFixedMathV2.hypot(dx, dz) / FIXED);
        }
        return best;
    }

    private static List<OxbowLakePlanV2.RingPoint> toRing(
            List<TerrainIntentV2.Point2> points,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<OxbowLakePlanV2.RingPoint> ring = new ArrayList<>(points.size());
        for (TerrainIntentV2.Point2 point : points) {
            ring.add(new OxbowLakePlanV2.RingPoint(
                    Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L),
                    Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L)));
        }
        if (ring.size() < 4 || !ring.getFirst().equals(ring.getLast())) {
            throw failure("v2.oxbow-geometry", "oxbow basin ring must be closed with at least four points");
        }
        return List.copyOf(ring);
    }

    private static long centroidX(List<OxbowLakePlanV2.RingPoint> ring) {
        long sum = 0L;
        int count = ring.size() - 1;
        for (int index = 0; index < count; index++) {
            sum = Math.addExact(sum, ring.get(index).xMillionths());
        }
        return sum / count;
    }

    private static long centroidZ(List<OxbowLakePlanV2.RingPoint> ring) {
        long sum = 0L;
        int count = ring.size() - 1;
        for (int index = 0; index < count; index++) {
            sum = Math.addExact(sum, ring.get(index).zMillionths());
        }
        return sum / count;
    }

    private static long estimateInteriorCells(
            List<OxbowLakePlanV2.RingPoint> ring,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<OxbowLakePlanV2.RingPoint> oxbowRing = ring;
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (OxbowLakePlanV2.RingPoint point : oxbowRing) {
            minX = Math.min(minX, point.xMillionths());
            maxX = Math.max(maxX, point.xMillionths());
            minZ = Math.min(minZ, point.zMillionths());
            maxZ = Math.max(maxZ, point.zMillionths());
        }
        int originX = Math.toIntExact(Math.max(0L, Math.floorDiv(minX, FIXED) - 1));
        int originZ = Math.toIntExact(Math.max(0L, Math.floorDiv(minZ, FIXED) - 1));
        int endX = Math.toIntExact(Math.min(bounds.width() - 1L, Math.floorDiv(maxX, FIXED) + 1));
        int endZ = Math.toIntExact(Math.min(bounds.length() - 1L, Math.floorDiv(maxZ, FIXED) + 1));
        long cells = 0L;
        for (int z = originZ; z <= endZ; z++) {
            for (int x = originX; x <= endX; x++) {
                long cx = Math.addExact(Math.multiplyExact((long) x, FIXED), FIXED / 2L);
                long cz = Math.addExact(Math.multiplyExact((long) z, FIXED), FIXED / 2L);
                if (OxbowLakeFixedMathV2.pointInRing(oxbowRing, cx, cz)) {
                    cells = Math.addExact(cells, 1L);
                }
            }
        }
        if (cells < 1L) {
            throw failure("v2.oxbow-geometry", "oxbow basin interior is empty");
        }
        return cells;
    }

    private static HostBinding resolveHostBinding(TerrainIntentV2.Feature oxbow, TerrainIntentV2 intent) {
        String endpoint = "feature:" + oxbow.id();
        List<TerrainIntentV2.Relation> hosts = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.SUPPORTED_BY)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isHost(intent, relation.to()))
                .toList();
        if (hosts.isEmpty()) {
            throw failure("v2.oxbow-missing-host", "oxbow requires exactly one HARD SUPPORTED_BY host surface");
        }
        if (hosts.size() > 1) {
            throw failure("v2.oxbow-missing-host", "oxbow has multiple HARD SUPPORTED_BY relations");
        }
        TerrainIntentV2.Relation host = hosts.getFirst();
        return new HostBinding(host.id(), host.to().substring("feature:".length()));
    }

    private static RiverRelationBinding resolveRiverBinding(TerrainIntentV2.Feature oxbow, TerrainIntentV2 intent) {
        String endpoint = "feature:" + oxbow.id();
        List<TerrainIntentV2.Relation> rivers = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ORIGINATES_AT)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> relation.to().startsWith("feature:"))
                .filter(relation -> isParentRiver(intent, relation.to()))
                .toList();
        if (rivers.isEmpty()) {
            throw failure("v2.oxbow-missing-river", "oxbow requires exactly one HARD ORIGINATES_AT parent river");
        }
        if (rivers.size() > 1) {
            throw failure("v2.oxbow-missing-river", "oxbow has multiple HARD ORIGINATES_AT parent river relations");
        }
        TerrainIntentV2.Relation river = rivers.getFirst();
        return new RiverRelationBinding(river.id(), river.to().substring("feature:".length()));
    }

    private static boolean isHost(TerrainIntentV2 intent, String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return false;
        }
        return HOST_KINDS.contains(featureKind(intent, endpoint.substring("feature:".length())));
    }

    private static boolean isParentRiver(TerrainIntentV2 intent, String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return false;
        }
        return PARENT_RIVER_KINDS.contains(featureKind(intent, endpoint.substring("feature:".length())));
    }

    private static TerrainIntentV2.FeatureKind featureKind(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(item -> item.id().equals(featureId))
                .map(TerrainIntentV2.Feature::kind)
                .findFirst()
                .orElseThrow(() -> failure("v2.oxbow-orphan", "referenced feature missing: " + featureId));
    }

    private static TerrainIntentV2.Feature requireFeature(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(item -> item.id().equals(featureId))
                .findFirst()
                .orElseThrow(() -> failure("v2.oxbow-orphan", "referenced feature missing: " + featureId));
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }

    public record HostBinding(String hostRelationId, String hostSurfaceFeatureId) {
    }

    public record RiverRelationBinding(String relationId, String riverFeatureId) {
    }

    public record ParentRiverBinding(
            String featureId,
            OxbowLakePlanV2.ParentRiverKind kind,
            String planChecksum,
            Object riverPlan
    ) {
    }
}
