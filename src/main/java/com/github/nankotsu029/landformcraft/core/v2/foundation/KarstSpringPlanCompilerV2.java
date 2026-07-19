package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstSpringPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Compiles V2-10-03 karst spring outlet plans bound to sealed host cave and surface outlet. */
public final class KarstSpringPlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private static final long FIXED = TerrainIntentV2.FIXED_SCALE;
    private static final Set<TerrainIntentV2.FeatureKind> SURFACE_OUTLET_KINDS = EnumSet.of(
            TerrainIntentV2.FeatureKind.PLAIN,
            TerrainIntentV2.FeatureKind.VALLEY,
            TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE,
            TerrainIntentV2.FeatureKind.HILL_RANGE,
            TerrainIntentV2.FeatureKind.FLOODPLAIN);

    public KarstSpringPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            String outletSurfaceGeometryChecksum,
            CaveNetworkPlanV2 hostCave
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        Objects.requireNonNull(outletSurfaceGeometryChecksum, "outletSurfaceGeometryChecksum");
        Objects.requireNonNull(hostCave, "hostCave");

        SinkholePlanCompilerV2.requireLimestoneGeology(intent, "v2.karst-spring-non-limestone");

        if (feature.kind() != TerrainIntentV2.FeatureKind.KARST_SPRING) {
            throw failure("v2.karst-spring-kind", "feature kind is not KARST_SPRING");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.KarstSpringParameters parameters)) {
            throw failure("v2.karst-spring-params", "karst spring parameters missing");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PointGeometry pointGeometry)) {
            throw failure("v2.karst-spring-geometry", "karst spring requires POINT geometry");
        }

        CaveBinding cave = resolveCaveBinding(feature, intent);
        if (!cave.caveNetworkFeatureId().equals(hostCave.featureId())) {
            throw failure("v2.karst-spring-orphan", "cave relation target does not match frozen host cave featureId");
        }
        OutletBinding outlet = resolveOutletBinding(feature, intent);

        long openingX = Math.multiplyExact((long) pointGeometry.point().xMillionths(), bounds.width() - 1L);
        long openingZ = Math.multiplyExact((long) pointGeometry.point().zMillionths(), bounds.length() - 1L);
        long openingY = Math.multiplyExact((long) hostCave.surfaceHeightBlocks(), FIXED);

        int discharge = midpoint(parameters.springDischargeBlocks());
        long work = Math.multiplyExact((long) discharge, 4L);
        if (work > KarstSpringPlanV2.MAXIMUM_WORK_UNITS) {
            throw failure("v2.karst-spring-budget", "karst spring work units exceed budget");
        }
        int support = Math.min(64, Math.max(4, 8));

        return new KarstSpringPlanV2(
                KarstSpringPlanV2.VERSION,
                feature.id(),
                cave.caveNetworkFeatureId(),
                hostCave.canonicalChecksum(),
                cave.caveRelationId(),
                outlet.outletSurfaceFeatureId(),
                outletSurfaceGeometryChecksum,
                outlet.outletRelationId(),
                discharge,
                KarstSpringPlanV2.MATERIAL_HANDOFF_ID,
                openingX,
                openingY,
                openingZ,
                KarstSpringPlanV2.DISCHARGE_MASK_FIELD_ID,
                KarstSpringPlanV2.OWNERSHIP_FIELD_ID,
                KarstSpringPlanV2.REACHABILITY_FIELD_ID,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    private static CaveBinding resolveCaveBinding(TerrainIntentV2.Feature spring, TerrainIntentV2 intent) {
        String endpoint = "feature:" + spring.id();
        List<TerrainIntentV2.Relation> caves = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ORIGINATES_AT)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isCaveNetwork(intent, relation.to()))
                .toList();
        if (caves.isEmpty()) {
            throw failure("v2.karst-spring-orphan", "karst spring requires exactly one HARD ORIGINATES_AT cave network");
        }
        if (caves.size() > 1) {
            throw failure("v2.karst-spring-orphan", "karst spring has multiple HARD cave relations");
        }
        TerrainIntentV2.Relation cave = caves.getFirst();
        return new CaveBinding(cave.id(), cave.to().substring("feature:".length()));
    }

    private static OutletBinding resolveOutletBinding(TerrainIntentV2.Feature spring, TerrainIntentV2 intent) {
        String springEndpoint = "feature:" + spring.id();
        List<TerrainIntentV2.Relation> emptiesInto = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO)
                .filter(relation -> springEndpoint.equals(relation.from()))
                .toList();
        if (emptiesInto.size() == 1) {
            TerrainIntentV2.Relation outlet = emptiesInto.getFirst();
            if (outlet.to().startsWith("boundary:")) {
                return outletFromSinkholeSurface(spring, intent, "boundary-" + outlet.id());
            }
            if (!isSurfaceOutlet(intent, outlet.to())) {
                throw failure("v2.karst-spring-missing-outlet", "karst spring EMPTIES_INTO target is invalid");
            }
            return new OutletBinding(outlet.id(), outlet.to().substring("feature:".length()));
        }
        if (emptiesInto.size() > 1) {
            throw failure("v2.karst-spring-ambiguous-outlet", "karst spring has multiple HARD EMPTIES_INTO relations");
        }

        List<TerrainIntentV2.Relation> upstreamFromSink = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.UPSTREAM_OF)
                .filter(relation -> springEndpoint.equals(relation.to()))
                .filter(relation -> relation.from().startsWith("feature:"))
                .filter(relation -> isSinkhole(intent, relation.from()))
                .toList();
        if (upstreamFromSink.isEmpty()) {
            throw failure("v2.karst-spring-missing-outlet",
                    "karst spring requires HARD EMPTIES_INTO surface/boundary outlet or sinkhole UPSTREAM_OF spring");
        }
        if (upstreamFromSink.size() > 1) {
            throw failure("v2.karst-spring-ambiguous-outlet", "karst spring has multiple upstream sinkhole relations");
        }
        return outletFromSinkholeSurface(spring, intent, "upstream-of-" + upstreamFromSink.getFirst().id());
    }

    private static OutletBinding outletFromSinkholeSurface(
            TerrainIntentV2.Feature spring,
            TerrainIntentV2 intent,
            String outletRelationId
    ) {
        Optional<TerrainIntentV2.Relation> sinkholeHost = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.SUPPORTED_BY)
                .filter(relation -> isSinkhole(intent, relation.from()))
                .findFirst();
        if (sinkholeHost.isEmpty()) {
            throw failure("v2.karst-spring-missing-outlet",
                    "karst spring outlet requires a sinkhole SUPPORTED_BY surface host");
        }
        if (!isSurfaceOutlet(intent, sinkholeHost.get().to())) {
            throw failure("v2.karst-spring-missing-outlet", "sinkhole surface host kind is invalid for spring outlet");
        }
        return new OutletBinding(outletRelationId, sinkholeHost.get().to().substring("feature:".length()));
    }

    private static boolean isSinkhole(TerrainIntentV2 intent, String endpoint) {
        if (!endpoint.startsWith("feature:")) {
            return false;
        }
        return featureKind(intent, endpoint.substring("feature:".length()))
                == TerrainIntentV2.FeatureKind.SINKHOLE;
    }

    private static boolean isSurfaceOutlet(TerrainIntentV2 intent, String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return false;
        }
        return SURFACE_OUTLET_KINDS.contains(featureKind(intent, endpoint.substring("feature:".length())));
    }

    private static boolean isCaveNetwork(TerrainIntentV2 intent, String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return false;
        }
        return featureKind(intent, endpoint.substring("feature:".length()))
                == TerrainIntentV2.FeatureKind.CAVE_NETWORK;
    }

    private static TerrainIntentV2.FeatureKind featureKind(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(feature -> feature.id().equals(featureId))
                .map(TerrainIntentV2.Feature::kind)
                .findFirst()
                .orElseThrow(() -> failure("v2.karst-spring-orphan", "referenced feature missing: " + featureId));
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }

    public record CaveBinding(String caveRelationId, String caveNetworkFeatureId) {
    }

    public record OutletBinding(String outletRelationId, String outletSurfaceFeatureId) {
    }
}
