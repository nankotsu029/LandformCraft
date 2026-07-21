package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Frozen general RIVER foundation plan (V2-9-04 base, V2-9-13 graph roles).
 * Encodes headwater/stream/tributary/confluence/distributary/rapids and
 * sandbar/river-island/plunge-pool children without new FeatureKinds.
 * Distinct from {@code MeanderingRiverPlanV2}; does not reinterpret meander seeds.
 */
public record RiverPlanV2(
        int planVersion,
        String featureId,
        TerrainIntentV2.DischargeClass dischargeClass,
        List<Node> nodes,
        List<Reach> reaches,
        List<ChildFeature> children,
        List<CenterlineSample> centerline,
        long totalArcLengthMillionths,
        long sourceBedYMillionths,
        long mouthBedYMillionths,
        int selectedBankfullWidthBlocks,
        int bankWidthBlocks,
        int floodplainHandoffWidthBlocks,
        long minimumBedSlopeMillionths,
        int selectedDischargeIndex,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String channelMaskFieldId,
        String bankMaskFieldId,
        String floodplainMaskFieldId,
        String bedElevationFieldId,
        String dischargeIndexFieldId,
        int supportRadiusXZ,
        long estimatedGraphWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.river";
    public static final String MODULE_VERSION = "0.1.0-v2-9-13";
    public static final String CONTRACT = "river-plan-contract-v1";
    public static final long MAXIMUM_GRAPH_WORK_UNITS = 64_000_000L;
    public static final int MAXIMUM_CENTERLINE_SAMPLES = 2_048;
    public static final int MAXIMUM_NODES = 32;
    public static final int MAXIMUM_REACHES = 24;
    public static final int MAXIMUM_CHILDREN = 32;
    public static final int MAXIMUM_BRANCH_FANOUT = 4;
    public static final int DISCHARGE_SHARE_TOTAL = TerrainIntentV2.FIXED_SCALE;
    public static final String CHANNEL_MASK_FIELD_ID = "foundation.river.channel-mask";
    public static final String BANK_MASK_FIELD_ID = "foundation.river.bank-mask";
    public static final String FLOODPLAIN_MASK_FIELD_ID = "foundation.river.floodplain-mask";
    public static final String BED_ELEVATION_FIELD_ID = "foundation.river.bed-elevation";
    public static final String DISCHARGE_INDEX_FIELD_ID = "foundation.river.discharge-index";

    /** Structural graph node kinds (roles are orthogonal; HEADWATER is a SOURCE role). */
    public enum NodeKind { SOURCE, CONFLUENCE, BIFURCATION, WATERFALL, MOUTH }

    /** Graph-internal node role. HEADWATER labels a SOURCE; never a FeatureKind. */
    public enum NodeRole { NONE, HEADWATER }

    /** Reach class semantic (STREAM/TRIBUTARY/DISTRIBUTARY are not FeatureKinds). */
    public enum ReachClass { MAIN_STEM, STREAM, TRIBUTARY, DISTRIBUTARY }

    /** Ordered reach modifiers (RAPIDS is not a FeatureKind). */
    public enum ReachModifier { RAPIDS }

    /** Channel/sediment/waterfall child ownership (not FeatureKinds). */
    public enum ChildKind { SANDBAR, RIVER_ISLAND, PLUNGE_POOL }

    public RiverPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("river planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        Objects.requireNonNull(dischargeClass, "dischargeClass");
        nodes = FoundationValidationV2.sorted(nodes, "nodes", MAXIMUM_NODES,
                Comparator.comparing(Node::nodeId));
        reaches = FoundationValidationV2.sorted(reaches, "reaches", MAXIMUM_REACHES,
                Comparator.comparing(Reach::reachId));
        children = FoundationValidationV2.sorted(children, "children", MAXIMUM_CHILDREN,
                Comparator.comparing(ChildFeature::childId));
        centerline = FoundationValidationV2.sorted(centerline, "centerline", MAXIMUM_CENTERLINE_SAMPLES,
                Comparator.comparingLong(CenterlineSample::arcLengthMillionths)
                        .thenComparingInt(CenterlineSample::sequence));
        validateGraph(nodes, reaches, children);
        if (centerline.size() < 2) {
            throw new IllegalArgumentException("river centerline requires at least two samples");
        }
        if (centerline.getFirst().arcLengthMillionths() != 0L
                || centerline.getLast().arcLengthMillionths() != totalArcLengthMillionths
                || totalArcLengthMillionths < TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("river centerline arc-length contract is invalid");
        }
        for (int index = 1; index < centerline.size(); index++) {
            if (centerline.get(index).bedYMillionths() > centerline.get(index - 1).bedYMillionths()) {
                throw new IllegalArgumentException("river bed profile must be monotonically non-increasing");
            }
        }
        if (sourceBedYMillionths != centerline.getFirst().bedYMillionths()
                || mouthBedYMillionths != centerline.getLast().bedYMillionths()
                || mouthBedYMillionths > sourceBedYMillionths) {
            throw new IllegalArgumentException("river bed endpoints are invalid");
        }
        if (selectedBankfullWidthBlocks < 2 || selectedBankfullWidthBlocks > 64
                || bankWidthBlocks < 1 || bankWidthBlocks > 32
                || floodplainHandoffWidthBlocks < selectedBankfullWidthBlocks
                || floodplainHandoffWidthBlocks > 128
                || minimumBedSlopeMillionths < 1
                || minimumBedSlopeMillionths > TerrainIntentV2.FIXED_SCALE
                || selectedDischargeIndex < 1 || selectedDischargeIndex > 3) {
            throw new IllegalArgumentException("river width/discharge contract is invalid");
        }
        if (width < 2 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 2 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("river bounds are invalid");
        }
        channelMaskFieldId = FoundationValidationV2.qualified(channelMaskFieldId, "channelMaskFieldId");
        bankMaskFieldId = FoundationValidationV2.qualified(bankMaskFieldId, "bankMaskFieldId");
        floodplainMaskFieldId = FoundationValidationV2.qualified(floodplainMaskFieldId, "floodplainMaskFieldId");
        bedElevationFieldId = FoundationValidationV2.qualified(bedElevationFieldId, "bedElevationFieldId");
        dischargeIndexFieldId = FoundationValidationV2.qualified(dischargeIndexFieldId, "dischargeIndexFieldId");
        if (supportRadiusXZ < floodplainHandoffWidthBlocks || supportRadiusXZ > 64
                || estimatedGraphWorkUnits < 1 || estimatedGraphWorkUnits > MAXIMUM_GRAPH_WORK_UNITS) {
            throw new IllegalArgumentException("river support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public RiverPlanV2 withCanonicalChecksum(String checksum) {
        return new RiverPlanV2(
                planVersion, featureId, dischargeClass, nodes, reaches, children, centerline,
                totalArcLengthMillionths, sourceBedYMillionths, mouthBedYMillionths,
                selectedBankfullWidthBlocks, bankWidthBlocks, floodplainHandoffWidthBlocks,
                minimumBedSlopeMillionths, selectedDischargeIndex, minY, maxY, waterLevel, width, length,
                channelMaskFieldId, bankMaskFieldId, floodplainMaskFieldId, bedElevationFieldId,
                dischargeIndexFieldId, supportRadiusXZ, estimatedGraphWorkUnits, geometryChecksum, checksum);
    }

    private static void validateGraph(List<Node> nodes, List<Reach> reaches, List<ChildFeature> children) {
        if (nodes.isEmpty() || reaches.isEmpty()) {
            throw new IllegalArgumentException("river graph requires nodes and reaches");
        }
        Map<String, Node> byId = new HashMap<>();
        for (Node node : nodes) {
            if (byId.put(node.nodeId(), node) != null) {
                throw new IllegalArgumentException("duplicate river node id");
            }
            if (node.kind() == NodeKind.SOURCE && node.role() != NodeRole.HEADWATER) {
                throw new IllegalArgumentException("SOURCE node must carry HEADWATER role");
            }
            if (node.kind() != NodeKind.SOURCE && node.role() == NodeRole.HEADWATER) {
                throw new IllegalArgumentException("HEADWATER role is only valid on SOURCE");
            }
        }
        long sources = nodes.stream().filter(node -> node.kind() == NodeKind.SOURCE).count();
        long mouths = nodes.stream().filter(node -> node.kind() == NodeKind.MOUTH).count();
        if (sources < 1 || mouths != 1) {
            throw new IllegalArgumentException("river graph requires >=1 SOURCE and exactly 1 MOUTH");
        }
        Set<String> reachIds = new HashSet<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Integer> outDegree = new HashMap<>();
        Map<String, List<Reach>> incoming = new HashMap<>();
        Map<String, List<Reach>> outgoing = new HashMap<>();
        for (Node node : nodes) {
            inDegree.put(node.nodeId(), 0);
            outDegree.put(node.nodeId(), 0);
            incoming.put(node.nodeId(), new ArrayList<>());
            outgoing.put(node.nodeId(), new ArrayList<>());
        }
        for (Reach reach : reaches) {
            if (!reachIds.add(reach.reachId())) {
                throw new IllegalArgumentException("duplicate river reach id");
            }
            if (!byId.containsKey(reach.fromNodeId()) || !byId.containsKey(reach.toNodeId())) {
                throw new IllegalArgumentException("river reach references unknown node");
            }
            if (reach.fromNodeId().equals(reach.toNodeId())) {
                throw new IllegalArgumentException("river reach self-loop is forbidden");
            }
            outDegree.merge(reach.fromNodeId(), 1, Integer::sum);
            inDegree.merge(reach.toNodeId(), 1, Integer::sum);
            outgoing.get(reach.fromNodeId()).add(reach);
            incoming.get(reach.toNodeId()).add(reach);
        }
        for (Node node : nodes) {
            int in = inDegree.getOrDefault(node.nodeId(), 0);
            int out = outDegree.getOrDefault(node.nodeId(), 0);
            if (in + out > 4 || out > MAXIMUM_BRANCH_FANOUT) {
                throw new IllegalArgumentException("river node degree exceeds budget");
            }
            switch (node.kind()) {
                case SOURCE -> {
                    if (in != 0 || out < 1) {
                        throw new IllegalArgumentException("SOURCE node degree is invalid");
                    }
                }
                case MOUTH -> {
                    if (out != 0 || in < 1) {
                        throw new IllegalArgumentException("MOUTH node degree is invalid");
                    }
                }
                case CONFLUENCE -> {
                    if (in < 2 || out != 1) {
                        throw new IllegalArgumentException("CONFLUENCE node degree is invalid");
                    }
                    long inShare = incoming.get(node.nodeId()).stream()
                            .mapToLong(Reach::dischargeShareMillionths).sum();
                    long outShare = outgoing.get(node.nodeId()).getFirst().dischargeShareMillionths();
                    if (inShare != outShare) {
                        throw new IllegalArgumentException("CONFLUENCE flow conservation failed");
                    }
                    validateElevationNonIncreasing(incoming.get(node.nodeId()), outgoing.get(node.nodeId()), byId);
                }
                case BIFURCATION -> {
                    if (in != 1 || out < 2) {
                        throw new IllegalArgumentException("BIFURCATION node degree is invalid");
                    }
                    long inShare = incoming.get(node.nodeId()).getFirst().dischargeShareMillionths();
                    long outShare = outgoing.get(node.nodeId()).stream()
                            .mapToLong(Reach::dischargeShareMillionths).sum();
                    if (inShare != outShare) {
                        throw new IllegalArgumentException("BIFURCATION flow conservation failed");
                    }
                    validateElevationNonIncreasing(incoming.get(node.nodeId()), outgoing.get(node.nodeId()), byId);
                }
                case WATERFALL -> {
                    if (in != 1 || out != 1) {
                        throw new IllegalArgumentException("WATERFALL node degree is invalid");
                    }
                    Node upstream = byId.get(incoming.get(node.nodeId()).getFirst().fromNodeId());
                    Node downstream = byId.get(outgoing.get(node.nodeId()).getFirst().toNodeId());
                    if (node.bedYMillionths() > upstream.bedYMillionths()
                            || downstream.bedYMillionths() > node.bedYMillionths()) {
                        throw new IllegalArgumentException("WATERFALL elevation continuity failed");
                    }
                }
            }
        }
        for (Reach reach : reaches) {
            Node from = byId.get(reach.fromNodeId());
            Node to = byId.get(reach.toNodeId());
            switch (reach.reachClass()) {
                case TRIBUTARY -> {
                    if (to.kind() != NodeKind.CONFLUENCE) {
                        throw new IllegalArgumentException("TRIBUTARY reach must end at CONFLUENCE");
                    }
                }
                case DISTRIBUTARY -> {
                    if (from.kind() != NodeKind.BIFURCATION) {
                        throw new IllegalArgumentException("DISTRIBUTARY reach must start at BIFURCATION");
                    }
                }
                case STREAM, MAIN_STEM -> {
                    // allowed on any structural edge
                }
            }
            if (to.bedYMillionths() > from.bedYMillionths()) {
                throw new IllegalArgumentException("reach elevation must be non-increasing downstream");
            }
        }
        if (hasCycle(nodes, reaches)) {
            throw new IllegalArgumentException("river graph contains a cycle");
        }
        if (!reachableFromSourcesToMouth(nodes, reaches)) {
            throw new IllegalArgumentException("river source-to-mouth reachability failed");
        }
        Set<String> childIds = new HashSet<>();
        for (ChildFeature child : children) {
            if (!childIds.add(child.childId())) {
                throw new IllegalArgumentException("duplicate river child id");
            }
            switch (child.kind()) {
                case SANDBAR, RIVER_ISLAND -> {
                    if (child.ownerReachId().isEmpty() || !reachIds.contains(child.ownerReachId())) {
                        throw new IllegalArgumentException("sandbar/river-island requires owning reach");
                    }
                    if (!child.ownerNodeId().isEmpty()) {
                        throw new IllegalArgumentException("sandbar/river-island must not bind a node owner");
                    }
                }
                case PLUNGE_POOL -> {
                    if (child.ownerNodeId().isEmpty() || !byId.containsKey(child.ownerNodeId())) {
                        throw new IllegalArgumentException("plunge-pool requires owning WATERFALL node");
                    }
                    if (byId.get(child.ownerNodeId()).kind() != NodeKind.WATERFALL) {
                        throw new IllegalArgumentException("plunge-pool owner must be WATERFALL");
                    }
                    if (!child.ownerReachId().isEmpty()) {
                        throw new IllegalArgumentException("plunge-pool must not bind a reach owner");
                    }
                }
            }
        }
        for (Node node : nodes) {
            if (node.kind() == NodeKind.WATERFALL) {
                boolean hasPool = children.stream()
                        .anyMatch(child -> child.kind() == ChildKind.PLUNGE_POOL
                                && child.ownerNodeId().equals(node.nodeId()));
                if (!hasPool) {
                    throw new IllegalArgumentException("WATERFALL node requires a plunge-pool child");
                }
            }
        }
    }

    private static void validateElevationNonIncreasing(
            List<Reach> incoming,
            List<Reach> outgoing,
            Map<String, Node> byId
    ) {
        long maxIncomingBed = incoming.stream()
                .mapToLong(reach -> byId.get(reach.fromNodeId()).bedYMillionths())
                .max()
                .orElse(0L);
        long minOutgoingBed = outgoing.stream()
                .mapToLong(reach -> byId.get(reach.toNodeId()).bedYMillionths())
                .min()
                .orElse(0L);
        Node junction = byId.get(outgoing.getFirst().fromNodeId());
        if (junction.bedYMillionths() > maxIncomingBed || minOutgoingBed > junction.bedYMillionths()) {
            throw new IllegalArgumentException("junction elevation continuity failed");
        }
    }

    private static boolean hasCycle(List<Node> nodes, List<Reach> reaches) {
        Map<String, List<String>> outgoing = new HashMap<>();
        for (Node node : nodes) {
            outgoing.put(node.nodeId(), new ArrayList<>());
        }
        for (Reach reach : reaches) {
            outgoing.get(reach.fromNodeId()).add(reach.toNodeId());
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (Node node : nodes) {
            if (dfsCycle(node.nodeId(), outgoing, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean dfsCycle(
            String nodeId,
            Map<String, List<String>> outgoing,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (visited.contains(nodeId)) {
            return false;
        }
        if (!visiting.add(nodeId)) {
            return true;
        }
        for (String next : outgoing.getOrDefault(nodeId, List.of())) {
            if (dfsCycle(next, outgoing, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    private static boolean reachableFromSourcesToMouth(List<Node> nodes, List<Reach> reaches) {
        String mouthId = nodes.stream()
                .filter(node -> node.kind() == NodeKind.MOUTH)
                .map(Node::nodeId)
                .findFirst()
                .orElseThrow();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (Node node : nodes) {
            outgoing.put(node.nodeId(), new ArrayList<>());
        }
        for (Reach reach : reaches) {
            outgoing.get(reach.fromNodeId()).add(reach.toNodeId());
        }
        for (Node source : nodes) {
            if (source.kind() != NodeKind.SOURCE) {
                continue;
            }
            Set<String> seen = new HashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(source.nodeId());
            seen.add(source.nodeId());
            boolean reached = false;
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                if (current.equals(mouthId)) {
                    reached = true;
                    break;
                }
                for (String next : outgoing.getOrDefault(current, List.of())) {
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            if (!reached) {
                return false;
            }
        }
        return true;
    }

    public record Node(
            String nodeId,
            NodeKind kind,
            NodeRole role,
            long xMillionths,
            long zMillionths,
            long bedYMillionths
    ) {
        public Node {
            nodeId = FoundationValidationV2.slug(nodeId, "nodeId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(role, "role");
            if (xMillionths < 0 || zMillionths < 0) {
                throw new IllegalArgumentException("river node coordinates are invalid");
            }
        }
    }

    public record Reach(
            String reachId,
            String fromNodeId,
            String toNodeId,
            ReachClass reachClass,
            List<ReachModifier> modifiers,
            int dischargeShareMillionths
    ) {
        public Reach {
            reachId = FoundationValidationV2.slug(reachId, "reachId");
            fromNodeId = FoundationValidationV2.slug(fromNodeId, "fromNodeId");
            toNodeId = FoundationValidationV2.slug(toNodeId, "toNodeId");
            Objects.requireNonNull(reachClass, "reachClass");
            modifiers = FoundationValidationV2.sorted(
                    modifiers == null ? List.of() : modifiers,
                    "modifiers",
                    8,
                    Comparator.comparing(Enum::name));
            if (dischargeShareMillionths < 1 || dischargeShareMillionths > DISCHARGE_SHARE_TOTAL) {
                throw new IllegalArgumentException("reach dischargeShareMillionths outside 1..1000000");
            }
        }
    }

    public record ChildFeature(
            String childId,
            ChildKind kind,
            String ownerReachId,
            String ownerNodeId,
            long xMillionths,
            long zMillionths,
            int radiusBlocks,
            int ownershipPriority
    ) {
        public ChildFeature {
            childId = FoundationValidationV2.slug(childId, "childId");
            Objects.requireNonNull(kind, "kind");
            ownerReachId = ownerReachId == null || ownerReachId.isBlank()
                    ? ""
                    : FoundationValidationV2.slug(ownerReachId, "ownerReachId");
            ownerNodeId = ownerNodeId == null || ownerNodeId.isBlank()
                    ? ""
                    : FoundationValidationV2.slug(ownerNodeId, "ownerNodeId");
            if (xMillionths < 0 || zMillionths < 0
                    || radiusBlocks < 1 || radiusBlocks > 32
                    || ownershipPriority < 1 || ownershipPriority > 64) {
                throw new IllegalArgumentException("river child feature contract is invalid");
            }
        }
    }

    public record CenterlineSample(
            int sequence,
            long xMillionths,
            long zMillionths,
            long arcLengthMillionths,
            long bedYMillionths
    ) {
        public CenterlineSample {
            if (sequence < 0 || xMillionths < 0 || zMillionths < 0 || arcLengthMillionths < 0) {
                throw new IllegalArgumentException("river centerline sample is invalid");
            }
        }
    }
}
