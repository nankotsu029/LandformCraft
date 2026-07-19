package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.river.RiverFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SpringPlanV2;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compiles V2-10-10 surface SPRING plans bound to a general RIVER source node and surface host. */
public final class SpringPlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private static final long FIXED = TerrainIntentV2.FIXED_SCALE;
    private static final Set<TerrainIntentV2.FeatureKind> SURFACE_HOST_KINDS = EnumSet.of(
            TerrainIntentV2.FeatureKind.PLAIN,
            TerrainIntentV2.FeatureKind.VALLEY,
            TerrainIntentV2.FeatureKind.HILL_RANGE,
            TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE,
            TerrainIntentV2.FeatureKind.FLOODPLAIN);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final RiverPlanCompilerV2 riverCompiler = new RiverPlanCompilerV2();

    public SpringPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            String outletSurfaceGeometryChecksum,
            RiverPlanV2 riverPlan
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        Objects.requireNonNull(outletSurfaceGeometryChecksum, "outletSurfaceGeometryChecksum");
        Objects.requireNonNull(riverPlan, "riverPlan");

        if (feature.kind() == TerrainIntentV2.FeatureKind.KARST_SPRING) {
            throw failure("v2.spring-kind", "karst spring must use KarstSpringPlanCompilerV2");
        }
        if (feature.kind() != TerrainIntentV2.FeatureKind.SPRING) {
            throw failure("v2.spring-kind", "feature kind is not SPRING");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.SpringParameters parameters)) {
            throw failure("v2.spring-params", "spring parameters missing");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PointGeometry pointGeometry)) {
            throw failure("v2.spring-geometry", "spring requires POINT geometry");
        }

        HostBinding host = resolveHostBinding(feature, intent);
        RiverBinding river = resolveRiverBinding(feature, intent);

        TerrainIntentV2.Feature riverFeature = requireFeature(intent, river.riverFeatureId());
        if (riverFeature.kind() != TerrainIntentV2.FeatureKind.RIVER) {
            throw failure("v2.spring-missing-river", "EMPTIES_INTO target is not RIVER");
        }
        RiverPlanV2 compiledRiver = codec.sealRiverPlan(riverCompiler.compile(
                riverFeature,
                intent,
                bounds,
                codec.geometryChecksum(riverFeature.geometry())));
        if (!compiledRiver.featureId().equals(riverPlan.featureId())
                || !compiledRiver.canonicalChecksum().equals(riverPlan.canonicalChecksum())) {
            throw failure("v2.spring-orphan", "river plan binding does not match compiled river");
        }

        RiverPlanV2.Node sourceNode = riverPlan.nodes().stream()
                .filter(node -> node.kind() == RiverPlanV2.NodeKind.SOURCE
                        && node.role() == RiverPlanV2.NodeRole.HEADWATER)
                .findFirst()
                .orElseThrow(() -> failure("v2.spring-missing-river",
                        "river plan lacks SOURCE+HEADWATER node"));
        RiverPlanV2.Reach downstreamReach = riverPlan.reaches().stream()
                .filter(reach -> reach.fromNodeId().equals(sourceNode.nodeId()))
                .findFirst()
                .orElseThrow(() -> failure("v2.spring-missing-river",
                        "river plan lacks downstream reach from source"));

        long openingX = Math.multiplyExact((long) pointGeometry.point().xMillionths(), bounds.width() - 1L);
        long openingZ = Math.multiplyExact((long) pointGeometry.point().zMillionths(), bounds.length() - 1L);
        long openingY = riverPlan.sourceBedYMillionths();

        int support = midpoint(parameters.supportRadiusBlocks());
        int outflowRadius = midpoint(parameters.outflowRadiusBlocks());
        int discharge = midpoint(parameters.dischargeBlocks());

        long dx = openingX - sourceNode.xMillionths();
        long dz = openingZ - sourceNode.zMillionths();
        long distanceBlocks = RiverFixedMathV2.hypot(dx, dz) / FIXED;
        if (distanceBlocks > support) {
            throw failure("v2.spring-disconnected",
                    "spring opening is farther than support radius from river source");
        }

        long work = Math.multiplyExact((long) discharge, (long) outflowRadius);
        if (work > SpringPlanV2.MAXIMUM_WORK_UNITS) {
            throw failure("v2.spring-budget", "spring work units exceed budget");
        }

        return new SpringPlanV2(
                SpringPlanV2.VERSION,
                feature.id(),
                host.outletSurfaceFeatureId(),
                outletSurfaceGeometryChecksum,
                host.hostRelationId(),
                riverPlan.featureId(),
                riverPlan.canonicalChecksum(),
                sourceNode.nodeId(),
                downstreamReach.reachId(),
                river.riverRelationId(),
                "hydro.spring." + feature.id(),
                SpringPlanV2.HYDROLOGY_NODE_KIND,
                openingX,
                openingY,
                openingZ,
                riverPlan.sourceBedYMillionths(),
                discharge,
                riverPlan.dischargeClass(),
                outflowRadius,
                SpringPlanV2.SOURCE_MASK_FIELD_ID,
                SpringPlanV2.OUTFLOW_MASK_FIELD_ID,
                SpringPlanV2.OWNERSHIP_FIELD_ID,
                SpringPlanV2.CONTINUITY_FIELD_ID,
                SpringPlanV2.REACHABILITY_FIELD_ID,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    private static HostBinding resolveHostBinding(TerrainIntentV2.Feature spring, TerrainIntentV2 intent) {
        String springEndpoint = "feature:" + spring.id();
        List<TerrainIntentV2.Relation> hosts = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.SUPPORTED_BY)
                .filter(relation -> springEndpoint.equals(relation.from()))
                .filter(relation -> isSurfaceHost(intent, relation.to()))
                .toList();
        if (hosts.isEmpty()) {
            throw failure("v2.spring-missing-host", "spring requires exactly one HARD SUPPORTED_BY surface host");
        }
        if (hosts.size() > 1) {
            throw failure("v2.spring-missing-host", "spring has multiple HARD SUPPORTED_BY relations");
        }
        TerrainIntentV2.Relation host = hosts.getFirst();
        return new HostBinding(host.id(), host.to().substring("feature:".length()));
    }

    private static RiverBinding resolveRiverBinding(TerrainIntentV2.Feature spring, TerrainIntentV2 intent) {
        String springEndpoint = "feature:" + spring.id();
        List<TerrainIntentV2.Relation> rivers = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO)
                .filter(relation -> springEndpoint.equals(relation.from()))
                .filter(relation -> relation.to().startsWith("feature:"))
                .filter(relation -> isRiver(intent, relation.to()))
                .toList();
        if (rivers.isEmpty()) {
            throw failure("v2.spring-missing-river", "spring requires exactly one HARD EMPTIES_INTO river");
        }
        if (rivers.size() > 1) {
            throw failure("v2.spring-missing-river", "spring has multiple HARD EMPTIES_INTO river relations");
        }
        TerrainIntentV2.Relation river = rivers.getFirst();
        return new RiverBinding(river.id(), river.to().substring("feature:".length()));
    }

    private static boolean isSurfaceHost(TerrainIntentV2 intent, String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return false;
        }
        return SURFACE_HOST_KINDS.contains(featureKind(intent, endpoint.substring("feature:".length())));
    }

    private static boolean isRiver(TerrainIntentV2 intent, String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return false;
        }
        return featureKind(intent, endpoint.substring("feature:".length()))
                == TerrainIntentV2.FeatureKind.RIVER;
    }

    private static TerrainIntentV2.FeatureKind featureKind(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(item -> item.id().equals(featureId))
                .map(TerrainIntentV2.Feature::kind)
                .findFirst()
                .orElseThrow(() -> failure("v2.spring-orphan", "referenced feature missing: " + featureId));
    }

    private static TerrainIntentV2.Feature requireFeature(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(item -> item.id().equals(featureId))
                .findFirst()
                .orElseThrow(() -> failure("v2.spring-orphan", "referenced feature missing: " + featureId));
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }

    public record HostBinding(String hostRelationId, String outletSurfaceFeatureId) {
    }

    public record RiverBinding(String riverRelationId, String riverFeatureId) {
    }
}
