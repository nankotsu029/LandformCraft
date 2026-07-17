package com.github.nankotsu029.landformcraft.generator.v2.hydrology.tidal;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TidalChannelPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Compiles one TIDAL_CHANNEL_NETWORK into a frozen marine-connected bidirectional graph. */
public final class TidalChannelPlanCompilerV2 {
    public TidalChannelPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK) {
            throw failure("v2.tidal-kind", "feature kind is not TIDAL_CHANNEL_NETWORK");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.MultiSplineGeometry multiSpline)) {
            throw failure("v2.tidal-geometry", "tidal channel network requires MULTI_SPLINE geometry");
        }
        if (multiSpline.paths().size() > TidalChannelPlanV2.MAXIMUM_EDGES) {
            throw failure("v2.tidal-budget", "tidal path count exceeds edge budget");
        }
        TerrainIntentV2.TidalChannelParameters parameters =
                (TerrainIntentV2.TidalChannelParameters) feature.parameters();
        try {
            OutletBinding outlet = requireMarineOutlet(feature, intent);
            TidalChannelPlanV2.WetlandChildPlanHook wetlandHook =
                    requireWetlandChildPlanHook(feature, intent);
            int selectedWidth = midpoint(parameters.widthBlocks());
            int halfWidth = Math.max(1, selectedWidth / 2);
            int depth = parameters.tidalRangeBlocks();
            List<EndpointSeed> seeds = flattenEndpoints(multiSpline, bounds);
            List<Cluster> clusters = clusterEndpoints(seeds, outlet.boundary(), bounds);
            rejectAmbiguousDirection(clusters, outlet.boundary(), bounds);
            if (clusters.stream().noneMatch(Cluster::marine)) {
                throw failure("v2.tidal-closed-channel", "tidal network has no open-sea connection");
            }
            Map<Integer, String> nodeIds = assignNodeIds(clusters);
            List<TidalChannelPlanV2.ChannelNode> nodes = clusters.stream()
                    .map(cluster -> new TidalChannelPlanV2.ChannelNode(
                            nodeIds.get(cluster.index()), cluster.point(), cluster.marine()))
                    .sorted(Comparator.comparing(TidalChannelPlanV2.ChannelNode::nodeId))
                    .toList();
            List<TidalChannelPlanV2.ChannelEdge> edges = new ArrayList<>();
            int edgeOrdinal = 1;
            for (TerrainIntentV2.NamedPath path : multiSpline.paths()) {
                if (path.points().size() > TidalChannelPlanV2.MAXIMUM_PATH_POINTS) {
                    throw failure("v2.tidal-budget", "tidal path exceeds point budget");
                }
                List<TidalChannelPlanV2.ChannelPoint> points = flattenPath(path, bounds);
                int startCluster = nearestCluster(points.getFirst(), clusters);
                int endCluster = nearestCluster(points.getLast(), clusters);
                if (startCluster == endCluster) {
                    throw failure("v2.tidal-closed-channel", "tidal path collapses to a single node");
                }
                String fromId = nodeIds.get(startCluster);
                String toId = nodeIds.get(endCluster);
                // Stable undirected orientation by node id so bidirectional semantics stay order-invariant.
                if (fromId.compareTo(toId) > 0) {
                    String swap = fromId;
                    fromId = toId;
                    toId = swap;
                    points = reverse(points);
                }
                edges.add(new TidalChannelPlanV2.ChannelEdge(
                        String.format(Locale.ROOT, "tidal-edge-%04d", edgeOrdinal++),
                        path.id(),
                        fromId,
                        toId,
                        points,
                        parameters.edgeKind(),
                        halfWidth,
                        depth));
            }
            edges = edges.stream()
                    .sorted(Comparator.comparing(TidalChannelPlanV2.ChannelEdge::edgeId))
                    .toList();
            long workUnits = estimatedWorkUnits(edges, bounds);
            if (halfWidth > HydrologyTidalModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.tidal-budget", "tidal support radius exceeds trusted halo budget");
            }
            try {
                TidalChannelPlanV2 plan = new TidalChannelPlanV2(
                        TidalChannelPlanV2.VERSION,
                        feature.id(),
                        outlet.relationId(),
                        outlet.boundary(),
                        parameters.edgeKind(),
                        wetlandHook,
                        parameters.widthBlocks().minimum(),
                        parameters.widthBlocks().maximum(),
                        selectedWidth,
                        depth,
                        nodes,
                        edges,
                        bounds.minY(), bounds.maxY(), bounds.waterLevel(),
                        bounds.width(), bounds.length(),
                        HydrologyTidalModuleV2.CHANNEL_MASK_FIELD_ID,
                        HydrologyTidalModuleV2.BRANCH_INDEX_FIELD_ID,
                        HydrologyTidalModuleV2.DEPTH_CORRIDOR_FIELD_ID,
                        HydrologyTidalModuleV2.MARINE_CONNECTION_FIELD_ID,
                        halfWidth,
                        workUnits,
                        geometryChecksum);
                TidalGraphValidatorV2.requireValid(plan, plan.edges());
                return plan;
            } catch (TidalGenerationException exception) {
                throw exception;
            } catch (IllegalArgumentException exception) {
                throw mapGraphFailure(exception);
            }
        } catch (TidalGenerationException exception) {
            throw exception;
        } catch (ArithmeticException exception) {
            throw new TidalGenerationException("v2.tidal-overflow", "tidal plan arithmetic overflow", exception);
        }
    }

    private static OutletBinding requireMarineOutlet(TerrainIntentV2.Feature tidal, TerrainIntentV2 intent) {
        List<TerrainIntentV2.Relation> outlets = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO
                        && relation.strength() == TerrainIntentV2.Strength.HARD
                        && relation.from().equals("feature:" + tidal.id()))
                .toList();
        if (outlets.size() != 1) {
            throw failure("v2.tidal-outlet-relation",
                    "tidal channel network requires exactly one HARD EMPTIES_INTO boundary");
        }
        TerrainIntentV2.Edge boundary = TerrainIntentV2.Edge.valueOf(
                outlets.getFirst().to().substring("boundary:".length()));
        boolean hardSea = intent.constraints().stream()
                .filter(TerrainIntentV2.EdgeClassificationConstraint.class::isInstance)
                .map(TerrainIntentV2.EdgeClassificationConstraint.class::cast)
                .anyMatch(constraint -> constraint.strength() == TerrainIntentV2.Strength.HARD
                        && constraint.edge() == boundary
                        && constraint.classification() == TerrainIntentV2.EdgeClassification.SEA);
        if (!hardSea) {
            throw failure("v2.tidal-hard-no-data",
                    "tidal receiving boundary must have an explicit HARD SEA classification");
        }
        return new OutletBinding(outlets.getFirst().id(), boundary);
    }

    private static TidalChannelPlanV2.WetlandChildPlanHook requireWetlandChildPlanHook(
            TerrainIntentV2.Feature tidal,
            TerrainIntentV2 intent
    ) {
        List<TerrainIntentV2.Relation> within = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.WITHIN
                        && relation.strength() == TerrainIntentV2.Strength.HARD
                        && relation.from().equals("feature:" + tidal.id()))
                .toList();
        if (within.isEmpty()) {
            return null;
        }
        if (within.size() != 1) {
            throw failure("v2.tidal-wetland-hook", "tidal network allows at most one HARD WITHIN wetland hook");
        }
        String wetlandId = within.getFirst().to().substring("feature:".length());
        TerrainIntentV2.Feature wetland = intent.features().stream()
                .filter(candidate -> candidate.id().equals(wetlandId))
                .findFirst()
                .orElseThrow(() -> failure("v2.tidal-wetland-hook", "wetland child-plan target is missing"));
        if (wetland.kind() != TerrainIntentV2.FeatureKind.MANGROVE_WETLAND) {
            throw failure("v2.tidal-wetland-hook",
                    "tidal WITHIN child-plan hook must target MANGROVE_WETLAND");
        }
        return new TidalChannelPlanV2.WetlandChildPlanHook(wetlandId, within.getFirst().id());
    }

    private static List<EndpointSeed> flattenEndpoints(
            TerrainIntentV2.MultiSplineGeometry geometry,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<EndpointSeed> seeds = new ArrayList<>();
        for (TerrainIntentV2.NamedPath path : geometry.paths()) {
            List<TidalChannelPlanV2.ChannelPoint> points = flattenPath(path, bounds);
            seeds.add(new EndpointSeed(path.id() + "-start", points.getFirst()));
            seeds.add(new EndpointSeed(path.id() + "-end", points.getLast()));
        }
        seeds.sort(Comparator.comparing(EndpointSeed::id));
        return List.copyOf(seeds);
    }

    private static List<TidalChannelPlanV2.ChannelPoint> flattenPath(
            TerrainIntentV2.NamedPath path,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<TidalChannelPlanV2.ChannelPoint> points = new ArrayList<>(path.points().size());
        for (TerrainIntentV2.Point2 point : path.points()) {
            points.add(new TidalChannelPlanV2.ChannelPoint(
                    Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L),
                    Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L)));
        }
        return List.copyOf(points);
    }

    private static List<Cluster> clusterEndpoints(
            List<EndpointSeed> seeds,
            TerrainIntentV2.Edge boundary,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<Cluster> clusters = new ArrayList<>();
        boolean[] used = new boolean[seeds.size()];
        for (int index = 0; index < seeds.size(); index++) {
            if (used[index]) continue;
            EndpointSeed seed = seeds.get(index);
            List<EndpointSeed> members = new ArrayList<>();
            members.add(seed);
            used[index] = true;
            for (int other = index + 1; other < seeds.size(); other++) {
                if (used[other]) continue;
                if (TidalFixedMathV2.hypotMillionths(
                        seeds.get(other).point().xMillionths() - seed.point().xMillionths(),
                        seeds.get(other).point().zMillionths() - seed.point().zMillionths())
                        <= TidalChannelPlanV2.JOIN_TOLERANCE_MILLIONTHS) {
                    members.add(seeds.get(other));
                    used[other] = true;
                }
            }
            long sumX = 0L;
            long sumZ = 0L;
            for (EndpointSeed member : members) {
                sumX = Math.addExact(sumX, member.point().xMillionths());
                sumZ = Math.addExact(sumZ, member.point().zMillionths());
            }
            TidalChannelPlanV2.ChannelPoint point = new TidalChannelPlanV2.ChannelPoint(
                    TidalFixedMathV2.roundDivide(sumX, members.size()),
                    TidalFixedMathV2.roundDivide(sumZ, members.size()));
            boolean marine = TidalChannelPlanV2.onBoundary(point, boundary, bounds.width(), bounds.length());
            clusters.add(new Cluster(clusters.size(), point, marine, List.copyOf(members)));
        }
        if (clusters.size() > TidalChannelPlanV2.MAXIMUM_NODES) {
            throw failure("v2.tidal-budget", "tidal node count exceeds budget");
        }
        return List.copyOf(clusters);
    }

    private static void rejectAmbiguousDirection(
            List<Cluster> clusters,
            TerrainIntentV2.Edge boundary,
            WorldBlueprintV2.Bounds bounds
    ) {
        for (Cluster cluster : clusters) {
            boolean onDeclared = TidalChannelPlanV2.onBoundary(
                    cluster.point(), boundary, bounds.width(), bounds.length());
            for (TerrainIntentV2.Edge edge : TerrainIntentV2.Edge.values()) {
                if (edge == boundary) continue;
                if (TidalChannelPlanV2.onBoundary(cluster.point(), edge, bounds.width(), bounds.length())) {
                    throw failure("v2.tidal-ambiguous-direction",
                            "tidal endpoint lies on a non-declared sea boundary");
                }
            }
            if (onDeclared != cluster.marine()) {
                throw failure("v2.tidal-ambiguous-direction",
                        "tidal marine classification is ambiguous for the declared outlet");
            }
        }
    }

    private static Map<Integer, String> assignNodeIds(List<Cluster> clusters) {
        List<Cluster> ordered = clusters.stream()
                .sorted(Comparator.comparingLong((Cluster cluster) -> cluster.point().xMillionths())
                        .thenComparingLong(cluster -> cluster.point().zMillionths())
                        .thenComparingInt(Cluster::index))
                .toList();
        Map<Integer, String> ids = new HashMap<>();
        int marineOrdinal = 1;
        int inlandOrdinal = 1;
        for (Cluster cluster : ordered) {
            String id = cluster.marine()
                    ? String.format(Locale.ROOT, "marine-%04d", marineOrdinal++)
                    : String.format(Locale.ROOT, "inland-%04d", inlandOrdinal++);
            ids.put(cluster.index(), id);
        }
        return Map.copyOf(ids);
    }

    private static int nearestCluster(
            TidalChannelPlanV2.ChannelPoint point,
            List<Cluster> clusters
    ) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (Cluster cluster : clusters) {
            long distance = TidalFixedMathV2.hypotMillionths(
                    point.xMillionths() - cluster.point().xMillionths(),
                    point.zMillionths() - cluster.point().zMillionths());
            if (distance < bestDistance || (distance == bestDistance && cluster.index() < best)) {
                bestDistance = distance;
                best = cluster.index();
            }
        }
        if (best < 0 || bestDistance > TidalChannelPlanV2.JOIN_TOLERANCE_MILLIONTHS) {
            throw failure("v2.tidal-isolated-component", "tidal path endpoint is not joined to the network");
        }
        return best;
    }

    private static List<TidalChannelPlanV2.ChannelPoint> reverse(List<TidalChannelPlanV2.ChannelPoint> points) {
        List<TidalChannelPlanV2.ChannelPoint> reversed = new ArrayList<>(points.size());
        for (int index = points.size() - 1; index >= 0; index--) {
            reversed.add(points.get(index));
        }
        return List.copyOf(reversed);
    }

    private static long estimatedWorkUnits(
            List<TidalChannelPlanV2.ChannelEdge> edges,
            WorldBlueprintV2.Bounds bounds
    ) {
        long cells = Math.multiplyExact((long) bounds.width(), bounds.length());
        long work = Math.multiplyExact(cells, Math.addExact(Math.multiplyExact((long) edges.size(), 2L), 2L));
        if (work > TidalChannelPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
            throw failure("v2.tidal-budget", "tidal channel/raster CPU budget exceeded");
        }
        return Math.max(1L, work);
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static TidalGenerationException mapGraphFailure(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("no open-sea")) {
            return failure("v2.tidal-closed-channel", message);
        }
        if (message.contains("isolated")) {
            return failure("v2.tidal-isolated-component", message);
        }
        if (message.contains("edge kind") || message.contains("marine")) {
            return failure("v2.tidal-ambiguous-direction", message);
        }
        return new TidalGenerationException("v2.tidal-graph", message, exception);
    }

    private static TidalGenerationException failure(String ruleId, String message) {
        return new TidalGenerationException(ruleId, message);
    }

    private record OutletBinding(String relationId, TerrainIntentV2.Edge boundary) {
    }

    private record EndpointSeed(String id, TidalChannelPlanV2.ChannelPoint point) {
    }

    private record Cluster(
            int index,
            TidalChannelPlanV2.ChannelPoint point,
            boolean marine,
            List<EndpointSeed> members
    ) {
    }
}
