package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical V2-3-08 execution plan for one TIDAL_CHANNEL_NETWORK.
 * Mangrove shaping, salinity/hydroperiod fields, and coral pass are out of scope.
 */
public record TidalChannelPlanV2(
        int planVersion,
        String featureId,
        String emptiesIntoRelationId,
        TerrainIntentV2.Edge receivingSeaBoundary,
        TerrainIntentV2.TidalEdgeKind edgeKind,
        WetlandChildPlanHook wetlandChildPlanHook,
        int minimumWidthBlocks,
        int maximumWidthBlocks,
        int selectedWidthBlocks,
        int selectedTidalRangeBlocks,
        List<ChannelNode> nodes,
        List<ChannelEdge> edges,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String channelMaskFieldId,
        String branchIndexFieldId,
        String depthCorridorFieldId,
        String marineConnectionFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum
) {
    public static final int VERSION = 1;
    public static final int MAXIMUM_NODES = 256;
    public static final int MAXIMUM_EDGES = 128;
    public static final int MAXIMUM_PATH_POINTS = 1_024;
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final long JOIN_TOLERANCE_MILLIONTHS = 16L * TerrainIntentV2.FIXED_SCALE;

    public TidalChannelPlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("tidal planVersion must be 1");
        featureId = V2Validation.slug(featureId, "featureId");
        emptiesIntoRelationId = V2Validation.slug(emptiesIntoRelationId, "emptiesIntoRelationId");
        Objects.requireNonNull(receivingSeaBoundary, "receivingSeaBoundary");
        Objects.requireNonNull(edgeKind, "edgeKind");
        if (edgeKind != TerrainIntentV2.TidalEdgeKind.BIDIRECTIONAL) {
            throw new IllegalArgumentException("unsupported tidal edge kind");
        }
        // null means no wetland child-plan hook; mangrove shaping remains V2-4.
        requireSelectedRange(minimumWidthBlocks, selectedWidthBlocks, maximumWidthBlocks, 2, 32, "width");
        if (selectedTidalRangeBlocks < 1 || selectedTidalRangeBlocks > 8) {
            throw new IllegalArgumentException("tidal range outside 1..8");
        }
        nodes = V2Validation.sorted(nodes, "nodes", MAXIMUM_NODES, Comparator.comparing(ChannelNode::nodeId));
        edges = V2Validation.sorted(edges, "edges", MAXIMUM_EDGES, Comparator.comparing(ChannelEdge::edgeId));
        if (nodes.isEmpty() || edges.isEmpty()) {
            throw new IllegalArgumentException("tidal network requires nodes and edges");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000
                || minY >= maxY || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("tidal world bounds are invalid");
        }
        long maximumX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maximumZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        for (ChannelNode node : nodes) requireInBounds(node.point(), maximumX, maximumZ, "node");
        for (ChannelEdge edge : edges) {
            for (ChannelPoint point : edge.path()) requireInBounds(point, maximumX, maximumZ, "edge path");
        }
        validateGraph(receivingSeaBoundary, edgeKind, nodes, edges, width, length);
        channelMaskFieldId = V2Validation.qualifiedId(channelMaskFieldId, "channelMaskFieldId");
        branchIndexFieldId = V2Validation.qualifiedId(branchIndexFieldId, "branchIndexFieldId");
        depthCorridorFieldId = V2Validation.qualifiedId(depthCorridorFieldId, "depthCorridorFieldId");
        marineConnectionFieldId = V2Validation.qualifiedId(marineConnectionFieldId, "marineConnectionFieldId");
        int halfWidth = Math.max(1, selectedWidthBlocks / 2);
        if (supportRadiusXZ < halfWidth || supportRadiusXZ > 128) {
            throw new IllegalArgumentException("tidal support radius is insufficient");
        }
        if (estimatedRasterWorkUnits < 1L || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("tidal raster work budget is invalid");
        }
        geometryChecksum = V2Validation.checksum(geometryChecksum, "geometryChecksum");
    }

    private static void validateGraph(
            TerrainIntentV2.Edge boundary,
            TerrainIntentV2.TidalEdgeKind edgeKind,
            List<ChannelNode> nodes,
            List<ChannelEdge> edges,
            int width,
            int length
    ) {
        Map<String, ChannelNode> byId = new HashMap<>();
        for (ChannelNode node : nodes) {
            if (byId.put(node.nodeId(), node) != null) {
                throw new IllegalArgumentException("duplicate tidal node id");
            }
        }
        Set<String> edgeIds = new HashSet<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        boolean hasMarineEndpoint = false;
        for (ChannelEdge edge : edges) {
            if (!edgeIds.add(edge.edgeId())) throw new IllegalArgumentException("duplicate tidal edge id");
            if (edge.edgeKind() != edgeKind) {
                throw new IllegalArgumentException("tidal edge kind mismatch");
            }
            ChannelNode from = byId.get(edge.fromNodeId());
            ChannelNode to = byId.get(edge.toNodeId());
            if (from == null || to == null || edge.fromNodeId().equals(edge.toNodeId())) {
                throw new IllegalArgumentException("tidal edge endpoints are invalid");
            }
            if (!edge.path().getFirst().equals(from.point()) || !edge.path().getLast().equals(to.point())) {
                throw new IllegalArgumentException("tidal edge path does not match endpoints");
            }
            if (from.marine() || to.marine()) hasMarineEndpoint = true;
            if ((from.marine() && !onBoundary(from.point(), boundary, width, length))
                    || (to.marine() && !onBoundary(to.point(), boundary, width, length))) {
                throw new IllegalArgumentException("tidal marine node is not on the receiving sea boundary");
            }
            if ((!from.marine() && onBoundary(from.point(), boundary, width, length))
                    || (!to.marine() && onBoundary(to.point(), boundary, width, length))) {
                throw new IllegalArgumentException("tidal boundary endpoint is not marked marine");
            }
            adjacency.computeIfAbsent(from.nodeId(), ignored -> new ArrayList<>()).add(to.nodeId());
            adjacency.computeIfAbsent(to.nodeId(), ignored -> new ArrayList<>()).add(from.nodeId());
        }
        if (!hasMarineEndpoint) {
            throw new IllegalArgumentException("tidal network has no open-sea connection");
        }
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (ChannelNode node : nodes) {
            if (node.marine()) {
                reachable.add(node.nodeId());
                queue.add(node.nodeId());
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String next : adjacency.getOrDefault(current, List.of())) {
                if (reachable.add(next)) queue.add(next);
            }
        }
        if (reachable.size() != nodes.size()) {
            throw new IllegalArgumentException("tidal network contains an isolated component");
        }
    }

    public static boolean onBoundary(ChannelPoint point, TerrainIntentV2.Edge boundary, int width, int length) {
        long maximumX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maximumZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        return switch (boundary) {
            case NORTH -> point.zMillionths() == 0L;
            case EAST -> point.xMillionths() == maximumX;
            case SOUTH -> point.zMillionths() == maximumZ;
            case WEST -> point.xMillionths() == 0L;
        };
    }

    private static void requireSelectedRange(
            int minimum,
            int selected,
            int maximum,
            int allowedMinimum,
            int allowedMaximum,
            String name
    ) {
        if (minimum < allowedMinimum || minimum > selected || selected > maximum || maximum > allowedMaximum) {
            throw new IllegalArgumentException("tidal " + name + " contract is invalid");
        }
    }

    private static void requireInBounds(ChannelPoint point, long maximumX, long maximumZ, String name) {
        if (point.xMillionths() < 0L || point.xMillionths() > maximumX
                || point.zMillionths() < 0L || point.zMillionths() > maximumZ) {
            throw new IllegalArgumentException("tidal " + name + " is outside bounds");
        }
    }

    public record ChannelPoint(long xMillionths, long zMillionths) {
        public ChannelPoint {
            if (xMillionths < 0L || zMillionths < 0L) {
                throw new IllegalArgumentException("tidal point is out of range");
            }
        }
    }

    public record ChannelNode(String nodeId, ChannelPoint point, boolean marine) {
        public ChannelNode {
            nodeId = V2Validation.slug(nodeId, "nodeId");
            Objects.requireNonNull(point, "point");
        }
    }

    public record ChannelEdge(
            String edgeId,
            String pathId,
            String fromNodeId,
            String toNodeId,
            List<ChannelPoint> path,
            TerrainIntentV2.TidalEdgeKind edgeKind,
            int halfWidthBlocks,
            int depthBlocks
    ) {
        public ChannelEdge {
            edgeId = V2Validation.slug(edgeId, "edgeId");
            pathId = V2Validation.slug(pathId, "pathId");
            fromNodeId = V2Validation.slug(fromNodeId, "fromNodeId");
            toNodeId = V2Validation.slug(toNodeId, "toNodeId");
            path = V2Validation.immutable(path, "edge path", MAXIMUM_PATH_POINTS);
            Objects.requireNonNull(edgeKind, "edgeKind");
            if (path.size() < 2 || halfWidthBlocks < 1 || halfWidthBlocks > 16
                    || depthBlocks < 1 || depthBlocks > 8) {
                throw new IllegalArgumentException("tidal channel edge is invalid");
            }
        }
    }

    /**
     * Frozen hook for a future MANGROVE_WETLAND child plan. Shaping itself is V2-4 scope.
     */
    public record WetlandChildPlanHook(String wetlandFeatureId, String withinRelationId) {
        public WetlandChildPlanHook {
            wetlandFeatureId = V2Validation.slug(wetlandFeatureId, "wetlandFeatureId");
            withinRelationId = V2Validation.slug(withinRelationId, "withinRelationId");
        }
    }
}
