package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformCaveEntranceModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.mountain.MountainRangeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.valley.ValleyGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.CaveEntrancePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MountainRangePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ValleyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
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

/** Compiles a V2-9-10 CAVE_ENTRANCE plan bound to a frozen host cave and surface host. */
public final class CaveEntrancePlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private static final long FIXED = TerrainIntentV2.FIXED_SCALE;

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public HostBinding resolveHostBinding(TerrainIntentV2.Feature entrance, TerrainIntentV2 intent) {
        Objects.requireNonNull(entrance, "entrance");
        Objects.requireNonNull(intent, "intent");
        String endpoint = "feature:" + entrance.id();

        List<TerrainIntentV2.Relation> surfaceHosts = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.SUPPORTED_BY)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isSurfaceHost(intent, relation.to()))
                .toList();
        if (surfaceHosts.isEmpty()) {
            throw failure("v2.cave-entrance-missing-relation",
                    "cave entrance requires exactly one HARD SUPPORTED_BY surface host");
        }
        if (surfaceHosts.size() > 1) {
            throw failure("v2.cave-entrance-ambiguous-host",
                    "cave entrance has multiple HARD SUPPORTED_BY surface hosts");
        }

        List<TerrainIntentV2.Relation> caveTargets = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ENTRANCE_OF)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isCaveNetwork(intent, relation.to()))
                .toList();
        if (caveTargets.isEmpty()) {
            throw failure("v2.cave-entrance-orphan",
                    "cave entrance requires exactly one HARD ENTRANCE_OF cave network");
        }
        if (caveTargets.size() > 1) {
            throw failure("v2.cave-entrance-missing-relation",
                    "cave entrance has multiple HARD ENTRANCE_OF cave targets");
        }

        TerrainIntentV2.Relation surface = surfaceHosts.getFirst();
        TerrainIntentV2.Relation cave = caveTargets.getFirst();
        String surfaceHostId = surface.to().substring("feature:".length());
        String caveId = cave.to().substring("feature:".length());
        TerrainIntentV2.FeatureKind hostKind = featureKind(intent, surfaceHostId);
        CaveEntrancePlanV2.SurfaceHostKind surfaceHostKind = hostKind == TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE
                ? CaveEntrancePlanV2.SurfaceHostKind.MOUNTAIN_RANGE
                : CaveEntrancePlanV2.SurfaceHostKind.VALLEY;
        return new HostBinding(
                surface.id(), surfaceHostId, surfaceHostKind,
                cave.id(), caveId);
    }

    public CaveEntrancePlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            MountainRangePlanV2 mountainHost,
            ValleyPlanV2 valleyHost,
            CaveNetworkPlanV2 hostCave
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        Objects.requireNonNull(hostCave, "hostCave");
        if (feature.kind() != TerrainIntentV2.FeatureKind.CAVE_ENTRANCE) {
            throw failure("v2.cave-entrance-missing-relation", "feature kind is not CAVE_ENTRANCE");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.CaveEntranceParameters parameters)) {
            throw failure("v2.cave-entrance-missing-relation", "cave entrance requires CaveEntranceParameters");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PointGeometry pointGeometry)) {
            throw failure("v2.cave-entrance-missing-relation", "cave entrance requires POINT geometry");
        }

        HostBinding binding = resolveHostBinding(feature, intent);
        if (!binding.caveNetworkFeatureId().equals(hostCave.featureId())) {
            throw failure("v2.cave-entrance-orphan",
                    "ENTRANCE_OF target does not match frozen host cave featureId");
        }

        String surfaceGeometryChecksum;
        if (binding.surfaceHostKind() == CaveEntrancePlanV2.SurfaceHostKind.MOUNTAIN_RANGE) {
            Objects.requireNonNull(mountainHost, "mountainHost");
            if (!mountainHost.featureId().equals(binding.surfaceHostFeatureId())) {
                throw failure("v2.cave-entrance-missing-relation", "mountain host feature mismatch");
            }
            surfaceGeometryChecksum = mountainHost.geometryChecksum();
        } else {
            Objects.requireNonNull(valleyHost, "valleyHost");
            if (!valleyHost.featureId().equals(binding.surfaceHostFeatureId())) {
                throw failure("v2.cave-entrance-missing-relation", "valley host feature mismatch");
            }
            surfaceGeometryChecksum = valleyHost.geometryChecksum();
        }

        CaveNetworkPlanV2.Node target = requireEntranceNode(hostCave, parameters.targetEntranceNodeId());
        requireReachableFromEntranceSet(hostCave, parameters.targetEntranceNodeId());

        long openingX = Math.multiplyExact((long) pointGeometry.point().xMillionths(), bounds.width() - 1L);
        long openingZ = Math.multiplyExact((long) pointGeometry.point().zMillionths(), bounds.length() - 1L);
        int blockX = clampBlock(roundDiv(openingX, FIXED), bounds.width());
        int blockZ = clampBlock(roundDiv(openingZ, FIXED), bounds.length());

        SurfaceSample surface = sampleSurface(
                binding.surfaceHostKind(), mountainHost, valleyHost, blockX, blockZ);
        if (!surface.owned()) {
            throw failure("v2.cave-entrance-owner-conflict",
                    "cave entrance opening is outside the surface host ownership mask");
        }
        // Volume seam uses frozen host-cave surface height; mountain/valley own XZ only.
        int surfaceY = hostCave.surfaceHeightBlocks();
        if (surface.elevationBlocks() > surfaceY) {
            surfaceY = surface.elevationBlocks();
        }
        int openingY = Math.addExact(surfaceY, parameters.surfaceOffsetBlocks());
        if (openingY < bounds.waterLevel()) {
            throw failure("v2.cave-entrance-flood-leak",
                    "cave entrance opening is below waterLevel without declared flood");
        }

        VolumeSdfVec3V2 openingCenter = new VolumeSdfVec3V2(
                openingX, Math.multiplyExact((long) openingY, FIXED), openingZ);
        ApproachGeometry approach = buildApproach(
                openingCenter, target, parameters.approachLengthBlocks(),
                parameters.minimumOpeningBlocks());

        if (!roofOk(surfaceY, openingCenter, approach, parameters.minimumOpeningBlocks(),
                parameters.roofClearanceBlocks())) {
            throw failure("v2.cave-entrance-thin-roof",
                    "cave entrance approach has insufficient solid roof clearance");
        }

        String mouthId = "prim.entrance.mouth." + feature.id();
        String approachId = "prim.entrance.approach." + feature.id();
        VolumeSdfPrimitiveV2 mouth = new VolumeSdfPrimitiveV2.Sphere(
                mouthId, openingCenter, Math.multiplyExact((long) parameters.minimumOpeningBlocks(), FIXED));
        VolumeSdfPrimitiveV2 capsule = new VolumeSdfPrimitiveV2.Capsule(
                approachId,
                new VolumeSdfVec3V2(approach.startX(), approach.startY(), approach.startZ()),
                new VolumeSdfVec3V2(approach.endX(), approach.endY(), approach.endZ()),
                Math.multiplyExact((long) parameters.minimumOpeningBlocks(), FIXED));
        List<VolumeSdfPrimitiveV2> primitives = List.of(mouth, capsule);
        VolumeSdfAabbV2 aabb = VolumeSdfAabbV2.spanning(
                        new VolumeSdfVec3V2(approach.startX(), approach.startY(), approach.startZ()),
                        new VolumeSdfVec3V2(approach.endX(), approach.endY(), approach.endZ()))
                .expand(Math.addExact(
                        Math.multiplyExact((long) parameters.minimumOpeningBlocks(), FIXED),
                        FIXED));
        long work = Math.addExact(
                Math.multiplyExact(aabb.extentXBlocks() + 1L, aabb.extentZBlocks() + 1L),
                Math.multiplyExact(aabb.extentYBlocks() + 1L, 4L));
        if (work > CaveEntrancePlanV2.MAXIMUM_WORK_UNITS
                || aabb.extentXBlocks() > 256
                || aabb.extentYBlocks() > 128
                || aabb.extentZBlocks() > 256) {
            throw failure("v2.cave-entrance-budget", "cave entrance AABB/support budget exceeded");
        }

        VolumeSdfPrimitivePlanV2 sdfPlan = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                primitives,
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        2,
                        64,
                        2048,
                        65536,
                        65536),
                ZERO));
        List<VolumeCsgPlanV2.Operator> operators = List.of(
                new VolumeCsgPlanV2.Operator(
                        "op.carve.entrance.mouth." + feature.id(),
                        0,
                        VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                        mouthId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(),
                        ""),
                new VolumeCsgPlanV2.Operator(
                        "op.carve.entrance.approach." + feature.id(),
                        1,
                        VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                        approachId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(),
                        ""));
        VolumeCsgPlanV2 csgPlan = codec.sealVolumeCsgPlan(new VolumeCsgPlanV2(
                1,
                "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, sdfPlan.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                operators,
                new VolumeCsgPlanV2.ResourceBudget(
                        "volume-csg-budget-v1",
                        64,
                        8,
                        1024L,
                        1_048_576L,
                        4096,
                        65536,
                        65536),
                ZERO));

        int support = Math.min(
                LandformCaveEntranceModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                Math.max(4, parameters.minimumOpeningBlocks() + parameters.approachLengthBlocks()));

        List<CaveEntrancePlanV2.OrderedCarveOp> carveOps = List.of(
                new CaveEntrancePlanV2.OrderedCarveOp(
                        "op.carve.entrance.mouth." + feature.id(), 0,
                        CaveEntrancePlanV2.OrderedCarveOp.CARVE_SOLID, mouthId),
                new CaveEntrancePlanV2.OrderedCarveOp(
                        "op.carve.entrance.approach." + feature.id(), 1,
                        CaveEntrancePlanV2.OrderedCarveOp.CARVE_SOLID, approachId));

        return new CaveEntrancePlanV2(
                CaveEntrancePlanV2.VERSION,
                feature.id(),
                binding.surfaceHostFeatureId(),
                binding.surfaceHostKind(),
                binding.surfaceHostRelationId(),
                binding.caveNetworkFeatureId(),
                binding.caveEntranceRelationId(),
                parameters.targetEntranceNodeId(),
                hostCave.canonicalChecksum(),
                surfaceGeometryChecksum,
                openingX,
                openingZ,
                surfaceY,
                openingY,
                new CaveEntrancePlanV2.ApproachCapsule(
                        approach.startX(), approach.startY(), approach.startZ(),
                        approach.endX(), approach.endY(), approach.endZ(),
                        Math.multiplyExact((long) parameters.minimumOpeningBlocks(), FIXED)),
                parameters.surfaceOffsetBlocks(),
                parameters.minimumOpeningBlocks(),
                parameters.approachLengthBlocks(),
                parameters.roofClearanceBlocks(),
                aabb,
                carveOps,
                new CaveEntrancePlanV2.ArtifactBinding(
                        CaveEntrancePlanV2.ArtifactBinding.VERSION,
                        sdfPlan.canonicalChecksum(),
                        CaveEntrancePlanV2.ArtifactBinding.SDF_CONTRACT),
                new CaveEntrancePlanV2.ArtifactBinding(
                        CaveEntrancePlanV2.ArtifactBinding.VERSION,
                        csgPlan.canonicalChecksum(),
                        CaveEntrancePlanV2.ArtifactBinding.CSG_CONTRACT),
                CaveEntrancePlanV2.OPENING_MASK_FIELD_ID,
                CaveEntrancePlanV2.APPROACH_MASK_FIELD_ID,
                CaveEntrancePlanV2.OWNERSHIP_FIELD_ID,
                CaveEntrancePlanV2.REACHABILITY_FIELD_ID,
                CaveEntrancePlanV2.ROOF_CLEARANCE_FIELD_ID,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    public record HostBinding(
            String surfaceHostRelationId,
            String surfaceHostFeatureId,
            CaveEntrancePlanV2.SurfaceHostKind surfaceHostKind,
            String caveEntranceRelationId,
            String caveNetworkFeatureId
    ) {
    }

    public record CompiledArtifacts(
            VolumeSdfPrimitivePlanV2 sdfPlan,
            VolumeCsgPlanV2 csgPlan
    ) {
    }

    public CompiledArtifacts compileArtifacts(CaveEntrancePlanV2 plan) {
        Objects.requireNonNull(plan, "plan");
        String mouthId = plan.orderedCarveOps().getFirst().primitiveId();
        String approachId = plan.orderedCarveOps().get(1).primitiveId();
        VolumeSdfVec3V2 opening = new VolumeSdfVec3V2(
                plan.openingXMillionths(),
                Math.multiplyExact((long) plan.openingYBlocks(), FIXED),
                plan.openingZMillionths());
        VolumeSdfPrimitiveV2 mouth = new VolumeSdfPrimitiveV2.Sphere(
                mouthId, opening, Math.multiplyExact((long) plan.selectedMinimumOpeningBlocks(), FIXED));
        CaveEntrancePlanV2.ApproachCapsule approach = plan.approach();
        VolumeSdfPrimitiveV2 capsule = new VolumeSdfPrimitiveV2.Capsule(
                approachId,
                new VolumeSdfVec3V2(approach.startXMillionths(), approach.startYMillionths(),
                        approach.startZMillionths()),
                new VolumeSdfVec3V2(approach.endXMillionths(), approach.endYMillionths(),
                        approach.endZMillionths()),
                approach.radiusMillionths());
        VolumeSdfPrimitivePlanV2 sdfPlan = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                List.of(mouth, capsule),
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        2,
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
                List.of(
                        new VolumeCsgPlanV2.Operator(
                                plan.orderedCarveOps().getFirst().opId(),
                                0,
                                VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                                mouthId,
                                VolumeCsgPlanV2.MaskMode.NONE,
                                "",
                                List.of(),
                                ""),
                        new VolumeCsgPlanV2.Operator(
                                plan.orderedCarveOps().get(1).opId(),
                                1,
                                VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                                approachId,
                                VolumeCsgPlanV2.MaskMode.NONE,
                                "",
                                List.of(),
                                "")),
                new VolumeCsgPlanV2.ResourceBudget(
                        "volume-csg-budget-v1",
                        64,
                        8,
                        1024L,
                        1_048_576L,
                        4096,
                        65536,
                        65536),
                ZERO));
        if (!sdfPlan.canonicalChecksum().equals(plan.sdfPlanBinding().sourceArtifactChecksum())
                || !csgPlan.canonicalChecksum().equals(plan.csgPlanBinding().sourceArtifactChecksum())) {
            throw failure("v2.cave-entrance-budget", "cave entrance SDF/CSG bindings diverge from sealed plan");
        }
        return new CompiledArtifacts(sdfPlan, csgPlan);
    }

    private static boolean isSurfaceHost(TerrainIntentV2 intent, String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return false;
        }
        TerrainIntentV2.FeatureKind kind = featureKind(intent, endpoint.substring("feature:".length()));
        return kind == TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE
                || kind == TerrainIntentV2.FeatureKind.VALLEY;
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
                .orElseThrow(() -> failure("v2.cave-entrance-missing-relation",
                        "required host feature is missing: " + featureId));
    }

    private static CaveNetworkPlanV2.Node requireEntranceNode(CaveNetworkPlanV2 host, String nodeId) {
        Optional<CaveNetworkPlanV2.Node> node = host.nodes().stream()
                .filter(item -> item.nodeId().equals(nodeId))
                .findFirst();
        if (node.isEmpty()) {
            throw failure("v2.cave-entrance-unreachable", "targetEntranceNodeId is missing from host cave");
        }
        if (node.get().kind() != CaveNetworkPlanV2.NodeKind.ENTRANCE
                || !host.entranceNodeIds().contains(nodeId)) {
            throw failure("v2.cave-entrance-unreachable",
                    "targetEntranceNodeId must be an ENTRANCE listed in entranceNodeIds");
        }
        return node.get();
    }

    private static void requireReachableFromEntranceSet(CaveNetworkPlanV2 host, String targetNodeId) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (CaveNetworkPlanV2.Edge edge : host.edges()) {
            adjacency.computeIfAbsent(edge.fromNodeId(), key -> new ArrayList<>()).add(edge.toNodeId());
            adjacency.computeIfAbsent(edge.toNodeId(), key -> new ArrayList<>()).add(edge.fromNodeId());
        }
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(host.entranceNodeIds());
        visited.addAll(host.entranceNodeIds());
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (current.equals(targetNodeId)) {
                return;
            }
            for (String next : adjacency.getOrDefault(current, List.of())) {
                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        throw failure("v2.cave-entrance-unreachable",
                "targetEntranceNodeId is unreachable from host cave entrance set");
    }

    private static SurfaceSample sampleSurface(
            CaveEntrancePlanV2.SurfaceHostKind kind,
            MountainRangePlanV2 mountainHost,
            ValleyPlanV2 valleyHost,
            int blockX,
            int blockZ
    ) {
        if (kind == CaveEntrancePlanV2.SurfaceHostKind.MOUNTAIN_RANGE) {
            MountainRangeGeneratorV2.MountainSample sample =
                    new MountainRangeGeneratorV2(mountainHost).sampleAt(blockX, blockZ);
            return new SurfaceSample(sample.active(), sample.elevationBlocks());
        }
        ValleyGeneratorV2.ValleySample sample = new ValleyGeneratorV2(valleyHost).sampleAt(blockX, blockZ);
        return new SurfaceSample(sample.active(), sample.elevationBlocks());
    }

    private static ApproachGeometry buildApproach(
            VolumeSdfVec3V2 opening,
            CaveNetworkPlanV2.Node target,
            int approachLengthBlocks,
            int openingBlocks
    ) {
        long dx = Math.subtractExact(target.center().xMillionths(), opening.xMillionths());
        long dy = Math.subtractExact(target.center().yMillionths(), opening.yMillionths());
        long dz = Math.subtractExact(target.center().zMillionths(), opening.zMillionths());
        long distance = hypot3(dx, dy, dz);
        if (distance < FIXED) {
            throw failure("v2.cave-entrance-unreachable",
                    "cave entrance opening coincides with target entrance node");
        }
        long length = Math.multiplyExact((long) approachLengthBlocks, FIXED);
        long endX = Math.addExact(opening.xMillionths(), mulDiv(dx, length, distance));
        long endY = Math.addExact(opening.yMillionths(), mulDiv(dy, length, distance));
        long endZ = Math.addExact(opening.zMillionths(), mulDiv(dz, length, distance));
        long endDistance = hypot3(
                Math.subtractExact(endX, target.center().xMillionths()),
                Math.subtractExact(endY, target.center().yMillionths()),
                Math.subtractExact(endZ, target.center().zMillionths()));
        if (endDistance > target.radiusMillionths()) {
            throw failure("v2.cave-entrance-unreachable",
                    "approach capsule end is outside the target entrance node radius");
        }
        if (openingBlocks < 2) {
            throw failure("v2.cave-entrance-budget", "opening radius collapsed");
        }
        return new ApproachGeometry(
                opening.xMillionths(), opening.yMillionths(), opening.zMillionths(),
                endX, endY, endZ);
    }

    private static boolean roofOk(
            int surfaceY,
            VolumeSdfVec3V2 opening,
            ApproachGeometry approach,
            int openingBlocks,
            int roofClearance
    ) {
        long openingRadius = Math.multiplyExact((long) openingBlocks, FIXED);
        int samples = Math.max(2, (int) Math.min(64L,
                roundDiv(hypot3(
                        approach.endX() - approach.startX(),
                        approach.endY() - approach.startY(),
                        approach.endZ() - approach.startZ()), FIXED) + 1L));
        for (int index = 0; index <= samples; index++) {
            long x = approach.startX()
                    + mulDiv(approach.endX() - approach.startX(), index, samples);
            long y = approach.startY()
                    + mulDiv(approach.endY() - approach.startY(), index, samples);
            long z = approach.startZ()
                    + mulDiv(approach.endZ() - approach.startZ(), index, samples);
            long radial = hypot3(
                    x - opening.xMillionths(),
                    y - opening.yMillionths(),
                    z - opening.zMillionths());
            if (radial <= openingRadius) {
                continue;
            }
            int sampleY = Math.toIntExact(roundDiv(y, FIXED));
            int thickness = surfaceY - sampleY;
            if (thickness < roofClearance) {
                return false;
            }
        }
        return true;
    }

    private static long hypot3(long dx, long dy, long dz) {
        return isqrt(Math.addExact(Math.addExact(sq(dx), sq(dy)), sq(dz)));
    }

    private static long sq(long value) {
        return Math.multiplyExact(value, value);
    }

    private static long isqrt(long value) {
        long estimate = value;
        if (estimate <= 0L) {
            return 0L;
        }
        long previous;
        do {
            previous = estimate;
            estimate = (estimate + value / estimate) >>> 1;
        } while (estimate < previous);
        return previous;
    }

    private static long mulDiv(long value, long numerator, long denominator) {
        return Math.multiplyExact(value, numerator) / denominator;
    }

    private static int roundDiv(long value, long divisor) {
        if (value >= 0L) {
            return Math.toIntExact((value + divisor / 2L) / divisor);
        }
        return Math.toIntExact((value - divisor / 2L) / divisor);
    }

    private static int clampBlock(int value, int span) {
        return Math.max(0, Math.min(span - 1, value));
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }

    private record SurfaceSample(boolean owned, int elevationBlocks) {
    }

    private record ApproachGeometry(
            long startX,
            long startY,
            long startZ,
            long endX,
            long endY,
            long endZ
    ) {
    }
}
