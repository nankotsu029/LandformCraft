package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SinkholePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compiles V2-10-03 sinkhole plans bound to surface host and sealed host cave. */
public final class SinkholePlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private static final long FIXED = TerrainIntentV2.FIXED_SCALE;
    private static final Set<TerrainIntentV2.FeatureKind> SURFACE_HOST_KINDS = EnumSet.of(
            TerrainIntentV2.FeatureKind.PLAIN,
            TerrainIntentV2.FeatureKind.VALLEY,
            TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE,
            TerrainIntentV2.FeatureKind.HILL_RANGE,
            TerrainIntentV2.FeatureKind.FLOODPLAIN);
    private static final Set<TerrainIntentV2.RelationKind> CAVE_RELATIONS = EnumSet.of(
            TerrainIntentV2.RelationKind.ENTRANCE_OF,
            TerrainIntentV2.RelationKind.DRAINS_TO);

    public SinkholePlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            String surfaceHostGeometryChecksum,
            CaveNetworkPlanV2 hostCave
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        Objects.requireNonNull(surfaceHostGeometryChecksum, "surfaceHostGeometryChecksum");
        Objects.requireNonNull(hostCave, "hostCave");

        requireLimestoneGeology(intent, "v2.sinkhole-non-limestone");

        if (feature.kind() != TerrainIntentV2.FeatureKind.SINKHOLE) {
            throw failure("v2.sinkhole-kind", "feature kind is not SINKHOLE");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.SinkholeParameters parameters)) {
            throw failure("v2.sinkhole-params", "sinkhole parameters missing");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PointGeometry pointGeometry)) {
            throw failure("v2.sinkhole-geometry", "sinkhole requires POINT geometry");
        }

        SurfaceBinding surface = resolveSurfaceHost(feature, intent);
        CaveBinding cave = resolveCaveBinding(feature, intent);
        if (!cave.caveNetworkFeatureId().equals(hostCave.featureId())) {
            throw failure("v2.sinkhole-orphan", "cave relation target does not match frozen host cave featureId");
        }
        if (!hostCave.entranceNodeIds().contains(parameters.targetEntranceNodeId())) {
            throw failure("v2.sinkhole-orphan",
                    "targetEntranceNodeId must be listed in host cave entranceNodeIds");
        }

        long openingX = Math.multiplyExact((long) pointGeometry.point().xMillionths(), bounds.width() - 1L);
        long openingZ = Math.multiplyExact((long) pointGeometry.point().zMillionths(), bounds.length() - 1L);
        long openingY = Math.multiplyExact((long) hostCave.surfaceHeightBlocks(), FIXED);

        int collapseRadius = midpoint(parameters.collapseRadiusBlocks());
        int roofClearance = midpoint(parameters.roofClearanceBlocks());
        int lossVolume = midpoint(parameters.lossVolumeBlocks());
        long work = Math.multiplyExact(
                Math.multiplyExact((long) collapseRadius, (long) collapseRadius),
                Math.max(1L, lossVolume));
        if (work > SinkholePlanV2.MAXIMUM_WORK_UNITS) {
            throw failure("v2.sinkhole-budget", "sinkhole work units exceed budget");
        }
        int support = Math.min(64, Math.max(4, collapseRadius + roofClearance));

        return new SinkholePlanV2(
                SinkholePlanV2.VERSION,
                feature.id(),
                surface.surfaceHostFeatureId(),
                surfaceHostGeometryChecksum,
                surface.surfaceHostRelationId(),
                cave.caveNetworkFeatureId(),
                hostCave.canonicalChecksum(),
                cave.caveRelationId(),
                parameters.targetEntranceNodeId(),
                collapseRadius,
                roofClearance,
                lossVolume,
                SinkholePlanV2.MATERIAL_HANDOFF_ID,
                openingX,
                openingY,
                openingZ,
                SinkholePlanV2.COLLAPSE_MASK_FIELD_ID,
                SinkholePlanV2.OWNERSHIP_FIELD_ID,
                SinkholePlanV2.REACHABILITY_FIELD_ID,
                SinkholePlanV2.ROOF_CLEARANCE_FIELD_ID,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    static void requireLimestoneGeology(TerrainIntentV2 intent, String ruleId) {
        String geology = intent.environment().geologyPreset();
        if (geology == null || geology.isBlank()) {
            throw failure(ruleId, "karst features require limestone-like geologyPreset");
        }
        if (!"FRACTURED_LIMESTONE_LIKE".equals(geology) && !geology.contains("LIMESTONE")) {
            throw failure(ruleId, "geologyPreset is not limestone-like: " + geology);
        }
    }

    private static SurfaceBinding resolveSurfaceHost(TerrainIntentV2.Feature sinkhole, TerrainIntentV2 intent) {
        String endpoint = "feature:" + sinkhole.id();
        List<TerrainIntentV2.Relation> hosts = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.SUPPORTED_BY)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isSurfaceHost(intent, relation.to()))
                .toList();
        if (hosts.isEmpty()) {
            throw failure("v2.sinkhole-missing-host", "sinkhole requires exactly one HARD SUPPORTED_BY surface host");
        }
        if (hosts.size() > 1) {
            throw failure("v2.sinkhole-ambiguous-host", "sinkhole has multiple HARD surface host relations");
        }
        TerrainIntentV2.Relation host = hosts.getFirst();
        return new SurfaceBinding(host.id(), host.to().substring("feature:".length()));
    }

    private static CaveBinding resolveCaveBinding(TerrainIntentV2.Feature sinkhole, TerrainIntentV2 intent) {
        String endpoint = "feature:" + sinkhole.id();
        List<TerrainIntentV2.Relation> caves = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> CAVE_RELATIONS.contains(relation.kind()))
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isCaveNetwork(intent, relation.to()))
                .toList();
        if (caves.isEmpty()) {
            throw failure("v2.sinkhole-missing-cave",
                    "sinkhole requires exactly one HARD ENTRANCE_OF/DRAINS_TO cave network");
        }
        if (caves.size() > 1) {
            throw failure("v2.sinkhole-missing-cave", "sinkhole has multiple HARD cave relations");
        }
        TerrainIntentV2.Relation cave = caves.getFirst();
        return new CaveBinding(cave.id(), cave.to().substring("feature:".length()));
    }

    private static boolean isSurfaceHost(TerrainIntentV2 intent, String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return false;
        }
        return SURFACE_HOST_KINDS.contains(featureKind(intent, endpoint.substring("feature:".length())));
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
                .orElseThrow(() -> failure("v2.sinkhole-orphan", "referenced feature missing: " + featureId));
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }

    public record SurfaceBinding(String surfaceHostRelationId, String surfaceHostFeatureId) {
    }

    public record CaveBinding(String caveRelationId, String caveNetworkFeatureId) {
    }
}
