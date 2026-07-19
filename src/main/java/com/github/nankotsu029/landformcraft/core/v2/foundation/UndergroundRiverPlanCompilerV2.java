package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformUndergroundRiverModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.CaveEntrancePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.UndergroundRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.UndergroundLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Compiles a V2-9-11 UNDERGROUND_RIVER plan bound to frozen host cave and underground lake. */
public final class UndergroundRiverPlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private static final long FIXED = TerrainIntentV2.FIXED_SCALE;

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public HostBinding resolveHostBinding(TerrainIntentV2.Feature river, TerrainIntentV2 intent) {
        Objects.requireNonNull(river, "river");
        Objects.requireNonNull(intent, "intent");
        String endpoint = "feature:" + river.id();

        List<TerrainIntentV2.Relation> within = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.WITHIN)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isCaveNetwork(intent, relation.to()))
                .toList();
        if (within.isEmpty()) {
            throw failure("v2.underground-river-missing-relation",
                    "underground river requires exactly one HARD WITHIN cave network");
        }
        if (within.size() > 1) {
            throw failure("v2.underground-river-missing-relation",
                    "underground river has multiple HARD WITHIN cave targets");
        }

        Optional<TerrainIntentV2.Relation> entrance = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ORIGINATES_AT
                        || relation.kind() == TerrainIntentV2.RelationKind.REACHABLE_FROM)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isCaveEntrance(intent, relation.to()))
                .findFirst();

        TerrainIntentV2.Relation cave = within.getFirst();
        String caveId = cave.to().substring("feature:".length());
        String entranceId = entrance
                .map(relation -> relation.to().substring("feature:".length()))
                .orElse("");
        String entranceRelationId = entrance.map(TerrainIntentV2.Relation::id).orElse("");
        return new HostBinding(cave.id(), caveId, entranceRelationId, entranceId);
    }

    public UndergroundRiverPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            CaveNetworkPlanV2 hostCave,
            UndergroundLakePlanV2 hostLake,
            CaveEntrancePlanV2 optionalEntrance
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        Objects.requireNonNull(hostCave, "hostCave");
        Objects.requireNonNull(hostLake, "hostLake");
        if (feature.kind() != TerrainIntentV2.FeatureKind.UNDERGROUND_RIVER) {
            throw failure("v2.underground-river-missing-relation", "feature kind is not UNDERGROUND_RIVER");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.UndergroundRiverParameters parameters)) {
            throw failure("v2.underground-river-missing-relation",
                    "underground river requires UndergroundRiverParameters");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)
                || spline.points().size() < 2) {
            throw failure("v2.underground-river-missing-relation",
                    "underground river requires SPLINE geometry with at least 2 points");
        }

        HostBinding binding = resolveHostBinding(feature, intent);
        if (!binding.caveNetworkFeatureId().equals(hostCave.featureId())) {
            throw failure("v2.underground-river-missing-relation",
                    "WITHIN target does not match frozen host cave featureId");
        }
        if (!hostLake.hostBinding().hostNetworkFeatureId().equals(hostCave.featureId())
                || !hostLake.hostBinding().hostNetworkPlanChecksum().equals(hostCave.canonicalChecksum())) {
            throw failure("v2.underground-river-missing-relation",
                    "host lake is not bound to the same frozen cave network");
        }
        if (!parameters.outletCaveNodeId().equals(hostLake.hostBinding().hostChamberNodeId())) {
            throw failure("v2.underground-river-unreachable",
                    "outletCaveNodeId must equal host lake chamber node");
        }
        if (!parameters.fluidBodyId().equals(hostLake.fluidBody().fluidBodyId())) {
            throw failure("v2.underground-river-fluid-owner-conflict",
                    "fluidBodyId must equal host lake single fluid owner");
        }

        String entranceFeatureId = "";
        String entranceChecksum = "";
        if (!binding.caveEntranceFeatureId().isBlank()) {
            if (optionalEntrance == null) {
                throw failure("v2.underground-river-missing-relation",
                        "ORIGINATES_AT/REACHABLE_FROM entrance requires CaveEntrancePlanV2");
            }
            if (!optionalEntrance.featureId().equals(binding.caveEntranceFeatureId())) {
                throw failure("v2.underground-river-missing-relation", "cave entrance feature mismatch");
            }
            entranceFeatureId = optionalEntrance.featureId();
            entranceChecksum = optionalEntrance.canonicalChecksum();
        }

        List<String> path = shortestPath(hostCave, parameters.sourceCaveNodeId(), parameters.outletCaveNodeId());
        if (path.isEmpty()) {
            throw failure("v2.underground-river-unreachable",
                    "sourceCaveNodeId cannot reach outletCaveNodeId on host cave graph");
        }
        Map<String, CaveNetworkPlanV2.Node> nodes = indexNodes(hostCave);
        if (!downGradientOk(path, nodes)) {
            throw failure("v2.underground-river-up-gradient",
                    "underground river bed must be non-increasing downhill source→outlet");
        }

        int channelRadius = parameters.channelRadiusBlocks().minimum();
        int fluidDepth = parameters.fluidDepthBlocks().minimum();
        int airPocket = parameters.minimumAirPocketBlocks();
        int waterSurfaceY = hostLake.fluidBody().waterSurfaceYBlocks();

        List<VolumeSdfPrimitiveV2> primitives = new ArrayList<>();
        List<UndergroundRiverPlanV2.OrderedVolumeOp> orderedOps = new ArrayList<>();
        List<VolumeCsgPlanV2.Operator> csgOps = new ArrayList<>();
        List<UndergroundRiverPlanV2.ReachSample> reaches = new ArrayList<>();
        List<String> carveOpIds = new ArrayList<>();
        VolumeSdfAabbV2 aabb = null;
        long radiusMillionths = Math.multiplyExact((long) channelRadius, FIXED);
        int ordinal = 0;
        for (int index = 0; index < path.size() - 1; index++) {
            String fromId = path.get(index);
            String toId = path.get(index + 1);
            CaveNetworkPlanV2.Node from = nodes.get(fromId);
            CaveNetworkPlanV2.Node to = nodes.get(toId);
            String primId = "prim.river.channel." + feature.id() + "." + index;
            VolumeSdfPrimitiveV2 capsule = new VolumeSdfPrimitiveV2.Capsule(
                    primId, from.center(), to.center(), radiusMillionths);
            primitives.add(capsule);
            VolumeSdfAabbV2 segment = VolumeSdfAabbV2.spanning(from.center(), to.center())
                    .expand(Math.addExact(radiusMillionths, FIXED));
            aabb = aabb == null ? segment : unionAabb(aabb, segment);

            String carveId = "op.carve.river." + feature.id() + "." + index;
            orderedOps.add(new UndergroundRiverPlanV2.OrderedVolumeOp(
                    carveId, ordinal, UndergroundRiverPlanV2.OrderedVolumeOp.CARVE_SOLID, primId, ""));
            csgOps.add(new VolumeCsgPlanV2.Operator(
                    carveId, ordinal,
                    VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                    primId,
                    VolumeCsgPlanV2.MaskMode.NONE,
                    "",
                    List.of(),
                    ""));
            carveOpIds.add(carveId);
            reaches.add(new UndergroundRiverPlanV2.ReachSample(
                    "reach." + feature.id() + "." + index,
                    index,
                    fromId,
                    toId,
                    Math.min(from.center().yMillionths(), to.center().yMillionths()),
                    (from.center().xMillionths() + to.center().xMillionths()) / 2L,
                    (from.center().zMillionths() + to.center().zMillionths()) / 2L));
            ordinal++;
        }
        Objects.requireNonNull(aabb, "aabb");

        CaveNetworkPlanV2.Node sourceNode = nodes.get(path.getFirst());
        CaveNetworkPlanV2.Node outletNode = nodes.get(path.getLast());
        String fluidCarrierId = "prim.river.fluid-carrier." + feature.id();
        VolumeSdfPrimitiveV2 fluidCarrier = new VolumeSdfPrimitiveV2.Capsule(
                fluidCarrierId, sourceNode.center(), outletNode.center(), radiusMillionths);
        primitives.add(fluidCarrier);

        // Fluid plane: cells strictly below waterSurfaceY+1 are fluid candidates.
        String planeId = "prim.river.water-plane." + feature.id();
        VolumeSdfPrimitiveV2 waterPlane = new VolumeSdfPrimitiveV2.Plane(
                planeId,
                new VolumeSdfVec3V2(
                        (aabb.minXMillionths() + aabb.maxXMillionths()) / 2L,
                        Math.multiplyExact((long) waterSurfaceY + 1L, FIXED),
                        (aabb.minZMillionths() + aabb.maxZMillionths()) / 2L),
                new VolumeSdfVec3V2(0, FIXED, 0),
                aabb);
        primitives.add(waterPlane);

        String fluidOpId = "op.fluid.river." + feature.id();
        orderedOps.add(new UndergroundRiverPlanV2.OrderedVolumeOp(
                fluidOpId, ordinal, UndergroundRiverPlanV2.OrderedVolumeOp.ADD_FLUID,
                fluidCarrierId, parameters.fluidBodyId()));
        csgOps.add(new VolumeCsgPlanV2.Operator(
                fluidOpId, ordinal,
                VolumeCsgPlanV2.OperationKind.ADD_FLUID,
                fluidCarrierId,
                VolumeCsgPlanV2.MaskMode.INTERSECTION_WITH_PRIMITIVE,
                planeId,
                List.copyOf(carveOpIds),
                parameters.fluidBodyId()));

        if (!leakFree(aabb, hostCave, waterSurfaceY, fluidDepth)) {
            throw failure("v2.underground-river-leak",
                    "underground river fluid interval escapes cave AABB or surface height");
        }
        if (!airPocketOk(path, nodes, channelRadius, waterSurfaceY, fluidDepth, airPocket)) {
            throw failure("v2.underground-river-air-pocket",
                    "underground river channel lacks required air pocket above fluid");
        }

        long work = Math.addExact(
                Math.multiplyExact(aabb.extentXBlocks() + 1L, aabb.extentZBlocks() + 1L),
                Math.multiplyExact(aabb.extentYBlocks() + 1L, 8L));
        if (work > UndergroundRiverPlanV2.MAXIMUM_WORK_UNITS
                || aabb.extentXBlocks() > 256
                || aabb.extentYBlocks() > 128
                || aabb.extentZBlocks() > 256
                || orderedOps.size() > UndergroundRiverPlanV2.MAXIMUM_OPS) {
            throw failure("v2.underground-river-budget", "underground river AABB/op budget exceeded");
        }

        VolumeSdfPrimitivePlanV2 sdfPlan = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        Math.max(2, primitives.size()),
                        64,
                        2048,
                        65536,
                        65536),
                ZERO));
        VolumeCsgPlanV2 csgPlan = codec.sealVolumeCsgPlan(new VolumeCsgPlanV2(
                1,
                "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, sdfPlan.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                csgOps,
                new VolumeCsgPlanV2.ResourceBudget(
                        "volume-csg-budget-v1",
                        64,
                        8,
                        Math.max(1024L, work),
                        1_048_576L,
                        4096,
                        65536,
                        65536),
                ZERO));

        int support = Math.min(
                LandformUndergroundRiverModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                Math.max(4, channelRadius + path.size()));

        UndergroundRiverPlanV2.FloodedCaveFluidRegionHook hook =
                new UndergroundRiverPlanV2.FloodedCaveFluidRegionHook(
                        UndergroundRiverPlanV2.FloodedCaveFluidRegionHook.FLOODED_CAVE,
                        parameters.fluidBodyId(),
                        waterSurfaceY,
                        aabb);

        return new UndergroundRiverPlanV2(
                UndergroundRiverPlanV2.VERSION,
                feature.id(),
                hostCave.featureId(),
                hostCave.canonicalChecksum(),
                hostLake.featureId(),
                hostLake.canonicalChecksum(),
                entranceFeatureId,
                entranceChecksum,
                parameters.sourceCaveNodeId(),
                parameters.outletCaveNodeId(),
                parameters.fluidBodyId(),
                hook,
                reaches,
                channelRadius,
                fluidDepth,
                airPocket,
                aabb,
                orderedOps,
                new UndergroundRiverPlanV2.ArtifactBinding(
                        UndergroundRiverPlanV2.ArtifactBinding.VERSION,
                        sdfPlan.canonicalChecksum(),
                        UndergroundRiverPlanV2.ArtifactBinding.SDF_CONTRACT),
                new UndergroundRiverPlanV2.ArtifactBinding(
                        UndergroundRiverPlanV2.ArtifactBinding.VERSION,
                        csgPlan.canonicalChecksum(),
                        UndergroundRiverPlanV2.ArtifactBinding.CSG_CONTRACT),
                UndergroundRiverPlanV2.CHANNEL_MASK_FIELD_ID,
                UndergroundRiverPlanV2.FLUID_MASK_FIELD_ID,
                UndergroundRiverPlanV2.OWNERSHIP_FIELD_ID,
                UndergroundRiverPlanV2.REACHABILITY_FIELD_ID,
                UndergroundRiverPlanV2.AIR_POCKET_FIELD_ID,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    public record HostBinding(
            String withinRelationId,
            String caveNetworkFeatureId,
            String entranceRelationId,
            String caveEntranceFeatureId
    ) {
    }

    private static boolean isCaveNetwork(TerrainIntentV2 intent, String endpoint) {
        if (!endpoint.startsWith("feature:")) {
            return false;
        }
        String id = endpoint.substring("feature:".length());
        return intent.features().stream()
                .anyMatch(feature -> feature.id().equals(id)
                        && feature.kind() == TerrainIntentV2.FeatureKind.CAVE_NETWORK);
    }

    private static boolean isCaveEntrance(TerrainIntentV2 intent, String endpoint) {
        if (!endpoint.startsWith("feature:")) {
            return false;
        }
        String id = endpoint.substring("feature:".length());
        return intent.features().stream()
                .anyMatch(feature -> feature.id().equals(id)
                        && feature.kind() == TerrainIntentV2.FeatureKind.CAVE_ENTRANCE);
    }

    public static List<String> shortestPath(CaveNetworkPlanV2 hostCave, String source, String outlet) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (CaveNetworkPlanV2.Node node : hostCave.nodes()) {
            adjacency.put(node.nodeId(), new ArrayList<>());
        }
        if (!adjacency.containsKey(source) || !adjacency.containsKey(outlet)) {
            return List.of();
        }
        for (CaveNetworkPlanV2.Edge edge : hostCave.edges()) {
            adjacency.get(edge.fromNodeId()).add(edge.toNodeId());
            adjacency.get(edge.toNodeId()).add(edge.fromNodeId());
        }
        Map<String, String> parent = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(source);
        seen.add(source);
        parent.put(source, null);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (current.equals(outlet)) {
                break;
            }
            for (String next : adjacency.getOrDefault(current, List.of())) {
                if (seen.add(next)) {
                    parent.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        if (!parent.containsKey(outlet)) {
            return List.of();
        }
        List<String> path = new ArrayList<>();
        for (String cursor = outlet; cursor != null; cursor = parent.get(cursor)) {
            path.add(cursor);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    public static boolean downGradientOk(List<String> path, Map<String, CaveNetworkPlanV2.Node> nodes) {
        long previous = Long.MAX_VALUE;
        for (String nodeId : path) {
            long y = nodes.get(nodeId).center().yMillionths();
            if (y > previous) {
                return false;
            }
            previous = y;
        }
        return true;
    }

    private static boolean leakFree(
            VolumeSdfAabbV2 aabb,
            CaveNetworkPlanV2 hostCave,
            int waterSurfaceY,
            int fluidDepth
    ) {
        int fluidTop = waterSurfaceY;
        int fluidBottom = waterSurfaceY - fluidDepth;
        if (fluidBottom < Math.toIntExact(aabb.minYMillionths() / FIXED) - 1) {
            return false;
        }
        if (fluidTop >= hostCave.surfaceHeightBlocks()) {
            return false;
        }
        VolumeSdfAabbV2 caveAabb = hostCave.aabb();
        return aabb.minXMillionths() >= caveAabb.minXMillionths()
                && aabb.maxXMillionths() <= caveAabb.maxXMillionths()
                && aabb.minZMillionths() >= caveAabb.minZMillionths()
                && aabb.maxZMillionths() <= caveAabb.maxZMillionths()
                && aabb.minYMillionths() >= caveAabb.minYMillionths()
                && aabb.maxYMillionths() <= caveAabb.maxYMillionths();
    }

    private static boolean airPocketOk(
            List<String> path,
            Map<String, CaveNetworkPlanV2.Node> nodes,
            int channelRadius,
            int waterSurfaceY,
            int fluidDepth,
            int minimumAirPocket
    ) {
        for (String nodeId : path) {
            CaveNetworkPlanV2.Node node = nodes.get(nodeId);
            int roofY = Math.toIntExact(node.center().yMillionths() / FIXED) + channelRadius;
            int fluidTop = waterSurfaceY;
            int fluidBottom = waterSurfaceY - fluidDepth;
            int bedY = Math.toIntExact(node.center().yMillionths() / FIXED) - channelRadius;
            if (fluidBottom < bedY) {
                // fluid clipped to channel bed; still need air above fluid under roof
                fluidBottom = bedY;
            }
            if (fluidTop >= roofY) {
                return false;
            }
            int air = roofY - Math.max(fluidTop, fluidBottom);
            if (air < minimumAirPocket) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, CaveNetworkPlanV2.Node> indexNodes(CaveNetworkPlanV2 hostCave) {
        Map<String, CaveNetworkPlanV2.Node> nodes = new HashMap<>();
        for (CaveNetworkPlanV2.Node node : hostCave.nodes()) {
            nodes.put(node.nodeId(), node);
        }
        return nodes;
    }

    private static VolumeSdfAabbV2 unionAabb(VolumeSdfAabbV2 left, VolumeSdfAabbV2 right) {
        return new VolumeSdfAabbV2(
                Math.min(left.minXMillionths(), right.minXMillionths()),
                Math.min(left.minYMillionths(), right.minYMillionths()),
                Math.min(left.minZMillionths(), right.minZMillionths()),
                Math.max(left.maxXMillionths(), right.maxXMillionths()),
                Math.max(left.maxYMillionths(), right.maxYMillionths()),
                Math.max(left.maxZMillionths(), right.maxZMillionths()));
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
